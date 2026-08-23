package com.example.rhythmbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RhythmColors = darkColorScheme(
    primary = Amber,
    onPrimary = OnAmber,
    secondary = Teal,
    onSecondary = OnAmber,
    tertiary = AmberBright,
    background = Panel,
    onBackground = TextPrimary,
    surface = Panel,
    onSurface = TextPrimary,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = TextMuted,
    surfaceContainer = PanelHigh,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerLow = PanelLow,
    outline = StepAccent,
)

/** 常にダークの固定配色（機材らしさを優先し、端末のダイナミックカラーは使わない）。 */
@Composable
fun RhythmBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RhythmColors,
        typography = Typography,
        content = content,
    )
}
