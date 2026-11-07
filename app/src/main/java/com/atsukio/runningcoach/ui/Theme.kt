package com.atsukio.runningcoach.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Which colours the app uses. Dark is the default: it suits the "lab" look and a gym or a treadmill room. */
enum class Appearance(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System"),
}

/** The chosen appearance, observed by [CoachTheme] so a change in Settings applies at once. */
object AppearanceState {
    var current by mutableStateOf(Appearance.DARK)
}

/** Accent colours with a fixed meaning, used for ranges, status and live readouts. */
object Signal {
    val Good = Color(0xFF5CE1B0)
    val Improve = Color(0xFFFFB347)
    val Info = Color(0xFF6EC8E8)
    val GoodLight = Color(0xFF0B7A5A)
    val ImproveLight = Color(0xFFB36B00)
    val InfoLight = Color(0xFF00698C)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5CE1B0),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0F4D3B),
    onPrimaryContainer = Color(0xFFB5F5DC),
    secondary = Color(0xFF6EC8E8),
    onSecondary = Color(0xFF00364A),
    secondaryContainer = Color(0xFF123F52),
    onSecondaryContainer = Color(0xFFC3E9F7),
    tertiary = Color(0xFFFFB347),
    onTertiary = Color(0xFF442B00),
    background = Color(0xFF0B1210),
    onBackground = Color(0xFFE6EFEA),
    surface = Color(0xFF0B1210),
    onSurface = Color(0xFFE6EFEA),
    surfaceVariant = Color(0xFF1B2D26),
    onSurfaceVariant = Color(0xFF93A8A0),
    surfaceContainerLowest = Color(0xFF080E0C),
    surfaceContainerLow = Color(0xFF111B17),
    surfaceContainer = Color(0xFF14211C),
    surfaceContainerHigh = Color(0xFF182822),
    surfaceContainerHighest = Color(0xFF1B2D26),
    outline = Color(0xFF3A4F46),
    outlineVariant = Color(0xFF24362F),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B7A5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F2E0),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF00698C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC3E9F7),
    onSecondaryContainer = Color(0xFF00364A),
    tertiary = Color(0xFFB36B00),
    background = Color(0xFFF3F7F5),
    onBackground = Color(0xFF0F1A16),
    surface = Color(0xFFF3F7F5),
    onSurface = Color(0xFF0F1A16),
    surfaceVariant = Color(0xFFE2ECE7),
    onSurfaceVariant = Color(0xFF53655D),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF7FAF8),
    surfaceContainerHighest = Color.White,
    outline = Color(0xFF8AA096),
    outlineVariant = Color(0xFFD3E0D9),
    error = Color(0xFFB3261E),
)

/** Numbers, timers and tags are set in a monospace face; everything else stays in the clean system sans. */
val Mono: FontFamily = FontFamily.Monospace

private val base = Typography()
private val LabTypography = Typography(
    displayMedium = base.displayMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    // Small tags such as "MAIN FOCUS".
    labelMedium = base.labelMedium.copy(fontFamily = Mono, letterSpacing = 0.8.sp),
    labelSmall = base.labelSmall.copy(fontFamily = Mono, letterSpacing = 1.2.sp),
)

private val LabShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Whether the dark colours are in use, from the chosen [Appearance]. */
@Composable
fun isDarkAppearance(): Boolean = when (AppearanceState.current) {
    Appearance.DARK -> true
    Appearance.LIGHT -> false
    Appearance.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    val dark = isDarkAppearance()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = LabTypography,
        shapes = LabShapes,
        content = content,
    )
}

/** The status colour for the current theme: green in range, amber to work on, blue for notes. */
@Composable
fun signalColour(good: Boolean?): Color {
    val dark = MaterialTheme.colorScheme.background.red < 0.5f
    return when (good) {
        null -> if (dark) Signal.Info else Signal.InfoLight
        true -> if (dark) Signal.Good else Signal.GoodLight
        false -> if (dark) Signal.Improve else Signal.ImproveLight
    }
}

/** A flat panel with a thin outline, the one container used across the app. Clickable when [onClick] is set. */
@Composable
fun LabCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    outlined: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = colors, border = border, elevation = elevation, content = content)
    } else {
        Card(modifier = modifier, colors = colors, border = border, elevation = elevation, content = content)
    }
}

/** Text style for a figure such as "172" or a timer: bold monospace at [size] sp. */
fun monoValue(size: Int = 20): TextStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = size.sp)
