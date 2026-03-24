package com.example.subtitlelearn.ui.theme

import com.example.subtitlelearn.CedictDictionary

fun segment(text: String): List<String> {
    val result = mutableListOf<String>()
    var i = 0

    val maxWordLen = 6 // typical Chinese word max length

    while (i < text.length) {
        var match: String? = null

        val maxLen = minOf(maxWordLen, text.length - i)

        for (len in maxLen downTo 1) {
            val sub = text.substring(i, i + len)
            if (CedictDictionary.get(sub).isNotEmpty()) {
                match = sub
                break
            }
        }

        if (match != null) {
            result.add(match)
            i += match.length
        } else {
            // fallback: single char
            result.add(text[i].toString())
            i++
        }
    }

    return result
}