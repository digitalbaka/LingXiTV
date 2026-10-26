package com.lingxi.tv.ui.player

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.api.ApiClient
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.data.model.VideoQuality
import com.lingxi.tv.player.DanmakuView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 视频播放页
 * 集成 ExoPlayer + 弹幕渲染
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_ID = "extra_video_id"
    }

    private lateinit var playerView: PlayerView
    private lateinit var danmakuView: DanmakuView
    private var player: ExoPlayer? = null

    private val repository = LingXiApp.get().repository

    private var video: Video? = null
    private var currentQualityIndex: Int = 0
    private var qualityList: List<VideoQuality> = emptyList()

    private var progressUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全屏 + 保持屏幕常亮
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
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

        // 选择画质：优先 720p，电视端平衡流畅与清晰度
        qualityList = video.qualityOptions
        val preferredHeight = 720
        currentQualityIndex = qualityList.indexOfFirst { it.height == preferredHeight }
            .coerceAtLeast(0)
        if (currentQualityIndex < 0) currentQualityIndex = 0

        val quality = qualityList.getOrNull(currentQualityIndex)
        val videoUrl = quality?.getFullUrl(ApiClient.BASE_URL)
            ?: video.getFullSrcUrl(ApiClient.BASE_URL)

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            exoPlayer.setMediaItem(mediaItem)
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
            })
        }
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player?.let { it.seekTo((it.currentPosition - 10000).coerceAtLeast(0)) }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player?.let { it.seekTo((it.currentPosition + 10000).coerceAtMost(it.duration)) }
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
        player?.play()
        danmakuView.setPlaying(true)
        startProgressUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        danmakuView.setPlaying(false)
        player?.release()
        player = null
    }
}
