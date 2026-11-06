package com.studioguu.miniaturegarden.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp

/** The game's palette: carved wood for controls, parchment for panels, dark ink for text. */
object GardenColors {
    val Wood = Color(0xFF8D5A2B)
    val WoodDark = Color(0xFF5D3A1A)
    val WoodLight = Color(0xFFD9B382)
    val Parchment = Color(0xFFF6E9CC)
    val ParchmentDark = Color(0xFFE8D5A9)
    val Cream = Color(0xFFFFF3D6)
    val Ink = Color(0xFF3E2723)
    val InkSoft = Color(0xFF6D4C41)
    val Leaf = Color(0xFF5E8C3A)

    /** A link in a sheet of text: a deep blue that reads on parchment. */
    val Link = Color(0xFF1E5A8C)
    val Gold = Color(0xFFFFD54F)

    /** The pale, worn parchment of dialogs and the panels inside them. */
    val ParchmentPale = Color(0xFFF4EEDC)

    /** A switch that is on, a slider's filled part: a muted olive, like a dyed leather strap. */
    val Olive = Color(0xFF6F8A3A)
    val OliveDark = Color(0xFF4F6427)

    /** The grain drawn across a plank, a shade darker than [WoodLight]. */
    val WoodGrain = Color(0xFFC79E6B)
}

private val GardenColorScheme = lightColorScheme(
    primary = GardenColors.Wood,
    onPrimary = GardenColors.Cream,
    primaryContainer = GardenColors.WoodLight,
    onPrimaryContainer = GardenColors.Ink,
    secondary = GardenColors.Leaf,
    onSecondary = GardenColors.Cream,
    secondaryContainer = GardenColors.WoodLight,
    onSecondaryContainer = GardenColors.Ink,
    tertiary = GardenColors.Leaf,
    background = GardenColors.Parchment,
    onBackground = GardenColors.Ink,
    surface = GardenColors.Parchment,
    onSurface = GardenColors.Ink,
    surfaceVariant = GardenColors.ParchmentDark,
    onSurfaceVariant = GardenColors.InkSoft,
    surfaceTint = GardenColors.Wood,
    // Dialogs, sheets and menus take their background from these.
    surfaceContainerLowest = GardenColors.Cream,
    surfaceContainerLow = GardenColors.Parchment,
    surfaceContainer = GardenColors.Parchment,
    surfaceContainerHigh = GardenColors.Parchment,
    surfaceContainerHighest = GardenColors.ParchmentDark,
    outline = GardenColors.InkSoft,
    outlineVariant = GardenColors.WoodLight,
    inverseSurface = GardenColors.Ink,
    inverseOnSurface = GardenColors.Cream,
)

// Squarer corners than the Material default, closer to a carved sign than to a pill.
private val GardenShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * How every text in the game wraps: at the seams between phrases (Android 13 and up; the text has to
 * say it is Japanese for that, whatever language the device is set to), with its lines kept
 * about as long as one another, so that a line is not left with a character or two on it.
 */
private val PhraseLineBreak = LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Strict, LineBreak.WordBreak.Phrase)
private val Japanese = LocaleList("ja")

private fun TextStyle.inPhrases() = copy(lineBreak = PhraseLineBreak, localeList = Japanese)

internal val GardenTypography = Typography().run {
    copy(
        displayLarge = displayLarge.inPhrases(),
        displayMedium = displayMedium.inPhrases(),
        displaySmall = displaySmall.inPhrases(),
        headlineLarge = headlineLarge.inPhrases(),
        headlineMedium = headlineMedium.inPhrases(),
        headlineSmall = headlineSmall.inPhrases(),
        titleLarge = titleLarge.inPhrases(),
        titleMedium = titleMedium.inPhrases(),
        titleSmall = titleSmall.inPhrases(),
        bodyLarge = bodyLarge.inPhrases(),
        bodyMedium = bodyMedium.inPhrases(),
        bodySmall = bodySmall.inPhrases(),
        labelLarge = labelLarge.inPhrases(),
        labelMedium = labelMedium.inPhrases(),
        labelSmall = labelSmall.inPhrases(),
    )
}

@Composable
fun GardenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GardenColorScheme, shapes = GardenShapes, typography = GardenTypography, content = content)
}
