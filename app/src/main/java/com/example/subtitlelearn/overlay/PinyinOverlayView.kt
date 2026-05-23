// PinyinOverlayView.kt
package com.example.subtitlelearn.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.subtitlelearn.Dictionary

class PinyinOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Paints ────────────────────────────────────────────────────────────────
    private val charPaint      = paint(Color.WHITE,                    50f)
    private val pinyinPaint    = paint(Color.YELLOW,                   35f)
    private val breakdownPaint = paint(Color.argb(210, 160, 210, 255), 26f) // NEW – soft blue
    private val boxFillPaint   = paint(Color.argb(180, 0, 0, 0)).apply { style = Paint.Style.FILL }
    private val boxStrokePaint = paint(Color.argb(90, 255, 255, 255)).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }

    private fun paint(color: Int, textSize: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; if (textSize > 0) this.textSize = textSize
        }

    // ── Layout constants ──────────────────────────────────────────────────────
    private val boxRadius = 18f
    private val boxPad    = 12f
    private val gap       = 20f   // between word blocks
    private val sp        = 12f   // internal spacing

    // ── Pre-computed layout ───────────────────────────────────────────────────
    private data class CharCell(val pinyin: String, val char: String, val colWidth: Float)
    private data class WordCell(
        val cells:          List<CharCell>,
        val meaning:        String,
        val charBreakdown:  String,   // e.g. "你·you  好·good" for multi-char words
        val blockWidth:     Float
    )

    private var layout: List<List<WordCell>> = emptyList()  // lines → words

    // ── Public API ────────────────────────────────────────────────────────────
    fun setText(words: List<String>, meanings: Map<String, String>) {
        val wordCells = words.map { word ->
            val pinyinTokens = Dictionary.getPinyin(word).split(" ")
            val cells = word.mapIndexed { i, ch ->
                val py   = pinyinTokens.getOrElse(i) { "" }
                val colW = maxOf(pinyinPaint.measureText(py), charPaint.measureText(ch.toString()))
                CharCell(py, ch.toString(), colW)
            }
            val wordW    = cells.sumOf { it.colWidth.toDouble() }.toFloat() + sp * cells.size
            val meaning  = meanings[word].orEmpty()
            val meaningW = if (meaning.isNotEmpty()) charPaint.measureText(meaning) + sp else 0f

            // Individual character breakdown – only for multi-char words
            val charBreakdown = if (word.length > 1) {
                word.map { ch ->
                    val m = Dictionary.getMeaning(ch.toString())
                    if (m.isNotEmpty()) "$ch·$m" else ch.toString()
                }.joinToString("  ")
            } else ""
            val breakdownW = if (charBreakdown.isNotEmpty())
                breakdownPaint.measureText(charBreakdown) + sp else 0f

            WordCell(cells, meaning, charBreakdown, maxOf(wordW, meaningW, breakdownW))
        }

        // Wrap into lines
        val maxW = (width - paddingLeft - paddingRight).toFloat().takeIf { it > 0 } ?: 1080f
        val lines = mutableListOf<MutableList<WordCell>>()
        var currentLine = mutableListOf<WordCell>()
        var lineW = 0f

        for (cell in wordCells) {
            val needed = cell.blockWidth + gap
            if (lineW + needed > maxW && currentLine.isNotEmpty()) {
                lines += currentLine
                currentLine = mutableListOf()
                lineW = 0f
            }
            currentLine += cell
            lineW += needed
        }
        if (currentLine.isNotEmpty()) lines += currentLine

        // Cap to 40 % of screen height — keep the most recent lines, drop oldest
        val maxHeight = resources.displayMetrics.heightPixels * 0.40f
        var usedHeight = sp * 2f
        val cappedLines = ArrayDeque<List<WordCell>>()
        for (line in lines.asReversed()) {
            val lh = lineHeight(line)
            if (usedHeight + lh > maxHeight) break
            cappedLines.addFirst(line)
            usedHeight += lh
        }

        layout = cappedLines.toList()
        requestLayout()
        invalidate()
    }

    // ── Measurement ───────────────────────────────────────────────────────────
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels
        val h = (layout.sumOf { lineHeight(it).toDouble() } + sp * 2).toInt()
        setMeasuredDimension(w, h)
    }

    private fun lineHeight(line: List<WordCell>): Float {
        val hasBreakdown = line.any { it.charBreakdown.isNotEmpty() }
        val hasMeaning   = line.any { it.meaning.isNotEmpty() }
        return pinyinPaint.textSize + charPaint.textSize +
                (if (hasBreakdown) breakdownPaint.textSize + sp else 0f) +
                (if (hasMeaning)   charPaint.textSize      + sp else 0f) +
                sp * 5
    }

    // ── Drawing ───────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var lineY = pinyinPaint.textSize + sp   // pinyin baseline for first line

        for (line in layout) {
            val hasBreakdown = line.any { it.charBreakdown.isNotEmpty() }
            val hasMeaning   = line.any { it.meaning.isNotEmpty() }

            // Pre-compute shared y baselines for this line
            val chY = lineY + sp + charPaint.textSize          // char row
            val bdY = chY   + sp + breakdownPaint.textSize      // breakdown row
            val mnY = (if (hasBreakdown) bdY else chY) + sp + charPaint.textSize  // meaning row

            var x = sp.toFloat()

            for (word in line) {
                // Box height mirrors the line's row count for consistent alignment
                val boxH = pinyinPaint.textSize + charPaint.textSize +
                        (if (hasBreakdown) breakdownPaint.textSize + sp else 0f) +
                        (if (hasMeaning)   charPaint.textSize      + sp else 0f) +
                        sp * 3

                val boxRect = RectF(
                    x - boxPad,
                    lineY - pinyinPaint.textSize - boxPad,
                    x + word.blockWidth + boxPad,
                    lineY - pinyinPaint.textSize - boxPad + boxH + boxPad * 2
                )
                canvas.drawRoundRect(boxRect, boxRadius, boxRadius, boxFillPaint)
                canvas.drawRoundRect(boxRect, boxRadius, boxRadius, boxStrokePaint)

                // Pinyin + characters
                val startX = x
                for (cell in word.cells) {
                    canvas.drawText(cell.pinyin, x, lineY, pinyinPaint)
                    canvas.drawText(cell.char,   x, chY,   charPaint)
                    x += cell.colWidth + sp
                }

                // Individual char breakdown (multi-char words only)
                if (word.charBreakdown.isNotEmpty()) {
                    canvas.drawText(word.charBreakdown, startX, bdY, breakdownPaint)
                }

                // Word meaning
                if (word.meaning.isNotEmpty()) {
                    canvas.drawText(word.meaning, startX, mnY, charPaint)
                }

                x = startX + word.blockWidth + gap
            }

            lineY += lineHeight(line)
        }
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}