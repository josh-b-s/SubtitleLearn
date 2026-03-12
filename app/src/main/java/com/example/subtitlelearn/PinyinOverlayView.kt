package com.example.subtitlelearn

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.github.promeg.pinyinhelper.Pinyin

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

    private val spacing = 10f

    // Each item is Triple<pinyin, hanzi, meaning>
    private var lines: MutableList<List<Triple<String, String, String>>> = mutableListOf()

    fun setText(words: List<String>, meanings: Map<String, String>) {

        val wordTriples = mutableListOf<Triple<String, String, String>>()

        for (word in words) {
            val wordPinyin = Pinyin.toPinyin(word, " ").lowercase().split(" ")
            val meaning = meanings[word] ?: ""

            for (i in word.indices) {
                val py = if (i < wordPinyin.size) wordPinyin[i] else ""
                val ch = word[i].toString()
                // Only assign meaning to first character of word
                val m = if (i == 0) meaning else ""
                wordTriples.add(Triple(py, ch, m))
            }
        }

        // Build lines
        lines.clear()
        val maxWidth = resources.displayMetrics.widthPixels - 100f
        var line = mutableListOf<Triple<String, String, String>>()
        var currentWidth = 0f

        for ((py, ch, m) in wordTriples) {
            val charWidth = maxOf(
                pinyinPaint.measureText(py),
                textPaint.measureText(ch),
                textPaint.measureText(m)
            ) + spacing

            if (currentWidth + charWidth > maxWidth) {
                lines.add(line)
                line = mutableListOf()
                currentWidth = 0f
            }

            line.add(Triple(py, ch, m))
            currentWidth += charWidth
        }

        if (line.isNotEmpty()) lines.add(line)

        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resources.displayMetrics.widthPixels
        val height = ((pinyinPaint.textSize + textPaint.textSize * 2 + spacing * 3) * lines.size + spacing).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        var y = pinyinPaint.textSize + spacing

        for (line in lines) {
            Log.i("line", line.toString())
            var x = spacing
            for ((py, ch, meaning) in line) {
                // Draw top: Pinyin
                canvas.drawText(py, x, y, pinyinPaint)
                // Draw middle: Hanzi
                canvas.drawText(ch, x, y + spacing + textPaint.textSize, textPaint)
                // Draw bottom: Meaning (only on first char of word)
                if (meaning.isNotEmpty()) {
                    canvas.drawText(meaning, x, y + spacing * 2 + textPaint.textSize * 2, textPaint)
                }
                x += maxOf(
                    pinyinPaint.measureText(py),
                    textPaint.measureText(ch),
                    textPaint.measureText(meaning)
                ) + spacing
            }
            y += pinyinPaint.textSize + textPaint.textSize * 2 + spacing * 3
        }
    }
}