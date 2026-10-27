package com.lingxi.tv.ui.player

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.auth.AuthManager
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.data.model.VideoQuality
import com.lingxi.tv.player.DanmakuView
import com.lingxi.tv.player.VideoCacheManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 视频播放页
 * 功能：ExoPlayer 播放 + 弹幕渲染 + 清晰度切换 + 画面比例 + 弹幕开关
 *
 * 遥控器操作：
 *   左/右   快退/快进 10 秒
 *   OK      播放/暂停
 *   上       打开设置菜单（清晰度/画面比例/弹幕/循环）
 *   菜单键   打开设置菜单
 *   下       呼出播放控制面板（暂停、进度条）
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_ID = "extra_video_id"

        // 画面比例模式（Media3 1.2.0 支持的模式）
        private val RESIZE_MODES = intArrayOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
        )
        private val RESIZE_MODE_LABELS = arrayOf("适应屏幕", "全屏裁剪", "填满宽度", "填满高度")
    }

    private lateinit var playerView: PlayerView
    private lateinit var danmakuView: DanmakuView
    private var player: ExoPlayer? = null

    private val repository = LingXiApp.get().repository

    private var video: Video? = null
    private var currentQualityIndex: Int = 0
    private var qualityList: List<VideoQuality> = emptyList()
    private var currentResizeMode: Int = 0
    private var retryCount: Int = 0
    private var maxRetryCount: Int = 3
    private var loopEnabled: Boolean = false

    private var progressUpdateJob: Job? = null
    private var optionsDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 全屏：用 WindowInsetsController 替代已废弃的 FLAG_FULLSCREEN
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        danmakuView = findViewById(R.id.danmaku_view)

        val videoId = intent.getLongExtra(EXTRA_VIDEO_ID, -1)
        if (videoId <= 0) {
            Toast.makeText(this, "视频ID无效", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadVideo(videoId)
    }

    /**
     * 构建媒体源：DefaultMediaSourceFactory 按 URL 自动检测
     * HLS (.m3u8) → HlsMediaSource（按分片缓冲，远距离 seek 正常）
     * 直链 (.mp4 等) → ProgressiveMediaSource
     * 都走磁盘缓存（CacheDataSource 按 segment/byte 缓存）
     */
    private fun buildMediaSource(url: String): MediaSource {
        return DefaultMediaSourceFactory(VideoCacheManager.cacheDataSourceFactory(this))
            .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
    }

    private fun loadVideo(videoId: Long) {
        lifecycleScope.launch {
            val result = repository.getVideoDetail(videoId)
            result.onSuccess { detail ->
                video = detail.video
                setupPlayer(detail.video)
                loadDanmaku(videoId)
            }.onFailure {
                Toast.makeText(this@PlayerActivity, "加载失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupPlayer(video: Video?) {
        if (video == null) return

        qualityList = video.qualityOptions
        // 优先选择 720p
        currentQualityIndex = qualityList.indexOfFirst { it.height == 720 }
        if (currentQualityIndex < 0) currentQualityIndex = 0

        val videoUrl = getCurrentQualityUrl() ?: video.getFullSrcUrl(AuthManager.getPlaybackBaseUrl())

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            exoPlayer.setMediaSource(buildMediaSource(videoUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            danmakuView.setPlaying(true)
                            startProgressUpdate()
                        }
                        Player.STATE_ENDED -> {
                            danmakuView.setPlaying(false)
                            stopProgressUpdate()
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    danmakuView.setPlaying(isPlaying)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    handlePlaybackError(error)
                }
            })
        }
    }

    /**
     * 处理播放错误（如 502）：先重试当前清晰度，再降级到其他清晰度
     */
    private fun handlePlaybackError(error: androidx.media3.common.PlaybackException) {
        val exoPlayer = player ?: return
        val errorMsg = error.localizedMessage ?: "未知错误"
        val position = exoPlayer.currentPosition

        // 先重试当前清晰度
        if (retryCount < maxRetryCount) {
            retryCount++
            Toast.makeText(this, "播放出错，第 $retryCount 次重试...", Toast.LENGTH_SHORT).show()
            val url = getCurrentQualityUrl() ?: video?.getFullSrcUrl(AuthManager.getPlaybackBaseUrl()) ?: return
            exoPlayer.setMediaSource(buildMediaSource(url))
            exoPlayer.prepare()
            exoPlayer.seekTo(position)
            exoPlayer.playWhenReady = true
            return
        }

        // 重试失败，尝试降级到其他清晰度
        retryCount = 0
        val fallbackIndex = qualityList.indices.firstOrNull { it != currentQualityIndex }
        if (fallbackIndex != null) {
            currentQualityIndex = fallbackIndex
            val fallbackLabel = qualityList[fallbackIndex].label
            Toast.makeText(this, "当前清晰度不可用，切换到 $fallbackLabel", Toast.LENGTH_LONG).show()
            val url = getCurrentQualityUrl() ?: return
            exoPlayer.setMediaSource(buildMediaSource(url))
            exoPlayer.prepare()
            exoPlayer.seekTo(position)
            exoPlayer.playWhenReady = true
        } else {
            Toast.makeText(this, "播放失败: $errorMsg", Toast.LENGTH_LONG).show()
        }
    }

    private fun getCurrentQualityUrl(): String? {
        return qualityList.getOrNull(currentQualityIndex)?.getFullUrl(AuthManager.getPlaybackBaseUrl())
    }

    /**
     * 切换清晰度，保持当前播放进度
     */
    private fun switchQuality(index: Int) {
        if (index == currentQualityIndex || index < 0 || index >= qualityList.size) return
        val exoPlayer = player ?: return
        val position = exoPlayer.currentPosition
        currentQualityIndex = index
        retryCount = 0 // 手动切换时重置重试计数

        val url = getCurrentQualityUrl() ?: return
        exoPlayer.setMediaSource(buildMediaSource(url))
        exoPlayer.prepare()
        exoPlayer.seekTo(position)
        exoPlayer.playWhenReady = true

        danmakuView.seekTo(position)
        Toast.makeText(this, "已切换到 ${qualityList[index].label}", Toast.LENGTH_SHORT).show()
    }

    /**
     * 切换画面比例
     */
    private fun cycleResizeMode() {
        currentResizeMode = (currentResizeMode + 1) % RESIZE_MODES.size
        playerView.resizeMode = RESIZE_MODES[currentResizeMode]
        Toast.makeText(this, "画面比例: ${RESIZE_MODE_LABELS[currentResizeMode]}", Toast.LENGTH_SHORT).show()
    }

    /**
     * 切换弹幕开关
     */
    private fun toggleDanmaku() {
        val enabled = !danmakuView.isDanmakuEnabled()
        danmakuView.setDanmakuEnabled(enabled)
        Toast.makeText(this, if (enabled) "弹幕: 开" else "弹幕: 关", Toast.LENGTH_SHORT).show()
    }

    /**
     * 切换单曲循环
     */
    private fun toggleLoop() {
        loopEnabled = !loopEnabled
        player?.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        Toast.makeText(this, if (loopEnabled) "循环: 开" else "循环: 关", Toast.LENGTH_SHORT).show()
    }

    /**
     * 显示设置菜单：清晰度、画面比例、弹幕开关
     */
    private fun showOptionsDialog() {
        // 已有对话框则不重复弹出
        optionsDialog?.let {
            if (it.isShowing) return
        }
        val options = mutableListOf<String>()
        options.add("清晰度: ${qualityList.getOrNull(currentQualityIndex)?.label ?: "默认"}")
        options.add("画面比例: ${RESIZE_MODE_LABELS[currentResizeMode]}")
        options.add("弹幕: ${if (danmakuView.isDanmakuEnabled()) "开" else "关"}")
        options.add("循环: ${if (loopEnabled) "开" else "关"}")

        optionsDialog = AlertDialog.Builder(this)
            .setTitle("播放设置")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showQualityDialog()
                    1 -> cycleResizeMode()
                    2 -> toggleDanmaku()
                    3 -> toggleLoop()
                }
            }
            .setNegativeButton("关闭", null)
            .create()
        optionsDialog?.show()
    }

    /**
     * 显示清晰度选择对话框
     */
    private fun showQualityDialog() {
        if (qualityList.isEmpty()) {
            Toast.makeText(this, "暂无可用清晰度", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = qualityList.map { it.label }.toTypedArray()
        val current = currentQualityIndex

        AlertDialog.Builder(this)
            .setTitle("选择清晰度")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                switchQuality(which)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadDanmaku(videoId: Long) {
        lifecycleScope.launch {
            val result = repository.getDanmaku(videoId)
            result.onSuccess { danmakuList ->
                danmakuView.setDanmaku(danmakuList.items)
            }
        }
    }

    private fun startProgressUpdate() {
        stopProgressUpdate()
        progressUpdateJob = lifecycleScope.launch {
            while (isActive) {
                val position = player?.currentPosition ?: 0
                danmakuView.updatePosition(position)
                delay(100)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    /**
     * 使用 dispatchKeyEvent 在 View 层拦截之前捕获按键，
     * 避免 PlayerView 的播放控制器吞掉菜单键/上键
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_UP -> {
                    showOptionsDialog()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    playerView.showController()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player?.let {
                    val pos = (it.currentPosition - 10000).coerceAtLeast(0)
                    it.seekTo(pos)
                    danmakuView.seekTo(pos)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player?.let {
                    val pos = (it.currentPosition + 10000).coerceAtMost(it.duration)
                    it.seekTo(pos)
                    danmakuView.seekTo(pos)
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player?.play()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player?.pause()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        danmakuView.setPlaying(false)
        stopProgressUpdate()
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回后应用最新弹幕设置
        danmakuView.applySettings()
        player?.play()
        danmakuView.setPlaying(true)
        startProgressUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        danmakuView.setPlaying(false)
        optionsDialog?.dismiss()
        optionsDialog = null
        player?.release()
        player = null
    }
}
