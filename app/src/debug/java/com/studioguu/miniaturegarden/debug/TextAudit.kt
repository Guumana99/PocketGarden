package com.studioguu.miniaturegarden.debug

import android.content.Context
import android.graphics.text.LineBreakConfig
import android.icu.util.ULocale
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.studioguu.miniaturegarden.R
import java.io.File
import java.util.Locale

/**
 * Wraps every string of the app the way the game's theme does (debug builds only), at each width a
 * text could have from a phone 360 dp across up, and at larger type, and writes down
 * where a last line comes out with only a character or two on it (`--ez audit_text true`,
 * [ShowcaseReceiver]; the result is `files/text_audit.tsv`, pulled with `run-as`).
 *
 * It wraps in three ways: in phrases, as on Android 13 and up ("phrase"), with only the lines evened
 * out, as on anything older ("old"), and as the game did before it set any of this ("plain"), which
 * shows that the check does find what it looks for. Each row: string, way, type size, width, the
 * short last line. Under "#wrap" it also writes out how some strings wrap, one row a width.
 */
object TextAudit {
    private val TYPE_SP = listOf(12f, 14f, 16f)
    private val SCALES = listOf(1f, 1.3f)
    private val WIDTHS_DP = (220..400 step 4).toList()
    private val WAYS = listOf("phrase", "old", "plain")
    private val SAMPLES = setOf("reward_rain_confirm", "tutorial_grow_text", "tutorial_thanks_text", "letter_tip_compost_body", "hint_withered")

    /** What a format's placeholders are filled in with: names and numbers of a usual length. */
    private fun filled(text: String): String = text
        .replace(Regex("""%\d\${'$'}s"""), "ミント")
        .replace(Regex("""%\d\${'$'}d"""), "12")
        .replace("%%", "%")

    fun run(context: Context): File {
        val density = context.resources.displayMetrics.density
        val rows = StringBuilder()
        val samples = StringBuilder("#wrap\n")
        var strings = 0
        for (field in R.string::class.java.fields) {
            val text = runCatching { context.getString(field.getInt(null)) }.getOrNull() ?: continue
            if (text.none { it.code in 0x3040..0x9FFF }) continue
            strings++
            val full = filled(text)
            if (field.name in SAMPLES) {
                for (dp in listOf(240, 280, 320)) for (way in WAYS) {
                    val layout = laidOut(full, way, 14f * density, (dp * density).toInt())
                    samples.append(field.name).append('\t').append(way).append('\t').append(dp).append('\t')
                        .append((0 until layout.lineCount).joinToString(" / ") { full.substring(layout.getLineStart(it), layout.getLineEnd(it)).trim() }).append('\n')
                }
            }
            for (way in WAYS) for (sp in TYPE_SP) for (scale in SCALES) for (dp in WIDTHS_DP) {
                val layout = laidOut(full, way, sp * scale * density, (dp * density).toInt())
                for (line in 1 until layout.lineCount) {
                    val start = layout.getLineStart(line)
                    val end = layout.getLineEnd(line)
                    val shown = full.substring(start, end).trimEnd('\n')
                    val endsParagraph = line == layout.lineCount - 1 || full.getOrNull(end - 1) == '\n'
                    val continues = full.getOrNull(start - 1) != '\n'
                    if (endsParagraph && continues && shown.count { it !in "。、！？」）" } <= 2) {
                        rows.append(field.name).append('\t').append(way).append('\t')
                            .append(sp).append('x').append(scale).append('\t').append(dp).append('\t').append(shown).append('\n')
                    }
                }
            }
        }
        val out = File(context.filesDir, "text_audit.tsv")
        out.writeText("# $strings strings, sdk ${Build.VERSION.SDK_INT}, ${ULocale.getDefault()}\n$rows$samples")
        return out
    }

    private fun laidOut(text: String, way: String, size: Float, width: Int): StaticLayout {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            if (way != "plain") textLocale = Locale.JAPANESE
        }
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        if (way == "plain") return builder.build()
        builder.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
        if (Build.VERSION.SDK_INT >= 33) {
            builder.setLineBreakConfig(
                LineBreakConfig.Builder()
                    .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_STRICT)
                    .setLineBreakWordStyle(if (way == "phrase") LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE else LineBreakConfig.LINE_BREAK_WORD_STYLE_NONE)
                    .build(),
            )
        }
        return builder.build()
    }
}
