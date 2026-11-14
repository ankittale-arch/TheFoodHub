package com.ankitt.thefoodhub.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours of the home design and the floating bottom bar. The light set is sampled from the
 * mockup; the dark set keeps the same slate-blue and olive character for system night mode.
 * [TheFoodHubTheme] provides the set matching the system theme; read it through [HomePalette].
 */
@Immutable
class HomeColors(
    val BackgroundTop: Color,
    val BackgroundBottom: Color,
    /** Primary text and icons, and the fill of selected chips and buttons. */
    val Ink: Color,
    val InkMuted: Color,
    /** Text and icons drawn on an [Ink] fill. */
    val OnInk: Color,
    val Field: Color,
    val FieldButton: Color,
    val Chip: Color,
    val Olive: Color,
    /** Text and icons drawn on an [Olive] fill. */
    val OnOlive: Color,
    /** Raised surfaces over the recipe photo: the title card and the likes pill. */
    val Card: Color,
    val Pill: Color,
    /** Frame around each carousel slide. */
    val SlideBorder: Color,
    val Bar: Color,
    val BarIconBackground: Color,
    val BarIcon: Color,
)

internal val LightHomeColors = HomeColors(
    BackgroundTop = Color(0xFFDDE3E8),
    BackgroundBottom = Color(0xFFB9CBD8),
    Ink = Color(0xFF111111),
    InkMuted = Color(0xFF6C7075),
    OnInk = Color.White,
    Field = Color(0xFFEDF0F3),
    FieldButton = Color(0xFFDCE2E8),
    Chip = Color(0xFFDCE3E9),
    Olive = Color(0xFF6E8360),
    OnOlive = Color.White,
    Card = Color.White,
    Pill = Color(0xFFF2F3F4),
    SlideBorder = Color.White.copy(alpha = 0.9f),
    Bar = Color(0xFF111111),
    BarIconBackground = Color.White,
    BarIcon = Color(0xFF111111),
)

internal val DarkHomeColors = HomeColors(
    BackgroundTop = Color(0xFF1C242B),
    BackgroundBottom = Color(0xFF0E1419),
    Ink = Color(0xFFECEFF2),
    InkMuted = Color(0xFF9AA3AB),
    OnInk = Color(0xFF111111),
    Field = Color(0xFF263038),
    FieldButton = Color(0xFF334049),
    Chip = Color(0xFF263038),
    Olive = Color(0xFF6E8360),
    OnOlive = Color.White,
    Card = Color(0xFF1E262D),
    Pill = Color(0xFF2B353D),
    SlideBorder = Color.White.copy(alpha = 0.12f),
    Bar = Color(0xFF2B353D),
    BarIconBackground = Color(0xFFE6EAEE),
    BarIcon = Color(0xFF111111),
)

internal val LocalHomeColors = staticCompositionLocalOf { LightHomeColors }

/** The home colours for the current theme (light or dark, following the system). */
val HomePalette: HomeColors
    @Composable
    @ReadOnlyComposable
    get() = LocalHomeColors.current
