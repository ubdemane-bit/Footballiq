package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.MatchInput
import com.example.engine.MathUtils
import com.example.engine.RecentMatchDetail
import com.example.engine.TeamRecentAnalysis
import com.example.ui.theme.MonoMetricSmall

@Composable
fun RecentSeasonAnalysisCard(
  seasonLabel: String,
  windowGames: Int,
  homeAnalysis: TeamRecentAnalysis?,
  awayAnalysis: TeamRecentAnalysis?,
  headToHeadMatches: List<MatchInput>,
  modifier: Modifier = Modifier,
) {
  if (homeAnalysis == null || awayAnalysis == null) return

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(18.dp),
    modifier = modifier
      .fillMaxWidth()
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "${seasonLabel.uppercase()} · RECENT GAMES BREAKDOWN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Input window: up to last $windowGames games per club",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      TeamRecentBlock(
        analysis = homeAnalysis,
        roleTag = "HOME",
        accentPrimary = true,
      )

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

      TeamRecentBlock(
        analysis = awayAnalysis,
        roleTag = "AWAY",
        accentPrimary = false,
      )

      if (headToHeadMatches.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "HEAD-TO-HEAD ($seasonLabel)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
          )
          headToHeadMatches.forEach { m ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 10.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = m.date,
                style = MonoMetricSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "${m.home} ${m.hg} - ${m.ag} ${m.away}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TeamRecentBlock(
  analysis: TeamRecentAnalysis,
  roleTag: String,
  accentPrimary: Boolean,
) {
  val roleColor = if (accentPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f),
      ) {
        Surface(
          color = roleColor.copy(alpha = 0.16f),
          shape = RoundedCornerShape(6.dp),
        ) {
          Text(
            text = roleTag,
            style = MaterialTheme.typography.labelSmall,
            color = roleColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
        Text(
          text = analysis.team,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Text(
        text = "${analysis.wins}W-${analysis.draws}D-${analysis.losses}L · ${MathUtils.formatFixed(analysis.pointsPerGame, 2)} PPG",
        style = MonoMetricSmall,
        color = roleColor,
        fontWeight = FontWeight.SemiBold,
      )
    }

    Text(
      text = "Last ${analysis.recentGames.size} games: ${analysis.goalsFor} GF (${MathUtils.formatFixed(analysis.goalsForPerGame, 2)}/g) · ${analysis.goalsAgainst} GA (${MathUtils.formatFixed(analysis.goalsAgainstPerGame, 2)}/g)",
      style = MonoMetricSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (analysis.recentGames.isEmpty()) {
      Text(
        text = "No games recorded in this season window.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        analysis.recentGames.take(6).forEach { game ->
          RecentMatchRow(game = game)
        }
      }
    }
  }
}

@Composable
private fun RecentMatchRow(game: RecentMatchDetail) {
  val (pillBg, pillFg) = when (game.result) {
    "W" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    "D" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .padding(horizontal = 10.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.weight(1f),
    ) {
      Text(
        text = game.date.takeLast(5), // MM-DD for compact mobile readability
        style = MonoMetricSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = if (game.isHome) "H" else "A",
        style = MonoMetricSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = game.opponent,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = "${game.goalsFor} - ${game.goalsAgainst}",
        style = MonoMetricSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
      )
      Box(
        modifier = Modifier
          .size(20.dp)
          .clip(CircleShape)
          .background(pillBg),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = game.result,
          style = MaterialTheme.typography.labelSmall,
          color = pillFg,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}
