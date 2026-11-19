package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.BacktestSummary
import com.example.engine.MathUtils
import com.example.ui.components.EmptyDatasetCard
import com.example.ui.components.WalkForwardChart
import com.example.ui.theme.MonoMetricLarge
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import kotlin.math.roundToInt

@Composable
fun BacktestScreen(
  totalMatches: Int,
  warmupMatches: Int,
  summary: BacktestSummary?,
  isBacktesting: Boolean,
  isBusy: Boolean,
  onUpdateWarmup: (Int) -> Unit,
  onRunBacktest: () -> Unit,
  onLoadSampleSeason: () -> Unit,
  onOpenApiImport: () -> Unit,
  onOpenAddMatch: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (totalMatches < 15) {
      EmptyDatasetCard(
        onLoadSampleSeason = onLoadSampleSeason,
        onOpenApiImport = onOpenApiImport,
        onOpenAddMatch = onOpenAddMatch,
        isBusy = isBusy,
      )
      return@Column
    }

    val maxWarmup = (totalMatches - 5).coerceAtLeast(15)
    val effectiveWarmup = warmupMatches.coerceIn(10, maxWarmup)

    // Walk-Forward Control Card
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "WALK-FORWARD CALIBRATION",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = "Zero-Leakage Sequential Backtest",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = "$totalMatches total",
              style = MonoMetricSmall,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }

        Text(
          text = "Predicts each match i >= warmup using strictly earlier matches [0..i-1].",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Warmup Window",
            style = MaterialTheme.typography.labelLarge,
          )
          Text(
            text = "$effectiveWarmup matches",
            style = MonoMetricMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }

        if (maxWarmup > 10) {
          Slider(
            value = effectiveWarmup.toFloat(),
            onValueChange = { onUpdateWarmup(it.roundToInt()) },
            valueRange = 10f..maxWarmup.toFloat(),
            modifier = Modifier.fillMaxWidth().testTag("warmup_slider"),
          )
        }

        Button(
          onClick = onRunBacktest,
          enabled = !isBacktesting,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .minimumInteractiveComponentSize()
            .testTag("run_backtest_button"),
        ) {
          if (isBacktesting) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Evaluating...")
          } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Run Walk-Forward Backtest")
          }
        }
      }
    }

    if (summary == null) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.QueryStats,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
          )
          Text(
            text = "Tap Run Walk-Forward Backtest to compute out-of-sample 1X2 accuracy, Brier score, and Log Loss.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      // KPI Grid (2x2)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        KpiCard(
          label = "1X2 ACCURACY",
          value = "${MathUtils.formatFixed(summary.accuracy1x2 * 100.0, 1)}%",
          subtext = "Random baseline ~33.3%",
          accent = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        KpiCard(
          label = "MULTI-CLASS BRIER",
          value = MathUtils.formatFixed(summary.brier, 4),
          subtext = "Lower is better (<0.667)",
          accent = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.weight(1f),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        KpiCard(
          label = "LOG LOSS",
          value = MathUtils.formatFixed(summary.logLoss, 4),
          subtext = "Uniform 3-way = 1.0986",
          accent = MaterialTheme.colorScheme.tertiary,
          modifier = Modifier.weight(1f),
        )
        KpiCard(
          label = "EVALUATED (N)",
          value = "${summary.evaluatedCount}",
          subtext = "Warmup ${summary.warmupUsed} fixtures",
          accent = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
        )
      }

      // Walk-Forward Curve
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "ROLLING CALIBRATION TIMELINE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          WalkForwardChart(timeline = summary.timeline)
        }
      }

      // Calibration Split (Predicted vs Actual)
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
          .fillMaxWidth()
          .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "PREDICTED VS ACTUAL OUTCOME FREQUENCY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          CalibrationComparisonRow(
            label = "Home Win",
            predicted = summary.homeWinPredictedRate,
            actual = summary.homeWinActualRate,
            color = MaterialTheme.colorScheme.primary,
          )
          CalibrationComparisonRow(
            label = "Draw",
            predicted = summary.drawPredictedRate,
            actual = summary.drawActualRate,
            color = MaterialTheme.colorScheme.tertiary,
          )
          CalibrationComparisonRow(
            label = "Away Win",
            predicted = summary.awayWinPredictedRate,
            actual = summary.awayWinActualRate,
            color = MaterialTheme.colorScheme.secondary,
          )
        }
      }
    }
  }
}

@Composable
private fun KpiCard(
  label: String,
  value: String,
  subtext: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(16.dp),
    modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MonoMetricLarge,
        color = accent,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = subtext,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun CalibrationComparisonRow(
  label: String,
  predicted: Double,
  actual: Double,
  color: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
      Text(
        text = "Pred ${MathUtils.formatFixed(predicted * 100, 1)}% · Actual ${MathUtils.formatFixed(actual * 100, 1)}%",
        style = MonoMetricSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    LinearProgressIndicator(
      progress = { predicted.toFloat().coerceIn(0f, 1f) },
      modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
      color = color,
      trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    LinearProgressIndicator(
      progress = { actual.toFloat().coerceIn(0f, 1f) },
      modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
      color = color.copy(alpha = 0.5f),
      trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
  }
}
