package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
  primary = FloodlightEmerald,
  onPrimary = OnFloodlightEmerald,
  primaryContainer = FloodlightEmeraldDim,
  onPrimaryContainer = FloodlightEmerald,
  secondary = TacticalCyan,
  onSecondary = OnTacticalCyan,
  secondaryContainer = TacticalCyanDim,
  onSecondaryContainer = TacticalCyan,
  tertiary = SignalAmber,
  onTertiary = OnSignalAmber,
  tertiaryContainer = SignalAmberDim,
  onTertiaryContainer = SignalAmber,
  error = AlertCoral,
  onError = Color.White,
  errorContainer = AlertCoralDim,
  onErrorContainer = AlertCoral,
  background = PitchObsidian,
  onBackground = TextOffWhite,
  surface = PitchSurface,
  onSurface = TextOffWhite,
  surfaceVariant = PitchSurfaceElevated,
  onSurfaceVariant = TextMutedSlate,
  surfaceContainerLow = PitchSurface,
  surfaceContainer = PitchSurfaceElevated,
  surfaceContainerHigh = PitchSurfaceHighest,
  outline = PitchOutline,
  outlineVariant = PitchOutline.copy(alpha = 0.55f),
)

private val LightColorScheme = lightColorScheme(
  primary = DeepTurfEmerald,
  onPrimary = Color.White,
  primaryContainer = DeepTurfContainer,
  onPrimaryContainer = Color(0xFF022C22),
  secondary = TacticalBlueLight,
  onSecondary = Color.White,
  secondaryContainer = TacticalBlueContainer,
  onSecondaryContainer = Color(0xFF0C4A6E),
  tertiary = AmberLight,
  onTertiary = Color.White,
  tertiaryContainer = AmberContainerLight,
  onTertiaryContainer = Color(0xFF451A03),
  error = Color(0xFFDC2626),
  onError = Color.White,
  errorContainer = Color(0xFFFEE2E2),
  onErrorContainer = Color(0xFF7F1D1D),
  background = ChalkBackground,
  onBackground = InkCharcoal,
  surface = ChalkSurface,
  onSurface = InkCharcoal,
  surfaceVariant = ChalkSurfaceVariant,
  onSurfaceVariant = InkMuted,
  surfaceContainerLow = ChalkSurface,
  surfaceContainer = ChalkSurfaceVariant,
  surfaceContainerHigh = Color(0xFFD9E4DF),
  outline = ChalkOutline,
  outlineVariant = ChalkOutline.copy(alpha = 0.7f),
)

val FootballIqShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun FootballIqTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = FootballIqShapes,
    content = content,
  )
}
