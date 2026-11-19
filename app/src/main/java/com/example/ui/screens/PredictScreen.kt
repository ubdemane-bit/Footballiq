package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.ConfidenceLevel
import com.example.engine.FootballIqConstants
import com.example.engine.MathUtils
import com.example.engine.PredictionOutcome
import com.example.engine.PredictionRecordBuilder
import com.example.engine.TeamRatingSnapshot
import com.example.ui.components.ConfidenceBadge
import com.example.ui.components.EmptyDatasetCard
import com.example.ui.components.FormPillsRow
import com.example.ui.components.PoissonHeatmapGrid
import com.example.ui.components.ProbabilitySegmentedBar
import com.example.ui.components.RecentSeasonAnalysisCard
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.viewmodel.ModelDashboardState
import com.example.ui.viewmodel.SeasonScope

@Composable
fun PredictScreen(
  dashboard: ModelDashboardState,
  isBusy: Boolean,
  onSelectHome: (String) -> Unit,
  onSelectAway: (String) -> Unit,
  onSwapTeams: () -> Unit,
  onSelectSeasonScope: (SeasonScope) -> Unit,
  onSelectRecentWindow: (Int) -> Unit,
  onGeneratePrediction: () -> Unit,
  onLoadSampleSeason: () -> Unit,
  onOpenApiImport: () -> Unit,
  onOpenAddMatch: () -> Unit,
  onLockRecord: (fixtureId: String, kickoffUtc: String, prediction: PredictionOutcome.Ready, onDone: (Boolean, String) -> Unit) -> Unit,
) {
  var showLockDialog by remember { mutableStateOf(false) }
  var generatePulseCount by remember { mutableIntStateOf(0) }
  val scrollState = rememberScrollState()

  LaunchedEffect(generatePulseCount, dashboard.hasGeneratedPrediction) {
    if (generatePulseCount > 0 && dashboard.hasGeneratedPrediction) {
      scrollState.animateScrollTo(260)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (dashboard.totalDatasetMatches == 0) {
      EmptyDatasetCard(
        onLoadSampleSeason = onLoadSampleSeason,
        onOpenApiImport = onOpenApiImport,
        onOpenAddMatch = onOpenAddMatch,
        isBusy = isBusy,
      )
      return@Column
    }

    if (dashboard.teams.size < 2) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "No 2026/27 Season Matches Found",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Your dataset has ${dashboard.totalDatasetMatches} matches outside the 2026/27 season window (2026-07-01 to 2027-06-30). Load the 26/27 season dataset, sync a 26/27 league, or switch to All Seasons.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = onLoadSampleSeason,
              enabled = !isBusy,
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
            ) {
              Text("Load 26/27 Dataset")
            }
            OutlinedButton(
              onClick = { onSelectSeasonScope(SeasonScope.ALL_MATCHES) },
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
            ) {
              Text("Use All Seasons")
            }
          }
        }
      }
      return@Column
    }

    // Header Telemetry Banner
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(104.dp)
        .clip(RoundedCornerShape(18.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
    ) {
      Image(
        painter = painterResource(id = R.drawable.img_pitch_hero),
        contentDescription = "Football pitch analytics header",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.horizontalGradient(
              listOf(
                Color(0xFF0A0F14).copy(alpha = 0.92f),
                Color(0xFF0A0F14).copy(alpha = 0.65f),
              )
            )
          )
          .padding(16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "POISSON × ELO ENGINE · ${dashboard.seasonScope.label.uppercase()}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "${dashboard.totalMatches} Matches · ${dashboard.teams.size} Clubs",
              style = MaterialTheme.typography.titleLarge,
              color = Color.White,
            )
            Text(
              text = "Recent Window: Last ${dashboard.recentGamesWindow}g · Avg xG: ${MathUtils.formatFixed(dashboard.avgHomeGoals, 2)} H / ${MathUtils.formatFixed(dashboard.avgAwayGoals, 2)} A",
              style = MonoMetricSmall,
              color = Color(0xFFCBD5E1),
            )
          }
        }
      }
    }

    // Team Picker Row
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TeamDropdownSelector(
            label = "Home",
            selectedTeam = dashboard.selectedHome,
            teams = dashboard.teams,
            onSelect = onSelectHome,
            testTag = "home_team_selector",
            modifier = Modifier.weight(1f),
          )
          FilledTonalIconButton(
            onClick = onSwapTeams,
            modifier = Modifier
              .padding(top = 6.dp)
              .minimumInteractiveComponentSize()
              .testTag("swap_teams_button"),
          ) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap home and away teams")
          }
          TeamDropdownSelector(
            label = "Away",
            selectedTeam = dashboard.selectedAway,
            teams = dashboard.teams,
            onSelect = onSelectAway,
            testTag = "away_team_selector",
            modifier = Modifier.weight(1f),
          )
        }

        val homeSnap = dashboard.leaderboard.firstOrNull { it.team == dashboard.selectedHome }
        val awaySnap = dashboard.leaderboard.firstOrNull { it.team == dashboard.selectedAway }
        if (homeSnap != null && awaySnap != null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            TeamMetaSummary(snapshot = homeSnap, alignEnd = false, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            TeamMetaSummary(snapshot = awaySnap, alignEnd = true, modifier = Modifier.weight(1f))
          }

          val eloDiff = homeSnap.elo - awaySnap.elo
          val edgeText = when {
            eloDiff >= 15.0 -> "${homeSnap.team} +${MathUtils.formatFixed(eloDiff, 0)} Elo edge"
            eloDiff <= -15.0 -> "${awaySnap.team} +${MathUtils.formatFixed(-eloDiff, 0)} Elo edge"
            else -> "Balanced matchup (${MathUtils.formatFixed(kotlin.math.abs(eloDiff), 0)} Elo diff)"
          }
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = edgeText,
              style = MonoMetricSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }

        val canGenerate = dashboard.selectedHome.isNotBlank() &&
          dashboard.selectedAway.isNotBlank() &&
          dashboard.selectedHome != dashboard.selectedAway

        // 26/27 Season Scope & Recent Games Window Controls
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "ANALYSIS SCOPE & RECENT GAMES",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilterChip(
              selected = dashboard.seasonScope == SeasonScope.SEASON_26_27,
              onClick = { onSelectSeasonScope(SeasonScope.SEASON_26_27) },
              label = { Text("26/27 Season (${dashboard.season2627MatchesCount})") },
              modifier = Modifier.testTag("scope_26_27_chip"),
            )
            FilterChip(
              selected = dashboard.seasonScope == SeasonScope.ALL_MATCHES,
              onClick = { onSelectSeasonScope(SeasonScope.ALL_MATCHES) },
              label = { Text("All (${dashboard.totalDatasetMatches})") },
              modifier = Modifier.testTag("scope_all_chip"),
            )
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            listOf(5 to "Last 5", 10 to "Last 10", 15 to "Last 15", 38 to "Full").forEach { (win, label) ->
              FilterChip(
                selected = dashboard.recentGamesWindow == win,
                onClick = { onSelectRecentWindow(win) },
                label = { Text(label) },
                modifier = Modifier.testTag("recent_window_${win}_chip"),
              )
            }
          }
        }

        if (dashboard.hasGeneratedPrediction) {
          OutlinedButton(
            onClick = {
              onGeneratePrediction()
              generatePulseCount++
            },
            enabled = canGenerate && !isBusy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .minimumInteractiveComponentSize()
              .testTag("generate_prediction_button"),
          ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Regenerate Prediction", style = MaterialTheme.typography.labelLarge)
          }
        } else {
          Button(
            onClick = {
              onGeneratePrediction()
              generatePulseCount++
            },
            enabled = canGenerate && !isBusy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .minimumInteractiveComponentSize()
              .testTag("generate_prediction_button"),
          ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.generate_prediction), style = MaterialTheme.typography.titleMedium)
          }
        }
      }
    }

    // Prediction Output
    if (dashboard.isSelectionPendingGeneration) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
          .testTag("pending_generation_card"),
      ) {
        Row(
          modifier = Modifier.padding(18.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "${dashboard.selectedHome} vs ${dashboard.selectedAway} Selected",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "Tap Generate Prediction above to compute Poisson expected goals, 1X2 probabilities, Safe/Smart/Exact picks, and the score matrix.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    AnimatedVisibility(
      visible = dashboard.prediction != null,
      enter = fadeIn(tween(220)) + expandVertically(tween(240)),
      exit = fadeOut(tween(150)),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (val pred = dashboard.prediction) {
          null -> Unit
          is PredictionOutcome.InsufficientData -> {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
            ) {
              Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
              ) {
                Icon(
                  imageVector = Icons.Filled.Info,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.tertiary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                  Text(
                    text = pred.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                  )
                  Text(
                    text = "${pred.home}: ${pred.homeGames} matches · ${pred.away}: ${pred.awayGames} matches (minimum ${pred.minRequired} per team required).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }

          is PredictionOutcome.Ready -> {
            // Expected Goals & 1X2 Distribution Card
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text(
                      text = "EXPECTED GOALS (xG)",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Text(
                        text = MathUtils.formatFixed(pred.expectedHomeGoals, 2),
                        style = MonoMetricLarge,
                        color = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text = "vs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                      Text(
                        text = MathUtils.formatFixed(pred.expectedAwayGoals, 2),
                        style = MonoMetricLarge,
                        color = MaterialTheme.colorScheme.secondary,
                      )
                    }
                  }

                  Column(horizontalAlignment = Alignment.End) {
                    Text(
                      text = "BTTS / O2.5",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                      text = "${MathUtils.formatFixed(pred.bttsYes * 100, 0)}% · ${MathUtils.formatFixed(pred.over25 * 100, 0)}%",
                      style = MonoMetricMedium,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }

                ProbabilitySegmentedBar(
                  probHome = pred.probHome,
                  probDraw = pred.probDraw,
                  probAway = pred.probAway,
                  homeName = pred.home,
                  awayName = pred.away,
                )
              }
            }

            // 3 Prediction Option Cards
            OptionCard(
              badge = "OPTION 1 · SAFE (DOUBLE CHANCE)",
              icon = Icons.Filled.Shield,
              pick = pred.option1Safe.pick,
              probability = pred.option1Safe.probability,
              confidence = pred.option1Safe.confidence,
              why = pred.option1Safe.why,
              accent = MaterialTheme.colorScheme.primary,
            )

            OptionCard(
              badge = "OPTION 2 · SMART (BEST MARKET)",
              icon = Icons.Filled.Bolt,
              pick = pred.option2Smart.pick,
              probability = pred.option2Smart.probability,
              confidence = pred.option2Smart.confidence,
              why = pred.option2Smart.why,
              accent = MaterialTheme.colorScheme.secondary,
            )

            OptionCard(
              badge = "OPTION 3 · ADVANCED (MODAL SCORE & HT)",
              icon = Icons.Filled.AutoAwesome,
              pick = "Score ${pred.option3Advanced.correctScore} · HT ${pred.option3Advanced.halfTime} (${MathUtils.formatFixed(pred.option3Advanced.halfTimeProbability * 100, 1)}%)",
              probability = pred.option3Advanced.probability,
              confidence = pred.option3Advanced.confidence,
              why = "${pred.option3Advanced.why} Exp total goals: ${MathUtils.formatFixed(pred.option3Advanced.expectedTotalGoals, 2)}.",
              accent = MaterialTheme.colorScheme.tertiary,
            )

            // 26/27 Season Recent Games & Head-to-Head Breakdown
            RecentSeasonAnalysisCard(
              seasonLabel = dashboard.seasonScope.label,
              windowGames = dashboard.recentGamesWindow,
              homeAnalysis = dashboard.homeRecentAnalysis,
              awayAnalysis = dashboard.awayRecentAnalysis,
              headToHeadMatches = dashboard.headToHeadMatches,
            )

            // Poisson Score Matrix Heatmap
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
            ) {
              PoissonHeatmapGrid(
                matrix = pred.scoreMatrix,
                homeName = pred.home,
                awayName = pred.away,
                modalScore = pred.option3Advanced.correctScore,
                modifier = Modifier.padding(16.dp),
              )
            }

            // Lock Immutable Pre-Kickoff Record CTA
            Button(
              onClick = { showLockDialog = true },
              enabled = !isBusy,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .minimumInteractiveComponentSize()
                .testTag("open_lock_record_dialog_button"),
            ) {
              Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.lock_prediction), style = MaterialTheme.typography.titleMedium)
            }

            if (showLockDialog) {
              LockPreKickoffDialog(
                prediction = pred,
                onDismiss = { showLockDialog = false },
                onConfirm = { fixtureId, kickoffUtc, onResult ->
                  onLockRecord(fixtureId, kickoffUtc, pred) { ok, msg ->
                    onResult(ok, msg)
                    if (ok) showLockDialog = false
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeamDropdownSelector(
  label: String,
  selectedTeam: String,
  teams: List<String>,
  onSelect: (String) -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = !expanded },
    modifier = modifier,
  ) {
    OutlinedTextField(
      value = selectedTeam,
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier
        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        .fillMaxWidth()
        .testTag(testTag),
    )
    ExposedDropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
    ) {
      teams.forEach { team ->
        DropdownMenuItem(
          text = { Text(team, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          onClick = {
            onSelect(team)
            expanded = false
          },
        )
      }
    }
  }
}

@Composable
private fun TeamMetaSummary(
  snapshot: TeamRatingSnapshot,
  alignEnd: Boolean,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = "Elo ${MathUtils.formatFixed(snapshot.elo, 0)} · ${snapshot.sampleGames}g",
      style = MonoMetricSmall,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = "Att ${MathUtils.formatFixed(snapshot.attackStrength, 2)} · Def ${MathUtils.formatFixed(snapshot.defenseStrength, 2)}",
      style = MonoMetricSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (snapshot.recentForm.isNotEmpty()) {
      FormPillsRow(form = snapshot.recentForm)
    }
  }
}

@Composable
private fun OptionCard(
  badge: String,
  icon: ImageVector,
  pick: String,
  probability: Double,
  confidence: ConfidenceLevel,
  why: String,
  accent: Color,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
          Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
          )
        }
        ConfidenceBadge(confidence)
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
      ) {
        Text(
          text = pick,
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "${MathUtils.formatFixed(probability * 100.0, 1)}%",
          style = MonoMetricLarge,
          color = accent,
        )
      }

      Text(
        text = why,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun LockPreKickoffDialog(
  prediction: PredictionOutcome.Ready,
  onDismiss: () -> Unit,
  onConfirm: (fixtureId: String, kickoffUtc: String, onResult: (Boolean, String) -> Unit) -> Unit,
) {
  val cleanH = prediction.home.uppercase().replace(Regex("[^A-Z0-9]"), "").take(6)
  val cleanA = prediction.away.uppercase().replace(Regex("[^A-Z0-9]"), "").take(6)
  var fixtureId by remember {
    mutableStateOf("FIX_${cleanH}_${cleanA}_${System.currentTimeMillis() / 1000}")
  }
  var kickoffUtc by remember {
    mutableStateOf(PredictionRecordBuilder.formatUtcIso(System.currentTimeMillis() + 2 * 3600_000L))
  }
  var localError by remember { mutableStateOf<String?>(null) }
  var submitting by remember { mutableStateOf(false) }

  val previewSha = remember(fixtureId, kickoffUtc, prediction.canonicalPayloadJson) {
    PredictionRecordBuilder.create(
      fixtureId = fixtureId,
      kickoffUtc = kickoffUtc,
      prediction = prediction,
    ).getOrNull()?.sha256
  }

  AlertDialog(
    onDismissRequest = { if (!submitting) onDismiss() },
    title = { Text("Seal Pre-Kickoff Record") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Creates an immutable append-only Firestore record sealed with SHA-256. Must be locked strictly before kickoff.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = fixtureId,
          onValueChange = { fixtureId = it; localError = null },
          label = { Text("Fixture ID") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("lock_fixture_id_input"),
        )
        OutlinedTextField(
          value = kickoffUtc,
          onValueChange = { kickoffUtc = it; localError = null },
          label = { Text("Kickoff UTC (ISO-8601)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("lock_kickoff_utc_input"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(2 to "+2h", 6 to "+6h", 24 to "+24h").forEach { (hrs, label) ->
            FilterChip(
              selected = false,
              onClick = {
                kickoffUtc = PredictionRecordBuilder.formatUtcIso(
                  System.currentTimeMillis() + hrs * 3600_000L
                )
                localError = null
              },
              label = { Text(label) },
            )
          }
        }
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "Model: ${FootballIqConstants.MODEL_VERSION}",
              style = MonoMetricSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = if (previewSha != null) "SHA-256: ${previewSha.take(24)}..." else "SHA-256: Invalid or post-kickoff timestamp",
              style = MonoMetricSmall,
              color = if (previewSha != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
          }
        }
        if (localError != null) {
          Text(
            text = localError!!,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          submitting = true
          onConfirm(fixtureId, kickoffUtc) { ok, msg ->
            submitting = false
            if (!ok) localError = msg
          }
        },
        enabled = !submitting,
        modifier = Modifier.testTag("confirm_lock_record_button"),
      ) {
        Text(if (submitting) "Sealing..." else "Seal & Save")
      }
    },
    dismissButton = {
      OutlinedButton(onClick = onDismiss, enabled = !submitting) {
        Text("Cancel")
      }
    },
  )
}
