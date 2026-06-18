package com.example.mindshelf.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatRelativeTime(epochMillis: Long): String {
    val diff = System.currentTimeMillis() - epochMillis
    if (diff < TimeUnit.MINUTES.toMillis(1)) return "刚刚"
    if (diff < TimeUnit.HOURS.toMillis(1)) {
        return "${TimeUnit.MILLISECONDS.toMinutes(diff)} 分钟前"
    }
    if (diff < TimeUnit.DAYS.toMillis(1)) {
        return "${TimeUnit.MILLISECONDS.toHours(diff)} 小时前"
    }
    if (diff < TimeUnit.DAYS.toMillis(7)) {
        return "${TimeUnit.MILLISECONDS.toDays(diff)} 天前"
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochMillis))
}
