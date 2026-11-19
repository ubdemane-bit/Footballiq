package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.BacktestPoint
import com.example.engine.ConfidenceLevel
import com.example.engine.MathUtils
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall

@Composable
fun ConfidenceBadge(level: ConfidenceLevel, modifier: Modifier = Modifier) {
  val (bg, fg) = when (level) {
    ConfidenceLevel.HIGH -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    ConfidenceLevel.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
    ConfidenceLevel.LOW -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
  }
  Surface(
    color = bg,
    shape = RoundedCornerShape(8.dp),
    modifier = modifier,
  ) {
    Text(
      text = level.label.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = fg,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun FormPillsRow(form: List<String>, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    form.forEach { res ->
      val (bg, fg) = when (res) {
        "W" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        "D" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
      }
      Box(
        modifier = Modifier
          .size(20.dp)
          .clip(CircleShape)
          .background(bg),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = res,
          style = MaterialTheme.typography.labelSmall,
          color = fg,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
fun ProbabilitySegmentedBar(
  probHome: Double,
  probDraw: Double,
  probAway: Double,
  homeName: String,
  awayName: String,
  modifier: Modifier = Modifier,
) {
  val total = (probHome + probDraw + probAway).coerceAtLeast(0.0001)
  val wHome by animateFloatAsState(
    targetValue = (probHome / total).toFloat().coerceIn(0.04f, 0.92f),
    animationSpec = spring(),
    label = "homeProb",
  )
  val wDraw by animateFloatAsState(
    targetValue = (probDraw / total).toFloat().coerceIn(0.04f, 0.92f),
    animationSpec = spring(),
    label = "drawProb",
  )
  val wAway by animateFloatAsState(
    targetValue = (probAway / total).toFloat().coerceIn(0.04f, 0.92f),
    animationSpec = spring(),
    label = "awayProb",
  )

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(14.dp)
        .clip(RoundedCornerShape(8.dp)),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Box(
        modifier = Modifier
          .weight(wHome)
          .height(14.dp)
          .background(MaterialTheme.colorScheme.primary)
      )
      Box(
        modifier = Modifier
          .weight(wDraw)
          .height(14.dp)
          .background(MaterialTheme.colorScheme.tertiary)
      )
      Box(
        modifier = Modifier
          .weight(wAway)
          .height(14.dp)
          .background(MaterialTheme.colorScheme.secondary)
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = homeName,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${MathUtils.formatFixed(probHome * 100.0, 1)}%",
          style = MonoMetricMedium,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Column(
        modifier = Modifier.weight(0.8f),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "DRAW",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = "${MathUtils.formatFixed(probDraw * 100.0, 1)}%",
          style = MonoMetricMedium,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.End,
      ) {
        Text(
          text = awayName,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${MathUtils.formatFixed(probAway * 100.0, 1)}%",
          style = MonoMetricMedium,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
    }
  }
}

@Composable
fun PoissonHeatmapGrid(
  matrix: List<List<Double>>,
  homeName: String,
  awayName: String,
  modalScore: String,
  modifier: Modifier = Modifier,
) {
  val displayN = minOf(6, matrix.size) // 0..5 goals grid for clean mobile readability
  val maxCellProb = remember(matrix) {
    matrix.take(displayN).maxOfOrNull { row -> row.take(displayN).maxOrNull() ?: 0.0 }?.coerceAtLeast(0.01) ?: 0.15
  }
  var inspectedCell by remember(matrix) { mutableStateOf<Pair<Int, Int>?>(null) }

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "POISSON SCORE MATRIX (0–5 GOALS)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      val activeCell = inspectedCell ?: modalScore.split("-").let {
        val h = it.getOrNull(0)?.toIntOrNull() ?: 0
        val a = it.getOrNull(1)?.toIntOrNull() ?: 0
        h to a
      }
      val activeProb = matrix.getOrNull(activeCell.first)?.getOrNull(activeCell.second) ?: 0.0
      Text(
        text = "Score ${activeCell.first}-${activeCell.second}: ${MathUtils.formatFixed(activeProb * 100, 2)}%",
        style = MonoMetricSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
      )
    }

    Text(
      text = "Rows: $homeName (Home) · Cols: $awayName (Away)",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Header row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Box(modifier = Modifier.width(24.dp))
      for (j in 0 until displayN) {
        Text(
          text = "$j",
          style = MonoMetricSmall,
          color = MaterialTheme.colorScheme.secondary,
          textAlign = TextAlign.Center,
          modifier = Modifier.weight(1f),
        )
      }
    }

    for (i in 0 until displayN) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "$i",
          style = MonoMetricSmall,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center,
          modifier = Modifier.width(24.dp),
        )
        for (j in 0 until displayN) {
          val p = matrix[i][j]
          val norm = (p / maxCellProb).toFloat().coerceIn(0.05f, 1f)
          val isModal = "$i-$j" == modalScore
          val isSelected = inspectedCell == (i to j)
          val cellBg = when {
            isModal -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            i > j -> MaterialTheme.colorScheme.primary.copy(alpha = norm * 0.28f)
            i == j -> MaterialTheme.colorScheme.tertiary.copy(alpha = norm * 0.28f)
            else -> MaterialTheme.colorScheme.secondary.copy(alpha = norm * 0.28f)
          }
          val borderColor = when {
            isSelected -> MaterialTheme.colorScheme.tertiary
            isModal -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
          }
          Box(
            modifier = Modifier
              .weight(1f)
              .height(38.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(cellBg)
              .border(
                width = if (isModal || isSelected) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp),
              )
              .clickable { inspectedCell = i to j },
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = MathUtils.formatFixed(p * 100.0, 1),
              style = MonoMetricSmall,
              fontWeight = if (isModal) FontWeight.Bold else FontWeight.Normal,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}

@Composable
fun WalkForwardChart(
  timeline: List<BacktestPoint>,
  modifier: Modifier = Modifier,
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val gridColor = MaterialTheme.colorScheme.outlineVariant

  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LegendDot(color = primaryColor, label = "1X2 Accuracy")
      LegendDot(color = secondaryColor, label = "Brier Score (lower=better)")
    }

    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(170.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .padding(12.dp),
    ) {
      if (timeline.size < 2) return@Canvas
      val w = size.width
      val h = size.height

      // Reference horizontal grid lines at 0.25, 0.50, 0.75
      listOf(0.25f, 0.50f, 0.75f).forEach { level ->
        val y = h * (1f - level)
        drawLine(
          color = gridColor,
          start = Offset(0f, y),
          end = Offset(w, y),
          strokeWidth = 1.dp.toPx(),
        )
      }

      val accPath = Path()
      val brierPath = Path()
      val count = timeline.size - 1

      timeline.forEachIndexed { idx, pt ->
        val x = (idx.toFloat() / count) * w
        val accY = h * (1f - pt.cumulativeAccuracy.toFloat().coerceIn(0f, 1f))
        val brierY = h * (1f - pt.cumulativeBrier.toFloat().coerceIn(0f, 1f))
        if (idx == 0) {
          accPath.moveTo(x, accY)
          brierPath.moveTo(x, brierY)
        } else {
          accPath.lineTo(x, accY)
          brierPath.lineTo(x, brierY)
        }
      }

      drawPath(
        path = accPath,
        color = primaryColor,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
      )
      drawPath(
        path = brierPath,
        color = secondaryColor,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
      )
    }
  }
}

@Composable
private fun LegendDot(color: Color, label: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(color),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
fun EmptyDatasetCard(
  onLoadSampleSeason: () -> Unit,
  onOpenApiImport: () -> Unit,
  onOpenAddMatch: () -> Unit,
  isBusy: Boolean,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(20.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .size(64.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Filled.SportsSoccer,
          contentDescription = "Empty match dataset",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(32.dp),
        )
      }
      Text(
        text = "No Match History Loaded",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      Text(
        text = "Plug a Football API, paste match JSON, log scores manually, or load the 132-match deterministic season to calibrate Elo and Poisson matrices.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Button(
        onClick = onLoadSampleSeason,
        enabled = !isBusy,
        modifier = Modifier
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .testTag("empty_load_sample_button"),
      ) {
        Icon(Icons.Filled.Dataset, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.load_sample_season))
      }
      OutlinedButton(
        onClick = onOpenApiImport,
        enabled = !isBusy,
        modifier = Modifier
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .testTag("empty_plug_api_button"),
      ) {
        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.import_api_data))
      }
      OutlinedButton(
        onClick = onOpenAddMatch,
        enabled = !isBusy,
        modifier = Modifier
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .testTag("empty_add_match_button"),
      ) {
        Text(stringResource(R.string.add_match))
      }
    }
  }
}
