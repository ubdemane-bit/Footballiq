package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MatchDoc
import com.example.data.remote.FootballLeagues
import com.example.engine.MathUtils
import com.example.engine.TeamRatingSnapshot
import com.example.ui.components.EmptyDatasetCard
import com.example.ui.components.FormPillsRow
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MatchesScreen(
  matches: List<MatchDoc>,
  leaderboard: List<TeamRatingSnapshot>,
  isBusy: Boolean,
  showAddDialog: Boolean,
  showApiDialog: Boolean,
  onSetShowAddDialog: (Boolean) -> Unit,
  onSetShowApiDialog: (Boolean) -> Unit,
  onAddMatch: (home: String, away: String, hg: Int, ag: Int, dateIso: String) -> Unit,
  onDeleteMatch: (String) -> Unit,
  onClearAllMatches: () -> Unit,
  onLoadSampleSeason: () -> Unit,
  onSyncLeagueApi: (leagueId: String, leagueName: String) -> Unit,
  onSyncCustomUrl: (url: String, token: String?) -> Unit,
  onImportRawJson: (rawJson: String) -> Unit,
) {
  var viewMode by remember { mutableIntStateOf(0) } // 0 = Matches, 1 = Elo Rankings
  var showClearConfirm by remember { mutableStateOf(false) }

  Column(modifier = Modifier.fillMaxSize()) {
    // Top Toolbar Actions
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = { onSetShowAddDialog(true) },
          enabled = !isBusy,
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("add_match_button"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Add Match", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
          onClick = { onSetShowApiDialog(true) },
          enabled = !isBusy,
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("plug_api_button"),
        ) {
          Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Plug API", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = viewMode == 0,
            onClick = { viewMode = 0 },
            label = { Text("Fixtures (${matches.size})") },
            modifier = Modifier.testTag("tab_fixtures_chip"),
          )
          FilterChip(
            selected = viewMode == 1,
            onClick = { viewMode = 1 },
            label = { Text("Elo Table (${leaderboard.size})") },
            modifier = Modifier.testTag("tab_elo_chip"),
          )
        }

        if (matches.isNotEmpty()) {
          IconButton(
            onClick = { showClearConfirm = true },
            enabled = !isBusy,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("clear_matches_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteSweep,
              contentDescription = "Clear all matches",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }

    if (matches.isEmpty()) {
      Box(modifier = Modifier.padding(horizontal = 16.dp)) {
        EmptyDatasetCard(
          onLoadSampleSeason = onLoadSampleSeason,
          onOpenApiImport = { onSetShowApiDialog(true) },
          onOpenAddMatch = { onSetShowAddDialog(true) },
          isBusy = isBusy,
        )
      }
    } else if (viewMode == 0) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(matches, key = { it.id }) { match ->
          MatchItemCard(
            match = match,
            onDelete = { onDeleteMatch(match.id) },
            enabled = !isBusy,
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(leaderboard.withIndex().toList(), key = { it.value.team }) { (idx, snap) ->
          TeamEloRowCard(rank = idx + 1, snapshot = snap)
        }
      }
    }
  }

  if (showAddDialog) {
    AddMatchDialog(
      onDismiss = { onSetShowAddDialog(false) },
      onSubmit = { h, a, hg, ag, date ->
        onAddMatch(h, a, hg, ag, date)
        onSetShowAddDialog(false)
      },
    )
  }

  if (showApiDialog) {
    PlugFootballApiDialog(
      isBusy = isBusy,
      onDismiss = { onSetShowApiDialog(false) },
      onLoadSampleSeason = {
        onLoadSampleSeason()
        onSetShowApiDialog(false)
      },
      onSyncLeague = { id, name ->
        onSyncLeagueApi(id, name)
        onSetShowApiDialog(false)
      },
      onSyncCustomUrl = { url, token ->
        onSyncCustomUrl(url, token)
        onSetShowApiDialog(false)
      },
      onImportRawJson = { json ->
        onImportRawJson(json)
        onSetShowApiDialog(false)
      },
    )
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      title = { Text("Clear Match Dataset?") },
      text = {
        Text("This deletes all ${matches.size} logged matches from your active dataset. Immutable prediction records in the Ledger are not affected.")
      },
      confirmButton = {
        Button(
          onClick = {
            showClearConfirm = false
            onClearAllMatches()
          },
          modifier = Modifier.testTag("confirm_clear_matches_button"),
        ) {
          Text("Clear All")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun MatchItemCard(
  match: MatchDoc,
  onDelete: () -> Unit,
  enabled: Boolean,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = match.matchDate,
            style = MonoMetricSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(
              text = match.source.uppercase(),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
        Text(
          text = "${match.home}  ${match.hg} - ${match.ag}  ${match.away}",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      IconButton(
        onClick = onDelete,
        enabled = enabled,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Filled.DeleteOutline,
          contentDescription = "Delete match ${match.home} vs ${match.away}",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun TeamEloRowCard(rank: Int, snapshot: TeamRatingSnapshot) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f),
      ) {
        Box(
          modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
              if (rank <= 4) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceContainerHigh
            ),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "$rank",
            style = MonoMetricSmall,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(
            text = snapshot.team,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Att ${MathUtils.formatFixed(snapshot.attackStrength, 2)} · Def ${MathUtils.formatFixed(snapshot.defenseStrength, 2)}",
              style = MonoMetricSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FormPillsRow(form = snapshot.recentForm)
          }
        }
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = MathUtils.formatFixed(snapshot.elo, 0),
          style = MonoMetricMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "${snapshot.totalMatches} matches",
          style = MonoMetricSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun AddMatchDialog(
  onDismiss: () -> Unit,
  onSubmit: (home: String, away: String, hg: Int, ag: Int, dateIso: String) -> Unit,
) {
  val todayIso = remember {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
  }
  var home by remember { mutableStateOf("") }
  var away by remember { mutableStateOf("") }
  var hgText by remember { mutableStateOf("1") }
  var agText by remember { mutableStateOf("0") }
  var dateIso by remember { mutableStateOf(todayIso) }
  var err by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Log Finished Match") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = home,
          onValueChange = { home = it; err = null },
          label = { Text("Home Team") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("add_match_home_input"),
        )
        OutlinedTextField(
          value = away,
          onValueChange = { away = it; err = null },
          label = { Text("Away Team") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("add_match_away_input"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = hgText,
            onValueChange = { hgText = it.filter { ch -> ch.isDigit() }.take(2); err = null },
            label = { Text("Home Goals") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag("add_match_hg_input"),
          )
          OutlinedTextField(
            value = agText,
            onValueChange = { agText = it.filter { ch -> ch.isDigit() }.take(2); err = null },
            label = { Text("Away Goals") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag("add_match_ag_input"),
          )
        }
        OutlinedTextField(
          value = dateIso,
          onValueChange = { dateIso = it; err = null },
          label = { Text("Date (YYYY-MM-DD)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("add_match_date_input"),
        )
        if (err != null) {
          Text(err!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val h = home.trim()
          val a = away.trim()
          val hg = hgText.toIntOrNull()
          val ag = agText.toIntOrNull()
          val d = dateIso.trim()
          when {
            h.isEmpty() || a.isEmpty() -> err = "Both team names are required."
            h.equals(a, ignoreCase = true) -> err = "Home and away teams must differ."
            hg == null || ag == null || hg !in 0..30 || ag !in 0..30 -> err = "Goals must be 0..30."
            !Regex("""^\d{4}-\d{2}-\d{2}$""").matches(d) -> err = "Use YYYY-MM-DD date format."
            else -> onSubmit(h, a, hg, ag, d)
          }
        },
        modifier = Modifier.testTag("confirm_add_match_button"),
      ) {
        Text("Save Match")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlugFootballApiDialog(
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onLoadSampleSeason: () -> Unit,
  onSyncLeague: (leagueId: String, leagueName: String) -> Unit,
  onSyncCustomUrl: (url: String, token: String?) -> Unit,
  onImportRawJson: (rawJson: String) -> Unit,
) {
  var tabIdx by remember { mutableIntStateOf(0) }
  var customUrl by remember { mutableStateOf("https://api.football-data.org/v4/competitions/PL/matches?status=FINISHED") }
  var apiToken by remember { mutableStateOf("") }
  var rawJson by remember {
    mutableStateOf(
      """[{"home":"Arsenal","away":"Chelsea","hg":2,"ag":1,"date":"2026-11-10"}]"""
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Plug Football API") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        SecondaryTabRow(selectedTabIndex = tabIdx) {
          Tab(selected = tabIdx == 0, onClick = { tabIdx = 0 }, text = { Text("Leagues") })
          Tab(selected = tabIdx == 1, onClick = { tabIdx = 1 }, text = { Text("API URL") })
          Tab(selected = tabIdx == 2, onClick = { tabIdx = 2 }, text = { Text("JSON") })
        }

        when (tabIdx) {
          0 -> {
            Text(
              text = "Fetch completed 2026/27 season fixtures via TheSportsDB public API or load the deterministic 132-match 26/27 season:",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FootballLeagues.presets.forEach { league ->
              OutlinedButton(
                onClick = { onSyncLeague(league.sportsDbLeagueId, league.name) },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
              ) {
                Text("${league.name} (${league.country}) · 26/27")
              }
            }
            Button(
              onClick = onLoadSampleSeason,
              enabled = !isBusy,
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("dialog_load_sample_button"),
            ) {
              Icon(Icons.Filled.Dataset, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Load 132-Match Dataset")
            }
          }

          1 -> {
            Text(
              text = "Supports football-data.org v4, TheSportsDB, or arrays of {home, away, hg, ag, date}.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = customUrl,
              onValueChange = { customUrl = it },
              label = { Text("Endpoint URL") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = apiToken,
              onValueChange = { apiToken = it },
              label = { Text("X-Auth-Token (Optional)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            Button(
              onClick = { onSyncCustomUrl(customUrl, apiToken.ifBlank { null }) },
              enabled = !isBusy && customUrl.isNotBlank(),
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
            ) {
              Text("Fetch & Import")
            }
          }

          2 -> {
            Text(
              text = "Paste a JSON array of Match objects or API response payload:",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = rawJson,
              onValueChange = { rawJson = it },
              label = { Text("JSON Payload") },
              minLines = 4,
              maxLines = 8,
              modifier = Modifier.fillMaxWidth().testTag("raw_json_input"),
            )
            Button(
              onClick = { onImportRawJson(rawJson) },
              enabled = !isBusy && rawJson.isNotBlank(),
              modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("import_raw_json_button"),
            ) {
              Text("Parse & Import JSON")
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
  )
}
