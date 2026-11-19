package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MatchDoc
import com.example.data.model.PredictionRecordDoc
import com.example.engine.MathUtils
import com.example.engine.ParsedPredictionPayload
import com.example.engine.PredictionPayloadParser
import com.example.engine.PredictionRecordBuilder
import com.example.ui.components.ProbabilitySegmentedBar
import com.example.ui.theme.MonoMetricSmall

private enum class HistoryFilter(val label: String) {
  ALL("All"),
  PAST("Past Kickoff"),
  UPCOMING("Upcoming"),
}

@Composable
fun PredictionHistoryScreen(
  records: List<PredictionRecordDoc>,
  matches: List<MatchDoc>,
  isRefreshing: Boolean,
  onRefresh: () -> Unit,
  onInspectInPredict: (home: String, away: String) -> Unit,
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var selectedFilter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
  val nowMillis = remember(records, isRefreshing) { System.currentTimeMillis() }

  val filteredRecords = remember(records, searchQuery, selectedFilter, nowMillis) {
    val q = searchQuery.trim().lowercase()
    records.filter { rec ->
      val kickoffMillis = PredictionRecordBuilder.parseUtcIsoMillis(rec.kickoffUtc)
      val matchesTime = when (selectedFilter) {
        HistoryFilter.ALL -> true
        HistoryFilter.PAST -> kickoffMillis != null && kickoffMillis <= nowMillis
        HistoryFilter.UPCOMING -> kickoffMillis == null || kickoffMillis > nowMillis
      }
      val matchesQuery = q.isEmpty() ||
        rec.homeTeam.lowercase().contains(q) ||
        rec.awayTeam.lowercase().contains(q) ||
        rec.fixtureId.lowercase().contains(q)
      matchesTime && matchesQuery
    }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    // Summary & Explicit Firestore Fetch Header
    Surface(
      color = MaterialTheme.colorScheme.surface,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.History,
                contentDescription = "Prediction history ledger",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
              )
            }
            Column {
              Text(
                text = "PAST PREDICTION HISTORY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "${records.size} sealed records · Original probabilities & SHA-256",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          OutlinedButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("refresh_history_button"),
          ) {
            if (isRefreshing) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
              )
            } else {
              Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Fetch history from Firestore",
                modifier = Modifier.size(16.dp),
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text("Fetch")
          }
        }

        if (records.isNotEmpty()) {
          val verifiedCount = remember(records) { records.count { it.isDigestValid() } }
          val settledCount = remember(records, matches) {
            records.count { findLoggedOutcome(it, matches) != null }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            HistoryMetricPill(
              label = "Sealed",
              value = "${records.size}",
              modifier = Modifier.weight(1f),
            )
            HistoryMetricPill(
              label = "SHA-256 Valid",
              value = "$verifiedCount/${records.size}",
              modifier = Modifier.weight(1f),
            )
            HistoryMetricPill(
              label = "Results Logged",
              value = "$settledCount",
              modifier = Modifier.weight(1f),
            )
          }

          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search team or fixture ID") },
            singleLine = true,
            leadingIcon = {
              Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
              if (searchQuery.isNotEmpty()) {
                IconButton(
                  onClick = { searchQuery = "" },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                  Icon(Icons.Filled.Close, contentDescription = "Clear search")
                }
              }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("history_search_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            HistoryFilter.entries.forEach { filter ->
              FilterChip(
                selected = selectedFilter == filter,
                onClick = { selectedFilter = filter },
                label = { Text(filter.label) },
                modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}"),
              )
            }
          }
        }
      }
    }

    if (records.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.History,
              contentDescription = "Empty prediction history",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(30.dp),
            )
          }
          Text(
            text = "No Past Predictions Saved",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )
          Text(
            text = "Lock a pre-kickoff snapshot on the Predict tab to store fixture details, original 1X2 probabilities, and a tamper-proof SHA-256 record in Firestore.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    } else if (filteredRecords.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "No prediction records match your current filter.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(filteredRecords, key = { it.id.ifBlank { it.sha256 } }) { record ->
          val matchedActual = remember(record, matches) {
            findLoggedOutcome(record, matches)
          }
          PredictionHistoryCard(
            record = record,
            actualResult = matchedActual,
            nowMillis = nowMillis,
            onInspectInPredict = onInspectInPredict,
          )
        }
      }
    }
  }
}

private fun findLoggedOutcome(
  record: PredictionRecordDoc,
  matches: List<MatchDoc>,
): MatchDoc? {
  val kickoffDay = record.kickoffUtc.take(10)
  val samePair = matches.filter {
    it.home.equals(record.homeTeam, ignoreCase = true) &&
      it.away.equals(record.awayTeam, ignoreCase = true)
  }
  return samePair.firstOrNull { it.matchDate == kickoffDay }
    ?: samePair.firstOrNull { it.matchDate >= record.createdUtc.take(10) }
}

@Composable
private fun PredictionHistoryCard(
  record: PredictionRecordDoc,
  actualResult: MatchDoc?,
  nowMillis: Long,
  onInspectInPredict: (home: String, away: String) -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  val isIntegrityOk = remember(record) { record.isDigestValid() }
  val parsed: ParsedPredictionPayload? = remember(record.payloadJson) {
    PredictionPayloadParser.parse(record.payloadJson)
  }
  val isPastKickoff = remember(record.kickoffUtc, nowMillis) {
    val ms = PredictionRecordBuilder.parseUtcIsoMillis(record.kickoffUtc)
    ms != null && ms <= nowMillis
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
      .clickable { expanded = !expanded }
      .testTag("history_record_card"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Top Meta Row: Fixture ID + Past/Upcoming + SHA-256 Badge
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = record.fixtureId,
          style = MonoMetricSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
          color = if (isPastKickoff) {
            MaterialTheme.colorScheme.surfaceContainerHigh
          } else {
            MaterialTheme.colorScheme.tertiaryContainer
          },
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text = if (isPastKickoff) "PAST" else "UPCOMING",
            style = MaterialTheme.typography.labelSmall,
            color = if (isPastKickoff) {
              MaterialTheme.colorScheme.onSurfaceVariant
            } else {
              MaterialTheme.colorScheme.tertiary
            },
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
          )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
          color = if (isIntegrityOk) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.errorContainer
          },
          shape = RoundedCornerShape(8.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              imageVector = if (isIntegrityOk) Icons.Filled.Verified else Icons.Filled.GppBad,
              contentDescription = if (isIntegrityOk) "SHA-256 verified" else "SHA-256 mismatch",
              tint = if (isIntegrityOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = if (isIntegrityOk) "VERIFIED" else "MISMATCH",
              style = MaterialTheme.typography.labelSmall,
              color = if (isIntegrityOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      // Fixture Details Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${record.homeTeam} vs ${record.awayTeam}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Kickoff ${formatShortUtc(record.kickoffUtc)} · Saved ${formatShortUtc(record.createdUtc)}",
            style = MonoMetricSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (actualResult != null) {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(10.dp),
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              horizontalAlignment = Alignment.End,
            ) {
              Text(
                text = "LOGGED FT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "${actualResult.hg} - ${actualResult.ag}",
                style = MonoMetricSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }

      // Original Predicted Probabilities (1X2 + xG)
      if (parsed != null) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "ORIGINAL PREDICTED PROBABILITIES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "xG ${MathUtils.formatFixed(parsed.expectedGoals.home, 2)} – ${MathUtils.formatFixed(parsed.expectedGoals.away, 2)}",
                style = MonoMetricSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
              )
            }

            ProbabilitySegmentedBar(
              probHome = parsed.probabilities.home,
              probDraw = parsed.probabilities.draw,
              probAway = parsed.probabilities.away,
              homeName = record.homeTeam,
              awayName = record.awayTeam,
            )
          }
        }
      }

      // 3 Prediction Options with Original Probability %
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        HistoryPickChip(
          label = "Safe",
          value = record.safePick,
          probPercent = parsed?.option1Safe?.probability?.let { "${MathUtils.formatFixed(it * 100.0, 1)}%" },
          modifier = Modifier.weight(1f),
        )
        HistoryPickChip(
          label = "Smart",
          value = record.smartPick,
          probPercent = parsed?.option2Smart?.probability?.let { "${MathUtils.formatFixed(it * 100.0, 1)}%" },
          modifier = Modifier.weight(1.3f),
        )
        HistoryPickChip(
          label = "Exact",
          value = record.exactScore,
          probPercent = parsed?.option3Advanced?.probability?.let { "${MathUtils.formatFixed(it * 100.0, 1)}%" },
          modifier = Modifier.weight(0.9f),
        )
      }

      // Footer actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = { onInspectInPredict(record.homeTeam, record.awayTeam) },
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("history_reopen_predict_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.Analytics,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Re-run Matchup")
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = if (expanded) "Hide Audit" else "Audit Payload",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse audit details" else "Expand audit details",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      AnimatedVisibility(visible = expanded) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (parsed != null) {
              Text(
                text = "Half-Time Lean: ${parsed.option3Advanced.halfTime} (${MathUtils.formatFixed(parsed.option3Advanced.halfTimeProbability * 100.0, 1)}%) · Expected Total Goals: ${MathUtils.formatFixed(parsed.option3Advanced.expectedTotalGoals, 2)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
            Text(
              text = "Model: ${record.modelVersion}",
              style = MonoMetricSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "SHA-256: ${record.sha256}",
              style = MonoMetricSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = record.payloadJson,
              style = MonoMetricSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HistoryPickChip(
  label: String,
  value: String,
  probPercent: String?,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(8.dp),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = label.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (probPercent != null) {
          Text(
            text = probPercent,
            style = MonoMetricSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
        }
      }
      Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun HistoryMetricPill(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape = RoundedCornerShape(10.dp),
    modifier = modifier.border(
      width = 1.dp,
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
      shape = RoundedCornerShape(10.dp),
    ),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = value,
        style = MonoMetricSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun formatShortUtc(isoUtc: String): String {
  if (isoUtc.length >= 16) {
    return "${isoUtc.substring(0, 10)} ${isoUtc.substring(11, 16)} UTC"
  }
  return isoUtc
}
