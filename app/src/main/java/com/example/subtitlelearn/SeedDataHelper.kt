package com.example.subtitlelearn

import android.content.Context
import java.time.LocalDate

object SeedDataHelper {

    private val sampleWords = listOf(
        "你好" to ("hello" to "nǐ hǎo"),
        "谢谢" to ("thank you" to "xiè xiè"),
        "水果" to ("fruit" to "shuǐ guǒ"),
        "葡萄" to ("grape" to "pú tao"),
        "苹果" to ("apple" to "píng guǒ"),
        "学习" to ("to study" to "xué xí"),
        "朋友" to ("friend" to "péng yǒu"),
        "电话" to ("telephone" to "diàn huà"),
        "工作" to ("work" to "gōng zuò"),
        "漂亮" to ("beautiful" to "piào liàng"),
        "有意思" to ("interesting" to "yǒu yì si"),
        "喜欢" to ("to like" to "xǐ huān"),
        "觉得" to ("to feel" to "jué de"),
        "知道" to ("to know" to "zhī dào"),
        "时间" to ("time" to "shí jiān"),
        "问题" to ("problem" to "wèn tí"),
        "意思" to ("meaning" to "yì si"),
        "因为" to ("because" to "yīn wèi"),
        "所以" to ("therefore" to "suǒ yǐ"),
        "但是" to ("but" to "dàn shì"),
        "开始" to ("to begin" to "kāi shǐ"),
        "结束" to ("to end" to "jié shù"),
        "高兴" to ("happy" to "gāo xìng"),
        "难过" to ("sad" to "nán guò"),
        "容易" to ("easy" to "róng yì"),
        "困难" to ("difficult" to "kùn nán"),
        "重要" to ("important" to "zhòng yào"),
        "明白" to ("to understand" to "míng bái"),
        "方法" to ("method" to "fāng fǎ"),
        "语言" to ("language" to "yǔ yán")
    )

    fun seed(context: Context, dict: String = Dictionary.currentFile) {
        val today = LocalDate.now().toEpochDay()

        // Spread words across 12 weeks with varying quality, simulating real learning
        sampleWords.forEachIndexed { index, (word, pair) ->
            val (meaning, pinyin) = pair
            Dictionary.addCustomEntry(word, meaning, pinyin)

            // Stagger first review across last 12 weeks
            val weeksAgo = (index % 12).toLong()
            val firstReviewDay = today - (weeksAgo * 7)

            // Simulate 1-4 reviews per word with realistic quality progression
            val reviewCount = 1 + (index % 4)
            repeat(reviewCount) { reviewIndex ->
                val reviewDay = firstReviewDay + (reviewIndex * 3)
                if (reviewDay <= today) {
                    // Earlier reviews more likely to fail, later ones succeed
                    val quality = if (reviewIndex == 0 && index % 3 == 0) 1
                    else if (reviewIndex < 2) 3
                    else 5

                    // Directly inject history by calling review with backdated context
                    SrsStore.reviewOnDay(context, word, quality, reviewDay, dict)
                }
            }
        }
    }

    fun clear(context: Context, dict: String = Dictionary.currentFile) {
        SrsStore.allTracked(context, dict).forEach {
            SrsStore.removeCard(context, it, dict)
        }
    }
}