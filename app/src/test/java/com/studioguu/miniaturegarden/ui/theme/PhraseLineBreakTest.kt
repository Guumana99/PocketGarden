package com.studioguu.miniaturegarden.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every text style of the game wraps at the seams between Japanese phrases (device feedback of 11/1). */
class PhraseLineBreakTest {
    private val styles: List<TextStyle> = with(GardenTypography) {
        listOf(
            displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
            titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall,
        )
    }

    @Test
    fun `no style is left wrapping in the middle of a phrase`() {
        for (style in styles) {
            assertEquals(LineBreak.WordBreak.Phrase, style.lineBreak.wordBreak)
            assertEquals(LineBreak.Strategy.Balanced, style.lineBreak.strategy)
            assertEquals(LineBreak.Strictness.Strict, style.lineBreak.strictness)
        }
    }

    @Test
    fun `the text says it is Japanese, whatever the device is set to`() {
        for (style in styles) assertEquals(LocaleList("ja"), style.localeList)
    }
}
