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
    private val charPaint = paint(Color.WHITE, 50f)
    private val pinyinPaint = paint(Color.YELLOW, 35f)
    private val boxFillPaint = paint(Color.argb(180, 0, 0, 0)).apply { style = Paint.Style.FILL }
    private val boxStrokePaint = paint(Color.argb(90, 255, 255, 255)).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }

    private fun paint(color: Int, textSize: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; if (textSize > 0) this.textSize = textSize
        }

    // ── Layout constants ──────────────────────────────────────────────────────
    private val boxRadius = 18f
    private val boxPad = 12f
    private val gap = 20f        // between word blocks
    private val sp = 12f         // internal spacing

    // ── Pre-computed layout ───────────────────────────────────────────────────
    private data class CharCell(val pinyin: String, val char: String, val colWidth: Float)
    private data class WordCell(
        val cells: List<CharCell>,
        val meaning: String,
        val blockWidth: Float
    )

    private var layout: List<List<WordCell>> = emptyList()  // lines → words

    // ── Public API ────────────────────────────────────────────────────────────
    fun setText(words: List<String>, meanings: Map<String, String>) {
        val wordCells = words.map { word ->
            val pinyinTokens = Dictionary.getPinyin(word).split(" ")
            val cells = word.mapIndexed { i, ch ->
                val py = pinyinTokens.getOrElse(i) { "" }
                val colW = maxOf(pinyinPaint.measureText(py), charPaint.measureText(ch.toString()))
                CharCell(py, ch.toString(), colW)
            }
            val wordW = cells.sumOf { it.colWidth.toDouble() }.toFloat() + sp * cells.size
            val meaning = meanings[word].orEmpty()
            val meaningW = if (meaning.isNotEmpty()) charPaint.measureText(meaning) + sp else 0f
            WordCell(cells, meaning, maxOf(wordW, meaningW))
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

        layout = lines
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
        val meaningRows = if (line.any { it.meaning.isNotEmpty() }) 1 else 0
        return pinyinPaint.textSize + charPaint.textSize +
                charPaint.textSize * meaningRows + sp * 5
    }

    // ── Drawing ───────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var y = pinyinPaint.textSize + sp

        for (line in layout) {
            var x = sp.toFloat()

            for (word in line) {
                val boxH = pinyinPaint.textSize + charPaint.textSize +
                        (if (word.meaning.isNotEmpty()) charPaint.textSize + sp else 0f) + sp * 3
                val boxRect = RectF(
                    x - boxPad,
                    y - pinyinPaint.textSize - boxPad,
                    x + word.blockWidth + boxPad,
                    y - pinyinPaint.textSize - boxPad + boxH + boxPad * 2
                )

                canvas.drawRoundRect(boxRect, boxRadius, boxRadius, boxFillPaint)
                canvas.drawRoundRect(boxRect, boxRadius, boxRadius, boxStrokePaint)

                val startX = x
                for (cell in word.cells) {
                    canvas.drawText(cell.pinyin, x, y, pinyinPaint)
                    canvas.drawText(cell.char, x, y + sp + charPaint.textSize, charPaint)
                    x += cell.colWidth + sp
                }

                if (word.meaning.isNotEmpty()) {
                    canvas.drawText(
                        word.meaning,
                        startX,
                        y + sp * 2 + charPaint.textSize * 2,
                        charPaint
                    )
                }

                x = startX + word.blockWidth + gap
            }

            y += lineHeight(line)
        }
    }

    override fun performClick(): Boolean {
        super.performClick(); return true
    }
}