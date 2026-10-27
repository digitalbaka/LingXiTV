package com.lingxi.tv.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.lingxi.tv.data.model.Danmaku

/**
 * 弹幕渲染视图
 * 使用 Canvas 直接绘制，相比多个 TextView 大幅节省内存
 *
 * 内存优化：
 * - 弹幕对象池复用
 * - 只绘制屏幕内可见弹幕
 * - 限制同屏弹幕数量上限
 * - 弹幕按时间排序，顺序派发
 */
class DanmakuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.LEFT
    }

    // 所有弹幕（按时间排序）
    private val allDanmaku = mutableListOf<Danmaku>()

    // 当前活跃的弹幕（正在屏幕上显示）
    private val activeDanmaku = mutableListOf<DanmakuItem>()

    // 各类弹幕的轨道占用时间
    private val scrollTrackTimes = mutableListOf<Long>()
    private val topTrackTimes = mutableListOf<Long>()
    private val bottomTrackTimes = mutableListOf<Long>()

    private var currentPositionMs: Long = 0
    private var lastDispatchIndex: Int = 0
    private var lastFrameTime: Long = 0
    private var isPlaying: Boolean = false
    private var danmakuEnabled: Boolean = true

    // 配置
    var maxScrollTracks: Int = 10
    var maxTopTracks: Int = 3
    var maxBottomTracks: Int = 3
    var maxScreenCount: Int = 80
    var scrollSpeedBase: Float = 200f // px/s
    var textScale: Float = DanmakuSettings.textScale // 电视端放大弹幕字号

    /** 弹幕不透明度（30-255） */
    var danmakuAlpha: Int = DanmakuSettings.alpha
        set(value) {
            field = value.coerceIn(30, 255)
            invalidate()
        }

    /** 速度倍率（作用于滚动弹幕） */
    var speedScale: Float = DanmakuSettings.speedScale

    /** 应用持久化设置（设置页修改后调用） */
    fun applySettings() {
        textScale = DanmakuSettings.textScale
        speedScale = DanmakuSettings.speedScale
        danmakuAlpha = DanmakuSettings.alpha
        invalidate()
    }

    private val density: Float = resources.displayMetrics.density

    fun setDanmaku(list: List<Danmaku>) {
        allDanmaku.clear()
        allDanmaku.addAll(list)
        allDanmaku.sortBy { it.timeMs }
        reset()
    }

    fun reset() {
        currentPositionMs = 0
        lastDispatchIndex = 0
        clearActive()
        invalidate()
    }

    fun seekTo(positionMs: Long) {
        currentPositionMs = positionMs
        lastDispatchIndex = 0
        for (i in allDanmaku.indices) {
            if (allDanmaku[i].timeMs >= positionMs) {
                lastDispatchIndex = i
                break
            }
            lastDispatchIndex = i + 1
        }
        clearActive()
        invalidate()
    }

    fun updatePosition(positionMs: Long) {
        currentPositionMs = positionMs
        if (isPlaying) {
            dispatchDanmaku()
            // 派发弹幕后必须触发重绘，否则 onDraw 不会被调用
            invalidate()
        }
    }

    fun setPlaying(playing: Boolean) {
        isPlaying = playing
        lastFrameTime = if (playing) System.currentTimeMillis() else 0
    }

    /** 开关弹幕，关闭时清空屏幕上的活跃弹幕 */
    fun setDanmakuEnabled(enabled: Boolean) {
        danmakuEnabled = enabled
        if (!enabled) {
            clearActive()
        }
        invalidate()
    }

    fun isDanmakuEnabled(): Boolean = danmakuEnabled

    private fun dispatchDanmaku() {
        if (!danmakuEnabled) return
        val now = currentPositionMs
        while (lastDispatchIndex < allDanmaku.size &&
            allDanmaku[lastDispatchIndex].timeMs <= now
        ) {
            val d = allDanmaku[lastDispatchIndex]
            // 屏蔽词过滤：命中直接跳过，不占用屏幕名额
            if (!DanmakuSettings.isBlocked(d.content) && activeDanmaku.size < maxScreenCount) {
                spawnDanmaku(d)
            }
            lastDispatchIndex++
        }
    }

    private fun spawnDanmaku(d: Danmaku) {
        val item = DanmakuItem.obtain()
        item.id = d.id
        item.content = d.content
        item.timeMs = d.timeMs
        item.mode = d.mode
        item.color = parseColor(d.color)
        item.fontSize = (d.fontSize * textScale * density).toInt().coerceIn(20, 60)

        paint.textSize = item.fontSize.toFloat()
        item.width = paint.measureText(item.content)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val lineHeight = item.fontSize * 1.3f

        when (item.mode) {
            Danmaku.MODE_TOP -> {
                item.track = findFreeTrack(topTrackTimes, maxTopTracks)
                item.y = (item.track + 1) * lineHeight
                item.x = (viewWidth - item.width) / 2
                item.speed = 0f
                reserveTrack(topTrackTimes, item.track, 4000L)
            }
            Danmaku.MODE_BOTTOM -> {
                item.track = findFreeTrack(bottomTrackTimes, maxBottomTracks)
                item.y = viewHeight - (item.track + 1) * lineHeight
                item.x = (viewWidth - item.width) / 2
                item.speed = 0f
                reserveTrack(bottomTrackTimes, item.track, 4000L)
            }
            else -> {
                item.track = findFreeTrack(scrollTrackTimes, maxScrollTracks)
                item.y = (item.track + 1) * lineHeight
                item.x = viewWidth
                item.speed = scrollSpeedBase * speedScale * density * (1f + item.width / 600f)
                reserveTrack(scrollTrackTimes, item.track,
                    ((item.width + viewWidth) / item.speed * 1000).toLong())
            }
        }
        item.alive = true
        activeDanmaku.add(item)
    }

    private fun findFreeTrack(tracks: MutableList<Long>, maxTracks: Int): Int {
        val now = System.currentTimeMillis()
        for (i in 0 until maxTracks) {
            if (i >= tracks.size) {
                tracks.add(0L)
                return i
            }
            if (tracks[i] <= now) {
                return i
            }
        }
        return 0
    }

    private fun reserveTrack(tracks: MutableList<Long>, track: Int, durationMs: Long) {
        while (tracks.size <= track) {
            tracks.add(0L)
        }
        tracks[track] = System.currentTimeMillis() + durationMs
    }

    private fun clearActive() {
        activeDanmaku.forEach { DanmakuItem.recycle(it) }
        activeDanmaku.clear()
        scrollTrackTimes.clear()
        topTrackTimes.clear()
        bottomTrackTimes.clear()
    }

    private fun parseColor(colorStr: String): Int {
        return try {
            if (colorStr.startsWith("#")) {
                Color.parseColor(colorStr)
            } else {
                Color.parseColor("#$colorStr")
            }
        } catch (e: Exception) {
            Color.WHITE
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (activeDanmaku.isEmpty()) return

        val now = System.currentTimeMillis()
        val dt = if (lastFrameTime > 0) now - lastFrameTime else 16
        lastFrameTime = now
        val dtSec = dt / 1000f

        val iterator = activeDanmaku.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()

            if (item.mode == Danmaku.MODE_SCROLL) {
                item.x -= item.speed * dtSec
            }

            val outOfScreen = when (item.mode) {
                Danmaku.MODE_SCROLL -> item.x + item.width < 0
                else -> item.timeMs + 4000 < currentPositionMs
            }

            if (outOfScreen) {
                DanmakuItem.recycle(item)
                iterator.remove()
                continue
            }

            paint.textSize = item.fontSize.toFloat()
            paint.color = Color.BLACK
            paint.alpha = danmakuAlpha
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawText(item.content, item.x, item.y, paint)

            paint.color = item.color
            paint.alpha = danmakuAlpha
            paint.style = Paint.Style.FILL
            canvas.drawText(item.content, item.x, item.y, paint)
            paint.alpha = 255
        }

        if (isPlaying) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearActive()
    }
}
