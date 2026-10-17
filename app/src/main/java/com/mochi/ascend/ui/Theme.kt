package com.mochi.ascend.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mochi.ascend.data.Horizon

// Shared accents used outside the theme system (status colours, cat roles).
val Gold = Color(0xFFF2C14E)
val Green = Color(0xFF7DD3A0)
val Blue = Color(0xFF7CB7FF)
val Violet = Color(0xFFB79CFF)
val Red = Color(0xFFFF7A7A)

// ---------------------------------------------------------------------------
// Theme catalogue
// ---------------------------------------------------------------------------

/**
 * A full theme: dark and light schemes, the colour the sprite is drawn in,
 * and one colour per rung of the ladder (day, week, month, year).
 */
data class AppTheme(
    val id: String,
    val label: String,
    val glyph: String,
    val dark: ColorScheme,
    val light: ColorScheme,
    val catAccent: Color,
    val horizons: List<Color>,
)

private fun horizons(day: Color, week: Color, month: Color, year: Color) =
    listOf(day, week, month, year)

private fun build(
    id: String,
    label: String,
    glyph: String,
    accent: Color,
    catAccent: Color,
    darkBg: Color,
    darkSurface: Color,
    darkVar: Color,
    darkOn: Color,
    darkOnVar: Color,
    darkOutline: Color,
    lightAccent: Color,
    lightBg: Color,
    lightSurface: Color,
    lightVar: Color,
    lightOn: Color,
    lightOnVar: Color,
    lightOutline: Color,
    h: List<Color>,
): AppTheme = AppTheme(
    id = id,
    label = label,
    glyph = glyph,
    catAccent = catAccent,
    horizons = h,
    dark = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF120A02),
        primaryContainer = accent.copy(alpha = 0.20f),
        onPrimaryContainer = accent,
        secondary = h[0],
        onSecondary = Color(0xFF052215),
        tertiary = h[2],
        onTertiary = Color(0xFF180E2E),
        background = darkBg,
        onBackground = darkOn,
        surface = darkSurface,
        onSurface = darkOn,
        surfaceVariant = darkVar,
        onSurfaceVariant = darkOnVar,
        outline = darkOutline,
        outlineVariant = darkOutline,
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF2A0505),
    ),
    light = lightColorScheme(
        primary = lightAccent,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = lightAccent.copy(alpha = 0.16f),
        onPrimaryContainer = lightAccent,
        secondary = h[0],
        onSecondary = Color(0xFF04220F),
        tertiary = h[2],
        onTertiary = Color(0xFF180E2E),
        background = lightBg,
        onBackground = lightOn,
        surface = lightSurface,
        onSurface = lightOn,
        surfaceVariant = lightVar,
        onSurfaceVariant = lightOnVar,
        outline = lightOutline,
        outlineVariant = lightOutline,
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
    ),
)

val APP_THEMES: List<AppTheme> = listOf(
    build(
        id = "ember",
        label = "Ember",
        glyph = "\uD83D\uDD25",
        accent = Gold,
        catAccent = Gold,
        darkBg = Color(0xFF0E1116),
        darkSurface = Color(0xFF141A21),
        darkVar = Color(0xFF1C242E),
        darkOn = Color(0xFFE8EDF2),
        darkOnVar = Color(0xFF9FB0C0),
        darkOutline = Color(0xFF2C3742),
        lightAccent = Color(0xFF8A6100),
        lightBg = Color(0xFFF7F9FB),
        lightSurface = Color(0xFFFFFFFF),
        lightVar = Color(0xFFEDF1F5),
        lightOn = Color(0xFF141A21),
        lightOnVar = Color(0xFF4A5A6A),
        lightOutline = Color(0xFFD2DAE2),
        h = horizons(Green, Blue, Violet, Gold),
    ),
    build(
        id = "crimson",
        label = "Crimson",
        glyph = "\uD83E\uDDE0",
        accent = Color(0xFFE23A3A),
        catAccent = Color(0xFFE23A3A),
        darkBg = Color(0xFF0B0909),
        darkSurface = Color(0xFF161213),
        darkVar = Color(0xFF211B1C),
        darkOn = Color(0xFFF2ECEC),
        darkOnVar = Color(0xFFB0A2A3),
        darkOutline = Color(0xFF352B2D),
        lightAccent = Color(0xFFC1121F),
        lightBg = Color(0xFFFCF6F6),
        lightSurface = Color(0xFFFFFFFF),
        lightVar = Color(0xFFF6E9E9),
        lightOn = Color(0xFF1A1112),
        lightOnVar = Color(0xFF5C4748),
        lightOutline = Color(0xFFE8D3D4),
        h = horizons(Color(0xFF40C9A2), Color(0xFFFF9E1B), Color(0xFFFF5D8F), Color(0xFFE23A3A)),
    ),
    build(
        id = "ocean",
        label = "Ocean",
        glyph = "\uD83C\uDF0A",
        accent = Color(0xFF38BDF8),
        catAccent = Color(0xFF38BDF8),
        darkBg = Color(0xFF080D12),
        darkSurface = Color(0xFF0F161D),
        darkVar = Color(0xFF17202A),
        darkOn = Color(0xFFE6EFF5),
        darkOnVar = Color(0xFF93A6B8),
        darkOutline = Color(0xFF27333F),
        lightAccent = Color(0xFF0B5C8A),
        lightBg = Color(0xFFF5F9FC),
        lightSurface = Color(0xFFFFFFFF),
        lightVar = Color(0xFFE7EFF5),
        lightOn = Color(0xFF101B23),
        lightOnVar = Color(0xFF44586A),
        lightOutline = Color(0xFFCFDCE6),
        h = horizons(Color(0xFF34D399), Color(0xFF60A5FA), Color(0xFF818CF8), Color(0xFF38BDF8)),
    ),
    build(
        id = "forest",
        label = "Forest",
        glyph = "\uD83C\uDF3F",
        accent = Color(0xFF3E9B5F),
        catAccent = Color(0xFF7DD37A),
        darkBg = Color(0xFF09100C),
        darkSurface = Color(0xFF0F1A13),
        darkVar = Color(0xFF16241B),
        darkOn = Color(0xFFE6F0E9),
        darkOnVar = Color(0xFF93A89A),
        darkOutline = Color(0xFF25352B),
        lightAccent = Color(0xFF1F6B2E),
        lightBg = Color(0xFFF5FAF6),
        lightSurface = Color(0xFFFFFFFF),
        lightVar = Color(0xFFE6F1E9),
        lightOn = Color(0xFF101A13),
        lightOnVar = Color(0xFF44574A),
        lightOutline = Color(0xFFCEDDD2),
        h = horizons(Color(0xFF86E39B), Color(0xFFB8C86B), Color(0xFF6FA88C), Color(0xFF3E9B5F)),
    ),
    build(
        id = "violet",
        label = "Violet",
        glyph = "\uD83D\uDD16",
        accent = Color(0xFFA78BFA),
        catAccent = Color(0xFFB79CFF),
        darkBg = Color(0xFF0C0910),
        darkSurface = Color(0xFF151021),
        darkVar = Color(0xFF1E1729),
        darkOn = Color(0xFFEFE9F7),
        darkOnVar = Color(0xFFA79BBF),
        darkOutline = Color(0xFF2E2540),
        lightAccent = Color(0xFF5B34C4),
        lightBg = Color(0xFFF8F5FD),
        lightSurface = Color(0xFFFFFFFF),
        lightVar = Color(0xFFEDE6F8),
        lightOn = Color(0xFF16101F),
        lightOnVar = Color(0xFF524468),
        lightOutline = Color(0xFFDDD2EE),
        h = horizons(Color(0xFF22D3EE), Color(0xFF818CF8), Color(0xFFC084FC), Color(0xFFA78BFA)),
    ),
    build(
        id = "slate",
        label = "Slate",
        glyph = "\uD83E\uDDE8",
        accent = Color(0xFF9AA7B4),
        catAccent = Color(0xFFC3CDD8),
        darkBg = Color(0xFF0C0E10),
        darkSurface = Color(0xFF14171A),
        darkVar = Color(0xFF1D2126),
        darkOn = Color(0xFFE9EDF0),
        darkOnVar = Color(0xFF98A3AC),
        darkOutline = Color(0xFF2A3036),
        lightAccent = Color(0xFF33414F),
        lightBg = Color(0xFFF7F8F9),
        lightSurface = Color(0xFFFFFFFF),
        lightVar = Color(0xFFEDF0F2),
        lightOn = Color(0xFF16191C),
        lightOnVar = Color(0xFF4C565F),
        lightOutline = Color(0xFFD5DBE0),
        h = horizons(Color(0xFF8FA3B5), Color(0xFFA7B4C2), Color(0xFFC0CBD6), Color(0xFFDDE5EC)),
    ),
)

fun themeById(id: String): AppTheme = APP_THEMES.firstOrNull { it.id == id } ?: APP_THEMES.first()

// ---------------------------------------------------------------------------
// Composition locals
// ---------------------------------------------------------------------------

private val DefaultHorizons = horizons(Green, Blue, Violet, Gold)

val LocalHorizonColors = staticCompositionLocalOf { DefaultHorizons }
val LocalCatAccent = staticCompositionLocalOf { Gold }

/** Per-rung colour for the current theme. */
@Composable
fun horizonColor(h: Horizon): Color = LocalHorizonColors.current[h.ordinal]

/** The colour the sprite is drawn in. */
@Composable
fun catAccent(): Color = LocalCatAccent.current

@Composable
fun AscendTheme(
    themeId: String = "ember",
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val theme = themeById(themeId)
    CompositionLocalProvider(
        LocalHorizonColors provides theme.horizons,
        LocalCatAccent provides theme.catAccent,
    ) {
        MaterialTheme(
            colorScheme = if (dark) theme.dark else theme.light,
            content = content,
        )
    }
}