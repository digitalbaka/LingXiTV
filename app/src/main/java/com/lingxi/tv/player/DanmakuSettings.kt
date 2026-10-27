package com.lingxi.tv.player

import android.content.Context
import android.content.SharedPreferences
import com.lingxi.tv.LingXiApp

/**
 * 弹幕播放设置（持久化到 SharedPreferences）
 * 字号倍率 / 速度倍率 / 不透明度 / 屏蔽词
 */
object DanmakuSettings {

    private const val PREFS_NAME = "lingxi_danmaku"
    private const val KEY_TEXT_SCALE = "text_scale"       // 字号倍率
    private const val KEY_SPEED_SCALE = "speed_scale"     // 速度倍率
    private const val KEY_ALPHA = "alpha"                 // 不透明度 0-255
    private const val KEY_BLOCK_WORDS = "block_words"     // 屏蔽词，逗号分隔

    const val DEFAULT_TEXT_SCALE = 1.4f
    const val DEFAULT_SPEED_SCALE = 1.0f
    const val DEFAULT_ALPHA = 255

    private val prefs: SharedPreferences by lazy {
        LingXiApp.get().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var textScale: Float
        get() = prefs.getFloat(KEY_TEXT_SCALE, DEFAULT_TEXT_SCALE)
        set(value) = prefs.edit().putFloat(KEY_TEXT_SCALE, value).apply()

    var speedScale: Float
        get() = prefs.getFloat(KEY_SPEED_SCALE, DEFAULT_SPEED_SCALE)
        set(value) = prefs.edit().putFloat(KEY_SPEED_SCALE, value).apply()

    var alpha: Int
        get() = prefs.getInt(KEY_ALPHA, DEFAULT_ALPHA)
        set(value) = prefs.edit().putInt(KEY_ALPHA, value.coerceIn(30, 255)).apply()

    /** 屏蔽词列表 */
    var blockWords: List<String>
        get() = prefs.getString(KEY_BLOCK_WORDS, "")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_BLOCK_WORDS, value.joinToString(","))
            .apply()

    /** 内容是否命中屏蔽词 */
    fun isBlocked(content: String): Boolean {
        val words = blockWords
        if (words.isEmpty()) return false
        return words.any { content.contains(it, ignoreCase = true) }
    }
}
