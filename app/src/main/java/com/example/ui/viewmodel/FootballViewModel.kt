package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MatchDoc
import com.example.data.model.PredictionRecordDoc
import com.example.data.model.UiState
import com.example.data.remote.FootballApiService
import com.example.data.remote.SampleLeagueDataset
import com.example.data.repository.FootballRepository
import com.example.engine.BacktestSummary
import com.example.engine.CalibrationEngine
import com.example.engine.FootballIqModel
import com.example.engine.MatchInput
import com.example.engine.PredictionOutcome
import com.example.engine.PredictionRecordBuilder
import com.example.engine.SeasonFilter
import com.example.engine.TeamRatingSnapshot
import com.example.engine.TeamRecentAnalysis
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SeasonScope(val label: String) {
  SEASON_26_27("26/27 Season"),
  ALL_MATCHES("All Seasons"),
}

data class ModelDashboardState(
  val teams: List<String> = emptyList(),
  val leaderboard: List<TeamRatingSnapshot> = emptyList(),
  val selectedHome: String = "",
  val selectedAway: String = "",
  val seasonScope: SeasonScope = SeasonScope.SEASON_26_27,
  val recentGamesWindow: Int = 10,
  val hasGeneratedPrediction: Boolean = false,
  val isSelectionPendingGeneration: Boolean = false,
  val prediction: PredictionOutcome? = null,
  val homeRecentAnalysis: TeamRecentAnalysis? = null,
  val awayRecentAnalysis: TeamRecentAnalysis? = null,
  val headToHeadMatches: List<MatchInput> = emptyList(),
  val totalMatches: Int = 0,
  val totalDatasetMatches: Int = 0,
  val season2627MatchesCount: Int = 0,
  val avgHomeGoals: Double = 1.45,
  val avgAwayGoals: Double = 1.15,
)

class FootballViewModel(
  private val repository: FootballRepository,
  private val apiService: FootballApiService,
  private val currentUserId: String,
) : ViewModel() {

  val matchesState: StateFlow<UiState<List<MatchDoc>>> = repository.observeMatches(currentUserId)
    .map<List<MatchDoc>, UiState<List<MatchDoc>>> { UiState.Success(it) }
    .catch { error ->
      val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
      if (firestoreError == null && error is CancellationException) throw error
      val rootError = firestoreError ?: error
      Log.w(TAG, "Error observing matches", rootError)
      emit(UiState.Error(rootError.message ?: "Failed to load matches"))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = UiState.Loading,
    )

  val ledgerState: StateFlow<UiState<List<PredictionRecordDoc>>> =
    repository.observePredictionRecords(currentUserId)
      .map<List<PredictionRecordDoc>, UiState<List<PredictionRecordDoc>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing prediction ledger", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load prediction ledger"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val _selectedHome = MutableStateFlow("")
  private val _selectedAway = MutableStateFlow("")
  private val _generatedPair = MutableStateFlow<Pair<String, String>?>(null)
  private val _seasonScope = MutableStateFlow(SeasonScope.SEASON_26_27)
  private val _recentGamesWindow = MutableStateFlow(10)

  private val _warmupMatches = MutableStateFlow(100)
  val warmupMatches: StateFlow<Int> = _warmupMatches.asStateFlow()

  private val _backtestSummary = MutableStateFlow<BacktestSummary?>(null)
  val backtestSummary: StateFlow<BacktestSummary?> = _backtestSummary.asStateFlow()

  private val _isBacktesting = MutableStateFlow(false)
  val isBacktesting: StateFlow<Boolean> = _isBacktesting.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _isRefreshingHistory = MutableStateFlow(false)
  val isRefreshingHistory: StateFlow<Boolean> = _isRefreshingHistory.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  fun refreshPredictionHistory() {
    viewModelScope.launch {
      _isRefreshingHistory.value = true
      val res = repository.fetchPredictionRecords(currentUserId)
      _isRefreshingHistory.value = false
      res.fold(
        onSuccess = { list ->
          _statusMessage.value = "Synced ${list.size} prediction records from Firestore"
        },
        onFailure = { err ->
          _statusMessage.value = err.localizedMessage ?: "Failed to fetch prediction history"
        },
      )
    }
  }

  private data class CachedModelBundle(
    val model: FootballIqModel?,
    val scope: SeasonScope,
    val totalMatches: Int,
    val totalDatasetMatches: Int,
    val season2627MatchesCount: Int,
  )

  private val activeModelFlow = combine(matchesState, _seasonScope) { state, scope ->
    val allDocs = (state as? UiState.Success)?.data ?: emptyList()
    if (allDocs.isEmpty()) {
      return@combine CachedModelBundle(null, scope, 0, 0, 0)
    }
    val allInputs = allDocs.map { it.toMatchInput() }
    val seasonInputs = SeasonFilter.filterSeason2627(allInputs)
    val activeInputs = when (scope) {
      SeasonScope.SEASON_26_27 -> seasonInputs
      SeasonScope.ALL_MATCHES -> allInputs
    }
    val model = if (activeInputs.isEmpty()) null else FootballIqModel(activeInputs)
    CachedModelBundle(
      model = model,
      scope = scope,
      totalMatches = activeInputs.size,
      totalDatasetMatches = allInputs.size,
      season2627MatchesCount = seasonInputs.size,
    )
  }.flowOn(Dispatchers.Default)

  val dashboardState: StateFlow<ModelDashboardState> = combine(
    activeModelFlow,
    _selectedHome,
    _selectedAway,
    _generatedPair,
    _recentGamesWindow,
  ) { bundle, homeSel, awaySel, genPair, window ->
    val model = bundle.model
      ?: return@combine ModelDashboardState(
        seasonScope = bundle.scope,
        recentGamesWindow = window,
        totalMatches = bundle.totalMatches,
        totalDatasetMatches = bundle.totalDatasetMatches,
        season2627MatchesCount = bundle.season2627MatchesCount,
      )

    val teams = model.allTeams
    val resolvedHome = if (homeSel in teams) homeSel else teams.firstOrNull().orEmpty()
    val resolvedAway = if (awaySel in teams && awaySel != resolvedHome) {
      awaySel
    } else {
      teams.firstOrNull { it != resolvedHome }.orEmpty()
    }
    val hasValidPair = resolvedHome.isNotBlank() && resolvedAway.isNotBlank() && resolvedHome != resolvedAway
    val isGeneratedForCurrentPair = hasValidPair &&
      genPair != null &&
      genPair.first == resolvedHome &&
      genPair.second == resolvedAway
    val prediction = if (isGeneratedForCurrentPair) {
      model.predict(resolvedHome, resolvedAway, recentWindow = window)
    } else {
      null
    }
    val homeAnalysis = if (resolvedHome.isNotBlank()) model.analyzeRecentTeam(resolvedHome, last = window) else null
    val awayAnalysis = if (resolvedAway.isNotBlank()) model.analyzeRecentTeam(resolvedAway, last = window) else null
    val h2h = if (hasValidPair) model.headToHead(resolvedHome, resolvedAway) else emptyList()

    ModelDashboardState(
      teams = teams,
      leaderboard = model.teamLeaderboard(recentWindow = window),
      selectedHome = resolvedHome,
      selectedAway = resolvedAway,
      seasonScope = bundle.scope,
      recentGamesWindow = window,
      hasGeneratedPrediction = isGeneratedForCurrentPair,
      isSelectionPendingGeneration = hasValidPair && !isGeneratedForCurrentPair,
      prediction = prediction,
      homeRecentAnalysis = homeAnalysis,
      awayRecentAnalysis = awayAnalysis,
      headToHeadMatches = h2h,
      totalMatches = bundle.totalMatches,
      totalDatasetMatches = bundle.totalDatasetMatches,
      season2627MatchesCount = bundle.season2627MatchesCount,
      avgHomeGoals = model.avgHomeGoals,
      avgAwayGoals = model.avgAwayGoals,
    )
  }.flowOn(Dispatchers.Default)
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      initialValue = ModelDashboardState(),
    )

  fun setSeasonScope(scope: SeasonScope) {
    _seasonScope.value = scope
  }

  fun setRecentGamesWindow(window: Int) {
    _recentGamesWindow.value = window.coerceIn(5, 50)
  }

  fun selectHomeTeam(team: String) {
    if (team == _selectedAway.value) {
      val prevHome = _selectedHome.value
      _selectedHome.value = team
      if (prevHome.isNotBlank() && prevHome != team) {
        _selectedAway.value = prevHome
      }
    } else {
      _selectedHome.value = team
    }
    _generatedPair.value = null
  }

  fun selectAwayTeam(team: String) {
    if (team == _selectedHome.value) {
      val prevAway = _selectedAway.value
      _selectedAway.value = team
      if (prevAway.isNotBlank() && prevAway != team) {
        _selectedHome.value = prevAway
      }
    } else {
      _selectedAway.value = team
    }
    _generatedPair.value = null
  }

  fun swapTeams() {
    val current = dashboardState.value
    if (current.selectedHome.isNotBlank() && current.selectedAway.isNotBlank()) {
      _selectedHome.value = current.selectedAway
      _selectedAway.value = current.selectedHome
      _generatedPair.value = null
    }
  }

  fun generatePrediction() {
    val current = dashboardState.value
    val home = current.selectedHome
    val away = current.selectedAway
    if (home.isNotBlank() && away.isNotBlank() && home != away) {
      _selectedHome.value = home
      _selectedAway.value = away
      _generatedPair.value = home to away
    }
  }

  fun selectFixture(home: String, away: String) {
    if (home.isNotBlank() && away.isNotBlank() && home != away) {
      _selectedHome.value = home
      _selectedAway.value = away
      _generatedPair.value = home to away
    }
  }

  fun clearStatusMessage() {
    _statusMessage.value = null
  }

  fun addMatch(home: String, away: String, hg: Int, ag: Int, dateIso: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.addMatch(
        MatchInput(home = home, away = away, hg = hg, ag = ag, date = dateIso, source = "manual")
      )
      _isBusy.value = false
      _statusMessage.value = res.fold(
        onSuccess = { "Logged $home $hg-$ag $away" },
        onFailure = { it.localizedMessage ?: "Failed to save match" },
      )
    }
  }

  fun deleteMatch(matchId: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.deleteMatch(matchId)
      _isBusy.value = false
      if (res.isFailure) {
        _statusMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete match"
      }
    }
  }

  fun clearAllMatches() {
    val current = (matchesState.value as? UiState.Success)?.data ?: return
    if (current.isEmpty()) return
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.clearMatches(current.map { it.id })
      _isBusy.value = false
      _statusMessage.value = res.fold(
        onSuccess = { "Cleared ${current.size} matches from dataset" },
        onFailure = { it.localizedMessage ?: "Failed to clear dataset" },
      )
    }
  }

  fun loadSampleSeason() {
    viewModelScope.launch {
      _isBusy.value = true
      val sample = withContext(Dispatchers.Default) { SampleLeagueDataset.generate132Matches() }
      val res = repository.importMatchesBatch(sample)
      _isBusy.value = false
      _statusMessage.value = res.fold(
        onSuccess = { count -> "Loaded $count deterministic league matches" },
        onFailure = { it.localizedMessage ?: "Failed to load sample dataset" },
      )
    }
  }

  fun syncLeagueFromApi(leagueId: String, leagueName: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val fetched = apiService.fetchLeagueFromSportsDb(leagueId)
      if (fetched.isFailure) {
        _isBusy.value = false
        _statusMessage.value = fetched.exceptionOrNull()?.localizedMessage ?: "API fetch failed"
        return@launch
      }
      val matches = fetched.getOrThrow()
      val saved = repository.importMatchesBatch(matches)
      _isBusy.value = false
      _statusMessage.value = saved.fold(
        onSuccess = { count -> "Imported $count $leagueName fixtures via API" },
        onFailure = { it.localizedMessage ?: "Failed to save imported matches" },
      )
    }
  }

  fun syncCustomApiUrl(url: String, apiToken: String?) {
    viewModelScope.launch {
      _isBusy.value = true
      val fetched = apiService.fetchCustomEndpoint(url, apiToken)
      if (fetched.isFailure) {
        _isBusy.value = false
        _statusMessage.value = fetched.exceptionOrNull()?.localizedMessage ?: "Custom API fetch failed"
        return@launch
      }
      val saved = repository.importMatchesBatch(fetched.getOrThrow())
      _isBusy.value = false
      _statusMessage.value = saved.fold(
        onSuccess = { count -> "Imported $count matches from custom endpoint" },
        onFailure = { it.localizedMessage ?: "Failed to save imported matches" },
      )
    }
  }

  fun importRawJson(rawJson: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val parsed = try {
        withContext(Dispatchers.Default) {
          apiService.parseMatchesJson(rawJson, defaultSource = "api")
        }
      } catch (e: Exception) {
        _isBusy.value = false
        _statusMessage.value = "Invalid JSON: ${e.localizedMessage}"
        return@launch
      }
      if (parsed.isEmpty()) {
        _isBusy.value = false
        _statusMessage.value = "No valid Match objects found in JSON."
        return@launch
      }
      val saved = repository.importMatchesBatch(parsed)
      _isBusy.value = false
      _statusMessage.value = saved.fold(
        onSuccess = { count -> "Imported $count matches from JSON payload" },
        onFailure = { it.localizedMessage ?: "Failed to save JSON matches" },
      )
    }
  }

  fun updateWarmup(warmup: Int) {
    _warmupMatches.value = warmup.coerceAtLeast(10)
  }

  fun runWalkForwardBacktest() {
    val docs = (matchesState.value as? UiState.Success)?.data ?: emptyList()
    if (docs.isEmpty()) {
      _statusMessage.value = "Load or add matches before running backtest."
      return
    }
    viewModelScope.launch {
      _isBacktesting.value = true
      val requestedWarmup = _warmupMatches.value
      val effectiveWarmup = requestedWarmup.coerceAtMost((docs.size - 1).coerceAtLeast(10))
      val summary = withContext(Dispatchers.Default) {
        CalibrationEngine.backtest(docs.map { it.toMatchInput() }, warmup = effectiveWarmup)
      }
      _isBacktesting.value = false
      _backtestSummary.value = summary
      if (summary == null) {
        _statusMessage.value = "Not enough post-warmup fixtures with >= 5 team games."
      }
    }
  }

  fun lockPreKickoffRecord(
    fixtureId: String,
    kickoffUtc: String,
    prediction: PredictionOutcome.Ready,
    onDone: (Boolean, String) -> Unit,
  ) {
    viewModelScope.launch {
      val built = PredictionRecordBuilder.create(
        fixtureId = fixtureId,
        kickoffUtc = kickoffUtc,
        prediction = prediction,
      )
      if (built.isFailure) {
        val msg = built.exceptionOrNull()?.localizedMessage ?: "Pre-kickoff validation failed"
        _statusMessage.value = msg
        onDone(false, msg)
        return@launch
      }
      _isBusy.value = true
      val payload = built.getOrThrow()
      val res = repository.lockPredictionRecord(payload)
      _isBusy.value = false
      res.fold(
        onSuccess = {
          val msg = "Locked immutable record (${payload.sha256.take(10)}...)"
          _statusMessage.value = msg
          onDone(true, msg)
        },
        onFailure = {
          val msg = it.localizedMessage ?: "Failed to lock prediction record"
          _statusMessage.value = msg
          onDone(false, msg)
        },
      )
    }
  }

  private companion object {
    const val TAG = "FootballVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
