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

    private var chineseText: String = ""
    private var pinyinList: List<String> = emptyList()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 50f
    }

    private val pinyinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        textSize = 35f
    }

    private val spacing = 10f
    private var lines: MutableList<List<Pair<String, String>>> = mutableListOf()

    fun setText(text: String) {
        chineseText = text
        pinyinList = text.map { char ->
            if (char.toString().matches(Regex("[\\u4e00-\\u9fa5]"))) {
                Pinyin.toPinyin(char).lowercase()
            } else {
                char.toString()
            }
        }
        createLines()
        requestLayout()
        invalidate()
    }

    private fun createLines() {
        lines.clear()
        val maxWidth = resources.displayMetrics.widthPixels - 100f
        var line = mutableListOf<Pair<String, String>>()
        var currentWidth = 0f

        for (i in chineseText.indices) {
            val py = pinyinList[i]
            val ch = chineseText[i].toString()
            val charWidth = maxOf(pinyinPaint.measureText(py), textPaint.measureText(ch)) + spacing

            if (currentWidth + charWidth > maxWidth) {
                lines.add(line)
                line = mutableListOf()
                currentWidth = 0f
            }

            line.add(py to ch)
            currentWidth += charWidth
        }

        if (line.isNotEmpty()) {
            lines.add(line)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resources.displayMetrics.widthPixels
        val height = ((pinyinPaint.textSize + textPaint.textSize + spacing) * lines.size + spacing).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        var y = pinyinPaint.textSize + spacing

        for (line in lines) {
            var x = spacing
            for ((py, ch) in line) {
                canvas.drawText(py, x, y, pinyinPaint)
                canvas.drawText(ch, x, y + spacing + textPaint.textSize, textPaint)

                x += maxOf(pinyinPaint.measureText(py), textPaint.measureText(ch)) + spacing
            }
            y += pinyinPaint.textSize + textPaint.textSize + spacing * 2
        }
    }
}
