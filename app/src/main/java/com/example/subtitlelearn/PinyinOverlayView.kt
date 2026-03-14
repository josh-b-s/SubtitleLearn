package com.example.subtitlelearn

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
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

    private val spacing = 20f

    // Lines: each line is a list of words
    // Each word: Pair<List<Pair<pinyin, hanzi>>, meaning>
    private var lines: MutableList<List<Pair<List<Pair<String,String>>, String>>> = mutableListOf()

    fun setText(words: List<String>, meanings: Map<String, String>) {

        val wordCells = mutableListOf<Pair<List<Pair<String,String>>, String>>()
        for (word in words) {
            val pinyinList = Pinyin.toPinyin(word, " ").lowercase().split(" ")
            val meaning = meanings[word] ?: ""

            val characters = mutableListOf<Pair<String,String>>()
            for (i in word.indices) {
                val py = if (i < pinyinList.size) pinyinList[i] else ""
                val ch = word[i].toString()
                characters.add(Pair(py, ch))
            }

            wordCells.add(Pair(characters, meaning))
        }

        // Wrap words into lines
        lines.clear()
        val maxWidth = resources.displayMetrics.widthPixels - spacing
        var currentLine = mutableListOf<Pair<List<Pair<String,String>>, String>>()
        var currentWidth = 0f

        for ((chars, meaning) in wordCells) {
            var wordWidth = 0f
            for ((py, ch) in chars) {
                wordWidth += maxOf(pinyinPaint.measureText(py), textPaint.measureText(ch)) + spacing
            }

            // Reserve space for meaning: check width of meaning text
            val meaningWidth = if (meaning.isNotEmpty()) textPaint.measureText(meaning) + spacing else 0f
            val totalWordWidth = maxOf(wordWidth, meaningWidth)

            if (currentWidth + totalWordWidth > maxWidth) {
                lines.add(currentLine)
                currentLine = mutableListOf()
                currentWidth = 0f
            }

            currentLine.add(Pair(chars, meaning))
            currentWidth += totalWordWidth
        }

        if (currentLine.isNotEmpty()) lines.add(currentLine)

        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resources.displayMetrics.widthPixels
        val height =
            ((pinyinPaint.textSize + textPaint.textSize * 2 + spacing * 3) * lines.size + spacing).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        var y = pinyinPaint.textSize + spacing

        for (line in lines) {
            var x = spacing

            for ((chars, meaning) in line) {
                val wordStartX = x
                var wordWidth = 0f

                for ((py, ch) in chars) {
                    val charWidth = maxOf(pinyinPaint.measureText(py), textPaint.measureText(ch))
                    canvas.drawText(py, x, y, pinyinPaint)
                    canvas.drawText(ch, x, y + spacing + textPaint.textSize, textPaint)
                    x += charWidth + spacing
                    wordWidth += charWidth + spacing
                }

                if (meaning.isNotEmpty()) {
                    canvas.drawText(
                        meaning,
                        wordStartX,
                        y + spacing * 2 + textPaint.textSize * 2,
                        textPaint
                    )
                    // Ensure next word starts after the wider of word or meaning
                    x = wordStartX + maxOf(wordWidth, textPaint.measureText(meaning) + spacing)
                }
            }

            y += pinyinPaint.textSize + textPaint.textSize * 2 + spacing * 3
        }
    }
}