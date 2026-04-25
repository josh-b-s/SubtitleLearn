package com.example.subtitlelearn

fun segment(text: String): List<String> {
    val spaced = text.trim()
    if (spaced.contains(" ")) {
        return spaced.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    val result = mutableListOf<String>()
    var i = 0
    val maxWordLen = 6

    while (i < text.length) {
        var match: String? = null
        val maxLen = minOf(maxWordLen, text.length - i)

        for (len in maxLen downTo 1) {
            val sub = text.substring(i, i + len)
            if (Dictionary.getMeaning(sub).isNotEmpty()) {
                match = sub
                break
            }
        }

        if (match != null) {
            result.add(match)
            i += match.length
        } else {
            result.add(text[i].toString())
            i++
        }
    }

    return result
}