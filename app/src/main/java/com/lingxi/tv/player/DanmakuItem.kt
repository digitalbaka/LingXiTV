package com.lingxi.tv.player

import com.lingxi.tv.data.model.Danmaku

/**
 * 弹幕项 - 用于渲染
 * 使用轻量数据结构，避免创建过多对象
 */
class DanmakuItem {
    var id: Long = 0
    var content: String = ""
    var timeMs: Long = 0
    var mode: Int = Danmaku.MODE_SCROLL
    var color: Int = 0xFFFFFF
    var fontSize: Int = 25

    // 渲染状态
    var x: Float = 0f
    var y: Float = 0f
    var width: Float = 0f
    var speed: Float = 0f
    var alive: Boolean = false
    var track: Int = 0

    fun reset() {
        id = 0
        content = ""
        timeMs = 0
        mode = Danmaku.MODE_SCROLL
        color = 0xFFFFFF
        fontSize = 25
        x = 0f
        y = 0f
        width = 0f
        speed = 0f
        alive = false
        track = 0
    }

    companion object {
        /**
         * 对象池 - 复用弹幕对象，减少 GC
         */
        private val pool = ArrayDeque<DanmakuItem>(64)

        fun obtain(): DanmakuItem {
            return pool.removeLastOrNull() ?: DanmakuItem()
        }

        fun recycle(item: DanmakuItem) {
            item.reset()
            if (pool.size < 128) {
                pool.addLast(item)
            }
        }
    }
}
