package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.model.MatchDoc
import com.example.data.model.PredictionRecordDoc
import com.example.data.model.UiState
import com.example.data.remote.FootballApiService
import com.example.data.repository.FootballRepository
import com.example.engine.SeasonFilter
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BacktestScreen
import com.example.ui.screens.MatchesScreen
import com.example.ui.screens.PredictScreen
import com.example.ui.screens.PredictionHistoryScreen
import com.example.ui.screens.ScanMatchScreen
import com.example.ui.theme.FootballIqTheme
import com.example.ui.viewmodel.FootballViewModel
import com.example.ui.viewmodel.SeasonScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class AppTab(
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
) {
  PREDICT(R.string.nav_predict, Icons.Filled.Analytics, Icons.Outlined.Analytics, "nav_predict"),
  SCAN(R.string.nav_scan, Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt, "nav_scan"),
  MATCHES(R.string.nav_matches, Icons.Filled.SportsSoccer, Icons.Outlined.SportsSoccer, "nav_matches"),
  BACKTEST(R.string.nav_backtest, Icons.Filled.QueryStats, Icons.Outlined.QueryStats, "nav_backtest"),
  HISTORY(R.string.nav_history, Icons.Filled.History, Icons.Outlined.History, "nav_history"),
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      FootballIqTheme {
        AppNavigation()
      }
    }
  }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      MainFeatureShell(
        currentUserId = user.uid,
        userEmail = user.email.orEmpty(),
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFeatureShell(
  currentUserId: String,
  userEmail: String,
) {
  val viewModel: FootballViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) as Application
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        FootballViewModel(
          repository = FootballRepository(db),
          apiService = FootballApiService(),
          currentUserId = currentUserId,
        )
      }
    },
  )

  val matchesState by viewModel.matchesState.collectAsStateWithLifecycle()
  val ledgerState by viewModel.ledgerState.collectAsStateWithLifecycle()
  val dashboard by viewModel.dashboardState.collectAsStateWithLifecycle()
  val warmupMatches by viewModel.warmupMatches.collectAsStateWithLifecycle()
  val backtestSummary by viewModel.backtestSummary.collectAsStateWithLifecycle()
  val isBacktesting by viewModel.isBacktesting.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val isRefreshingHistory by viewModel.isRefreshingHistory.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

  var activeTab by rememberSaveable { mutableStateOf(AppTab.PREDICT) }
  var showAddMatchDialog by rememberSaveable { mutableStateOf(false) }
  var showApiImportDialog by rememberSaveable { mutableStateOf(false) }
  var showAccountMenu by remember { mutableStateOf(false) }

  val snackbarHostState = remember { SnackbarHostState() }
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  LaunchedEffect(statusMessage) {
    val msg = statusMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  val matchesList: List<MatchDoc> = (matchesState as? UiState.Success)?.data ?: emptyList()
  val ledgerList: List<PredictionRecordDoc> = (ledgerState as? UiState.Success)?.data ?: emptyList()

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      topBar = {
        Column {
          TopAppBar(
            title = {
              Column {
                Text(
                  text = stringResource(R.string.app_name),
                  style = MaterialTheme.typography.titleLarge,
                )
                Text(
                  text = "${stringResource(activeTab.labelRes)} · ${dashboard.seasonScope.label}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background,
              titleContentColor = MaterialTheme.colorScheme.onBackground,
            ),
            actions = {
              IconButton(
                onClick = { activeTab = AppTab.HISTORY },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("topbar_history_button"),
              ) {
                BadgedBox(
                  badge = {
                    if (ledgerList.isNotEmpty()) {
                      Badge { Text("${ledgerList.size.coerceAtMost(99)}") }
                    }
                  },
                ) {
                  Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "Open prediction history",
                  )
                }
              }
              Box {
                IconButton(
                  onClick = { showAccountMenu = true },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("account_menu_button"),
                ) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Account options and sign out",
                  )
                }
                DropdownMenu(
                  expanded = showAccountMenu,
                  onDismissRequest = { showAccountMenu = false },
                ) {
                  if (userEmail.isNotBlank()) {
                    DropdownMenuItem(
                      text = { Text(userEmail, style = MaterialTheme.typography.bodySmall) },
                      onClick = {},
                      enabled = false,
                    )
                  }
                  DropdownMenuItem(
                    text = { Text(stringResource(R.string.sign_out)) },
                    leadingIcon = {
                      Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    },
                    onClick = {
                      showAccountMenu = false
                      signOutUser(credentialManager = credentialManager, scope = scope)
                    },
                    modifier = Modifier.testTag("sign_out_menu_item"),
                  )
                }
              }
            },
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        }
      },
      bottomBar = {
        if (!useNavRail) {
          NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            AppTab.entries.forEach { tab ->
              val selected = activeTab == tab
              NavigationBarItem(
                selected = selected,
                onClick = { activeTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useNavRail) {
          NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            AppTab.entries.forEach { tab ->
              val selected = activeTab == tab
              NavigationRailItem(
                selected = selected,
                onClick = { activeTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = stringResource(tab.labelRes),
                  )
                },
                label = { Text(stringResource(tab.labelRes)) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }

        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
        ) {
          Box(
            modifier = Modifier
              .widthIn(max = 820.dp)
              .fillMaxSize(),
          ) {
            Crossfade(
              targetState = activeTab,
              animationSpec = tween(durationMillis = 180),
              label = "tabCrossfade",
            ) { tab ->
              when (tab) {
              AppTab.PREDICT -> {
                when (val ms = matchesState) {
                  is UiState.Loading -> CenteredLoading()
                  is UiState.Error -> ErrorBanner(ms.message)
                  is UiState.Success -> {
                    PredictScreen(
                      dashboard = dashboard,
                      isBusy = isBusy,
                      onSelectHome = viewModel::selectHomeTeam,
                      onSelectAway = viewModel::selectAwayTeam,
                      onSwapTeams = viewModel::swapTeams,
                      onSelectSeasonScope = viewModel::setSeasonScope,
                      onSelectRecentWindow = viewModel::setRecentGamesWindow,
                      onGeneratePrediction = viewModel::generatePrediction,
                      onLoadSampleSeason = viewModel::loadSampleSeason,
                      onOpenApiImport = {
                        activeTab = AppTab.MATCHES
                        showApiImportDialog = true
                      },
                      onOpenAddMatch = {
                        activeTab = AppTab.MATCHES
                        showAddMatchDialog = true
                      },
                      onLockRecord = viewModel::lockPreKickoffRecord,
                    )
                  }
                }
              }

              AppTab.SCAN -> {
                when (val ms = matchesState) {
                  is UiState.Loading -> CenteredLoading()
                  is UiState.Error -> ErrorBanner(ms.message)
                  is UiState.Success -> {
                    val scanInputs = remember(matchesList, dashboard.seasonScope) {
                      val all = matchesList.map { it.toMatchInput() }
                      if (dashboard.seasonScope == SeasonScope.SEASON_26_27) {
                        SeasonFilter.filterSeason2627(all)
                      } else {
                        all
                      }
                    }
                    ScanMatchScreen(
                      matches = scanInputs,
                      knownTeams = dashboard.teams,
                      isBusy = isBusy,
                      onLoadSampleSeason = viewModel::loadSampleSeason,
                      onOpenFullPrediction = { home, away ->
                        viewModel.selectFixture(home, away)
                        activeTab = AppTab.PREDICT
                      },
                    )
                  }
                }
              }

              AppTab.MATCHES -> {
                when (val ms = matchesState) {
                  is UiState.Loading -> CenteredLoading()
                  is UiState.Error -> ErrorBanner(ms.message)
                  is UiState.Success -> {
                    MatchesScreen(
                      matches = matchesList,
                      leaderboard = dashboard.leaderboard,
                      isBusy = isBusy,
                      showAddDialog = showAddMatchDialog,
                      showApiDialog = showApiImportDialog,
                      onSetShowAddDialog = { showAddMatchDialog = it },
                      onSetShowApiDialog = { showApiImportDialog = it },
                      onAddMatch = viewModel::addMatch,
                      onDeleteMatch = viewModel::deleteMatch,
                      onClearAllMatches = viewModel::clearAllMatches,
                      onLoadSampleSeason = viewModel::loadSampleSeason,
                      onSyncLeagueApi = viewModel::syncLeagueFromApi,
                      onSyncCustomUrl = viewModel::syncCustomApiUrl,
                      onImportRawJson = viewModel::importRawJson,
                    )
                  }
                }
              }

              AppTab.BACKTEST -> {
                when (val ms = matchesState) {
                  is UiState.Loading -> CenteredLoading()
                  is UiState.Error -> ErrorBanner(ms.message)
                  is UiState.Success -> {
                    BacktestScreen(
                      totalMatches = matchesList.size,
                      warmupMatches = warmupMatches,
                      summary = backtestSummary,
                      isBacktesting = isBacktesting,
                      isBusy = isBusy,
                      onUpdateWarmup = viewModel::updateWarmup,
                      onRunBacktest = viewModel::runWalkForwardBacktest,
                      onLoadSampleSeason = viewModel::loadSampleSeason,
                      onOpenApiImport = {
                        activeTab = AppTab.MATCHES
                        showApiImportDialog = true
                      },
                      onOpenAddMatch = {
                        activeTab = AppTab.MATCHES
                        showAddMatchDialog = true
                      },
                    )
                  }
                }
              }

              AppTab.HISTORY -> {
                when (val ls = ledgerState) {
                  is UiState.Loading -> CenteredLoading()
                  is UiState.Error -> ErrorBanner(ls.message)
                  is UiState.Success -> {
                    PredictionHistoryScreen(
                      records = ledgerList,
                      matches = matchesList,
                      isRefreshing = isRefreshingHistory,
                      onRefresh = viewModel::refreshPredictionHistory,
                      onInspectInPredict = { home, away ->
                        viewModel.selectFixture(home, away)
                        activeTab = AppTab.PREDICT
                      },
                    )
                  }
                }
              }
            }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CenteredLoading() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}

@Composable
private fun ErrorBanner(message: String) {
  Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
    Surface(
      color = MaterialTheme.colorScheme.errorContainer,
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
