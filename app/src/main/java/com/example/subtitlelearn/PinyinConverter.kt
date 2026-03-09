package com.example.subtitlelearn

import com.github.promeg.pinyinhelper.Pinyin

object PinyinConverter {

    /**
     * Returns character-aligned pinyin:
     * qi   chuang   di   yi   jian   shi
     * 起    床       第   一    件    事
     */
    fun toAlignedPinyin(text: String): String {
        val chars = text.toCharArray()
        val pinyinList = chars.map { char ->
            if (char.toInt() in 0x4E00..0x9FFF) {
                Pinyin.toPinyin(char).lowercase()
            } else {
                char.toString()
            }
        }

        // Add spacing for alignment (simple version)
        val maxLength = pinyinList.maxOfOrNull { it.length } ?: 1
        val spacedPinyin = pinyinList.joinToString(" ") { it.padEnd(maxLength, ' ') }
        val spacedChars = chars.joinToString(" ") { it.toString().padEnd(maxLength, ' ') }

        return "$spacedPinyin\n$spacedChars"
    }
}
