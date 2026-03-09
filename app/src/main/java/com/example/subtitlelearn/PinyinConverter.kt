package com.example.subtitlelearn

import com.github.promeg.pinyinhelper.Pinyin

object PinyinConverter {

    fun toPinyinLine(text: String): String {
        return Pinyin.toPinyin(text, " ").lowercase()
    }

}
