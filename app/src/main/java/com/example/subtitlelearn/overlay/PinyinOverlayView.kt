package com.example.subtitlelearn.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.subtitlelearn.Dictionary

class PinyinOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 50f
    }

    private val pinyinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        textSize = 35f
    }

    private val spacing = 12f

    private var lines = mutableListOf<List<Pair<List<Pair<String, String>>, String>>>()

    fun setText(words: List<String>, meanings: Map<String, String>) {
        val wordCells = words.map { word ->
            val pinyinList = Dictionary.getPinyin(word).split(" ")
            val meaning = meanings[word].orEmpty()

            val chars = word.mapIndexed { i, ch ->
                (pinyinList.getOrNull(i).orEmpty()) to ch.toString()
            }

            chars to meaning
        }

        lines.clear()

        val maxWidth = (width - paddingLeft - paddingRight - spacing).toFloat()
        var currentLine = mutableListOf<Pair<List<Pair<String, String>>, String>>()
        var currentWidth = 0f

        for ((chars, meaning) in wordCells) {
            val wordWidth = chars.sumOf {
                maxOf(
                    pinyinPaint.measureText(it.first),
                    textPaint.measureText(it.second)
                ).toDouble()
            }.toFloat() + spacing * chars.size

            val meaningWidth = if (meaning.isNotEmpty()) textPaint.measureText(meaning) else 0f
            val totalWidth = maxOf(wordWidth, meaningWidth)

            if (currentWidth + totalWidth > maxWidth && currentLine.isNotEmpty()) {
                lines.add(currentLine)
                currentLine = mutableListOf()
                currentWidth = 0f
            }

            currentLine.add(chars to meaning)
            currentWidth += totalWidth + spacing
        }

        if (currentLine.isNotEmpty()) lines.add(currentLine)

        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 }
            ?: resources.displayMetrics.widthPixels

        var totalHeight = spacing

        for (line in lines) {
            totalHeight += rowHeight(line)
        }

        totalHeight += spacing * 2
        setMeasuredDimension(width, totalHeight.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        var y = pinyinPaint.textSize + spacing

        for (line in lines) {
            var x = spacing

            for ((chars, meaning) in line) {
                val startX = x
                var wordWidth = 0f

                for ((py, ch) in chars) {
                    val charWidth = maxOf(
                        pinyinPaint.measureText(py),
                        textPaint.measureText(ch)
                    )
                    canvas.drawText(py, x, y, pinyinPaint)
                    canvas.drawText(ch, x, y + spacing + textPaint.textSize, textPaint)
                    x += charWidth + spacing
                    wordWidth += charWidth + spacing
                }

                if (meaning.isNotEmpty()) {
                    val meaningY = y + spacing * 2 + textPaint.textSize * 2
                    canvas.drawText(meaning, startX, meaningY, textPaint)

                    val meaningWidth = textPaint.measureText(meaning)
                    x = startX + maxOf(wordWidth, meaningWidth + spacing)
                }
            }

            y += rowHeight(line)
        }
    }

    private fun rowHeight(line: List<Pair<List<Pair<String, String>>, String>>): Float {
        val maxMeaningLines = if (line.any { it.second.isNotEmpty() }) 1 else 0
        return pinyinPaint.textSize +
                textPaint.textSize +
                textPaint.textSize * maxMeaningLines +
                spacing * 5
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}