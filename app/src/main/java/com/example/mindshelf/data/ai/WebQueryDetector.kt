package com.example.mindshelf.data.ai

import java.util.Locale

/** 判断用户是否在询问需要联网的外部信息（非寒暄、非本地知识库问题）。 */
object WebQueryDetector {
    private val smallTalk = setOf(
        "你好", "您好", "嗨", "hi", "hello", "hey", "在吗", "在不在",
        "谢谢", "感谢", "多谢", "好的", "ok", "okay", "嗯", "哦", "再见", "拜拜",
    )

    private val keywords = listOf(
        "新闻", "资讯", "头条", "时事", "最新消息",
        "天气", "气温", "降雨", "预报",
        "股价", "股票", "汇率", "基金", "行情",
        "热搜", "世界杯", "赛事",
        "联网搜索", "搜索一下", "帮我搜",
        "news", "weather", "stock price", "search the web",
    )

    private val explicitPatterns = listOf(
        Regex("搜(索|一下).+"),
        Regex("(最新|今天|现在|当前|近期).+(消息|新闻|动态|进展|情况|价格|股价)"),
    )

    fun looksLikeWebQuery(text: String): Boolean {
        val q = text.trim()
        if (q.isBlank()) return false
        val lower = q.lowercase(Locale.ROOT)
        if (smallTalk.contains(lower) || smallTalk.contains(q)) return false
        if (q.length <= 6 && keywords.none { lower.contains(it) }) return false
        if (explicitPatterns.any { it.containsMatchIn(q) }) return true
        return keywords.any { lower.contains(it) }
    }
}
