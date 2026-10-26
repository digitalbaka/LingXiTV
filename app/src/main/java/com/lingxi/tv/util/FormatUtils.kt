package com.lingxi.tv.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 格式化工具
 */
object FormatUtils {

    /**
     * 格式化时长（秒 -> mm:ss 或 hh:mm:ss）
     */
    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }

    /**
     * 格式化时长（毫秒）
     */
    fun formatDurationMs(ms: Long): String {
        return formatDuration(TimeUnit.MILLISECONDS.toSeconds(ms))
    }

    /**
     * 格式化播放量/数字
     */
    fun formatCount(count: Int): String {
        return when {
            count >= 10000 -> String.format(Locale.getDefault(), "%.1f万", count / 10000f)
            else -> count.toString()
        }
    }

    /**
     * 格式化时间戳为日期
     */
    fun formatDate(timestampMs: Long): String {
        if (timestampMs <= 0) return ""
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestampMs))
    }
}
