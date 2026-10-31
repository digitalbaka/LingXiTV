﻿package com.lingxi.tv.ui.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.media3.exoplayer.DefaultLoadControl
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
import com.lingxi.tv.ui.list.VideoListActivity
import com.lingxi.tv.ui.space.UserSpaceActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 视频播放页
 *
 * 普通模式：播放单个视频，上键呼出设置，下键呼出控制面板。
 * 关注动态模式（EXTRA_FEED_MODE）：全屏铺满，按向下键切换到下一条关注动态，
 * 列表到底会自动加载下一页，可以一直往下看。
 *
 * 遥控器操作：
 *   左/右   快退/快进 10 秒
 *   OK      播放/暂停
 *   上       打开设置菜单（清晰度/画面比例/弹幕/循环）
 *   下       普通模式呼出控制面板；关注动态模式切下一条
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_FEED_MODE = "extra_feed_mode"
        const val EXTRA_START_INDEX = "extra_start_index"

        private const val FEED_PAGE_SIZE = 30
        private const val TAG = "LingXiPlayer"

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

    /** 本次播放是否已经尝试过换清晰度 */
    private var triedQualityFallback: Boolean = false
    /** 本次播放失败后是否已经重新做过节点授权（公网 IP 可能变化） */
    private var reHandoffTried: Boolean = false
    /** 本次页面是否已经做过节点授权 */
    private var nodeHandoffDone: Boolean = false

    // ==================== 关注动态模式 ====================
    private var feedMode: Boolean = false
    private var feedIndex: Int = 0
    private var feedPage: Int = 1
    private var feedHasMore: Boolean = false
    private var feedLoading: Boolean = false
    private val feedVideos = mutableListOf<Video>()

    private var progressUpdateJob: Job? = null
    private var optionsDialog: AlertDialog? = null

    // ==================== 互动状态（点赞/投喂/收藏/关注） ====================
    private var liked: Boolean = false
    /** 我在本视频已投喂的小鱼干数量（单稿上限 2） */
    private var coined: Int = 0
    private var faved: Boolean = false
    private var followingUp: Boolean = false
    /** 正在观看人数（进入页面拉一次，菜单里可手动刷新） */
    private var viewersCount: Int = 0

    /** 播放量/观看进度上报协程 */
    private var reportJob: Job? = null

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

        feedMode = intent.getBooleanExtra(EXTRA_FEED_MODE, false)
        if (feedMode) {
            // 关注动态：默认与其他视频一致用「适应屏幕」，用户可手动切画面比例
            feedIndex = intent.getIntExtra(EXTRA_START_INDEX, 0).coerceAtLeast(0)
            loadFeed()
            return
        }

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
     *
     * 选中直连节点时必须附加 X-Forwarded-For 头：节点自身拿不到 TCP 来源 IP，
     * 只靠该头识别 handoff 授权的公网 IP，否则返回 HTTP 200 + {code:40301} JSON，
     * ExoPlayer 无法解析（UnrecognizedInputFormatException）
     */
    private fun buildMediaSource(url: String): MediaSource {
        val headers = mutableMapOf<String, String>()
        val grantedIp = AuthManager.nodeGrantedIp
        if (AuthManager.nodeId > 0 && !grantedIp.isNullOrEmpty()) {
            headers["X-Forwarded-For"] = grantedIp
            headers["X-Real-IP"] = grantedIp
        }
        return DefaultMediaSourceFactory(VideoCacheManager.cacheDataSourceFactory(this, headers))
            .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
    }

    // ==================== 关注动态 ====================

    private fun loadFeed() {
        lifecycleScope.launch {
            val result = repository.getFollowingVideos(1, FEED_PAGE_SIZE)
            val page = result.getOrNull()
            if (page == null) {
                Toast.makeText(this@PlayerActivity,
                    "加载关注动态失败：${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            feedVideos.addAll(page.items.filter { it.isVideo || it.isAudio })
            feedPage = 1
            feedHasMore = page.hasMore
            if (feedVideos.isEmpty()) {
                Toast.makeText(this@PlayerActivity, "关注的 UP 主还没有新动态", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            if (feedIndex >= feedVideos.size) feedIndex = 0
            playFeed(feedIndex)
        }
    }

    private fun playFeed(index: Int) {
        val v = feedVideos.getOrNull(index) ?: return
        feedIndex = index
        Toast.makeText(this, "${index + 1}/${feedVideos.size} ${v.title}", Toast.LENGTH_SHORT).show()
        loadVideo(v.id)
    }

    /** 按下键：切换下一条，必要时加载下一页 */
    private fun nextFeed() {
        if (feedIndex + 1 < feedVideos.size) {
            playFeed(feedIndex + 1)
            return
        }
        if (!feedHasMore) {
            Toast.makeText(this, "已经是最后一条关注动态", Toast.LENGTH_SHORT).show()
            return
        }
        if (feedLoading) return
        feedLoading = true
        lifecycleScope.launch {
            val result = repository.getFollowingVideos(feedPage + 1, FEED_PAGE_SIZE)
            feedLoading = false
            val page = result.getOrNull()
            if (page == null) {
                Toast.makeText(this@PlayerActivity, "加载更多失败", Toast.LENGTH_SHORT).show()
                return@launch
            }
            feedPage++
            feedHasMore = page.hasMore
            feedVideos.addAll(page.items.filter { it.isVideo || it.isAudio })
            if (feedIndex + 1 < feedVideos.size) {
                playFeed(feedIndex + 1)
            } else {
                Toast.makeText(this@PlayerActivity, "已经是最后一条关注动态", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== 播放 ====================

    private fun loadVideo(videoId: Long) {
        lifecycleScope.launch {
            ensureNodeHandoff()
            val result = repository.getVideoDetail(videoId)
            result.onSuccess { detail ->
                val v = detail.video
                if (v == null) {
                    Toast.makeText(this@PlayerActivity, "视频不存在", Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                video = v
                // 从详情初始化互动状态（viewer 仅登录时返回，未登录保持默认 false/0）
                v.viewer?.let { viewer ->
                    liked = viewer.liked
                    coined = viewer.coined
                    faved = viewer.faved
                }
                followingUp = detail.following
                viewersCount = 0
                setupPlayer(v)
                loadDanmaku(videoId)
                refreshViewers()
                startReporting()
            }.onFailure {
                Toast.makeText(this@PlayerActivity, "加载失败: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 切换节点后必须先向 /gateway/handoff 授权当前公网 IP，并把服务端回传的 IP
     * 存到 AuthManager.nodeGrantedIp——播放时通过 X-Forwarded-For 头带给节点，
     * 否则节点识别不了来源，返回 HTTP 200 + {code:40301} 的 JSON，表现为无法播放。
     * 授权失败不回退主站（用户明确要求走自己选的节点），只提示并继续尝试。
     */
    private suspend fun ensureNodeHandoff() {
        if (nodeHandoffDone) return
        if (AuthManager.nodeId <= 0) {
            nodeHandoffDone = true
            return
        }
        val result = repository.handoff(AuthManager.nodeId)
        val data = result.getOrNull()
        if (result.isSuccess && data != null) {
            nodeHandoffDone = true
            if (data.ip.isNotEmpty()) AuthManager.nodeGrantedIp = data.ip
            Log.d(TAG, "handoff ok: nodeId=${AuthManager.nodeId} ip=${data.ip}")
        } else {
            val msg = result.exceptionOrNull()?.message ?: "未知错误"
            Log.w(TAG, "handoff failed: $msg")
            Toast.makeText(this, "节点授权失败：$msg，仍将尝试直连", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupPlayer(video: Video?) {
        if (video == null) return

        // 切换下一条时释放旧播放器，避免多个 ExoPlayer 同时占用解码器
        player?.release()
        player = null

        // 每条视频都强制应用当前画面比例，防止任何路径（节点切换/重试/重建）留下错误的比例状态
        playerView.resizeMode = RESIZE_MODES[currentResizeMode]

        retryCount = 0
        triedQualityFallback = false
        reHandoffTried = false

        qualityList = video.qualityOptions
        // 优先选择 720p
        currentQualityIndex = qualityList.indexOfFirst { it.height == 720 }
        if (currentQualityIndex < 0) currentQualityIndex = 0

        val videoUrl = currentPlayUrl()
        if (videoUrl == null) {
            Toast.makeText(this, "该视频没有可播放地址", Toast.LENGTH_SHORT).show()
            return
        }
        Log.d(TAG, "play videoId=${video.id} url=$videoUrl")

        player = ExoPlayer.Builder(this)
            // 缩小缓冲时长：电视内存有限，默认 50s 缓冲太占内存
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 30_000, 1_500, 3_000)
                    .build()
            )
            .build().also { exoPlayer ->
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
                            // 关注动态：一条播完自动切下一条
                            if (feedMode) {
                                nextFeed()
                            }
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

    /** 播放基准地址：始终走用户选择的节点（未选节点时为主站） */
    private fun currentPlayUrl(): String? {
        val base = AuthManager.getPlaybackBaseUrl()
        val qualityUrl = qualityList.getOrNull(currentQualityIndex)
            ?.takeIf { it.url.isNotEmpty() }
            ?.getFullUrl(base)
        if (qualityUrl != null) return qualityUrl
        val src = video?.src.orEmpty()
        if (src.isEmpty()) return null
        return video?.getFullSrcUrl(base)
    }

    private fun playFrom(position: Long) {
        val exoPlayer = player ?: return
        val url = currentPlayUrl() ?: return
        exoPlayer.setMediaSource(buildMediaSource(url))
        exoPlayer.prepare()
        if (position > 0) exoPlayer.seekTo(position)
        exoPlayer.playWhenReady = true
    }

    /**
     * 处理播放错误（始终留在用户选择的节点，不回退主站）：
     * 0. 节点播放首次失败——重新 handoff 授权一次（公网 IP 可能已变化）再重试
     * 1. 重试 3 次
     * 2. 换一个清晰度
     */
    private fun handlePlaybackError(error: androidx.media3.common.PlaybackException) {
        val exoPlayer = player ?: return
        val errorMsg = error.localizedMessage ?: "未知错误"
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)

        if (AuthManager.nodeId > 0 && !reHandoffTried) {
            reHandoffTried = true
            nodeHandoffDone = false
            Log.w(TAG, "node playback failed, re-handoff: $errorMsg")
            // 先清掉该地址可能存在的「毒缓存」：节点未授权时返回 HTTP 200 + {code:40301}
            // JSON，CacheDataSource 误当视频流缓存，之后即便授权成功也永远命中它
            currentPlayUrl()?.let { VideoCacheManager.evict(this, it) }
            Toast.makeText(this, "节点连接失败，正在重新授权…", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                ensureNodeHandoff()
                playFrom(position)
            }
            return
        }

        if (retryCount < maxRetryCount) {
            retryCount++
            Toast.makeText(this, "播放出错，第 $retryCount 次重试...", Toast.LENGTH_SHORT).show()
            playFrom(position)
            return
        }

        if (!triedQualityFallback) {
            val fallbackIndex = qualityList.indices.firstOrNull { it != currentQualityIndex }
            if (fallbackIndex != null) {
                triedQualityFallback = true
                currentQualityIndex = fallbackIndex
                retryCount = 0
                val fallbackLabel = qualityList[fallbackIndex].label
                Toast.makeText(this, "当前清晰度不可用，切换到 $fallbackLabel", Toast.LENGTH_LONG).show()
                playFrom(position)
                return
            }
        }

        Toast.makeText(this, "播放失败: $errorMsg", Toast.LENGTH_LONG).show()
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
        triedQualityFallback = false

        playFrom(position)
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
     * 显示设置菜单：清晰度/画面比例/弹幕/循环 + 互动（点赞/投喂/收藏/关注）
     * + UP主空间/相关视频/正在观看人数
     */
    private fun showOptionsDialog() {
        // 已有对话框则不重复弹出
        optionsDialog?.let {
            if (it.isShowing) return
        }
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        options.add("清晰度: ${qualityList.getOrNull(currentQualityIndex)?.label ?: "默认"}")
        actions.add { showQualityDialog() }
        options.add("画面比例: ${RESIZE_MODE_LABELS[currentResizeMode]}")
        actions.add { cycleResizeMode() }
        options.add("弹幕: ${if (danmakuView.isDanmakuEnabled()) "开" else "关"}")
        actions.add { toggleDanmaku() }
        options.add("循环: ${if (loopEnabled) "开" else "关"}")
        actions.add { toggleLoop() }

        val v = video
        val upUid = v?.up?.id ?: v?.uid ?: 0
        // 互动项需要登录，未登录时不显示（服务端要求 Bearer）
        if (v != null && AuthManager.isLoggedIn) {
            options.add(if (liked) "已点赞 ✓" else "点赞")
            actions.add { doLike() }
            options.add(if (coined > 0) "投喂小鱼干（已投 $coined/2）" else "投喂小鱼干")
            actions.add { doCoin() }
            options.add(if (faved) "已收藏 ✓" else "收藏")
            actions.add { doFavorite() }
            // 自己的视频不显示关注入口
            if (upUid > 0 && upUid != AuthManager.user?.id) {
                options.add(if (followingUp) "已关注 UP 主 ✓" else "关注 UP 主")
                actions.add { doFollowUp(upUid) }
            }
        }
        if (upUid > 0) {
            options.add("UP主空间")
            actions.add {
                startActivity(
                    Intent(this, UserSpaceActivity::class.java)
                        .putExtra(UserSpaceActivity.EXTRA_UID, upUid)
                )
            }
        }
        if (v != null) {
            options.add("相关视频")
            actions.add { openRelated() }
            options.add(if (viewersCount > 0) "正在观看：$viewersCount 人" else "正在观看：点击查询")
            actions.add { refreshViewers(showToast = true) }
        }

        optionsDialog = AlertDialog.Builder(this)
            .setTitle("播放设置")
            .setItems(options.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.invoke()
            }
            .setNegativeButton("关闭", null)
            .create()
        optionsDialog?.show()
    }

    // ==================== 互动操作 ====================

    private fun doLike() {
        val v = video ?: return
        lifecycleScope.launch {
            repository.likeVideo(v.id).onSuccess { r ->
                liked = r.liked
                Toast.makeText(this@PlayerActivity,
                    if (r.liked) "已点赞" else "已取消点赞", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@PlayerActivity, "操作失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun doCoin() {
        val v = video ?: return
        if (coined >= 2) {
            Toast.makeText(this, "这个视频已经投喂过啦", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            repository.coinVideo(v.id, 1).onSuccess { r ->
                coined = r.coined
                Toast.makeText(this@PlayerActivity,
                    "投喂成功！剩余小鱼干 ${r.coins}", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@PlayerActivity, "投喂失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun doFavorite() {
        val v = video ?: return
        if (faved) {
            Toast.makeText(this, "已收藏，可在「我的收藏」中管理", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            repository.favoriteVideo(v.id).onSuccess {
                faved = true
                Toast.makeText(this@PlayerActivity, "已收藏", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@PlayerActivity, "收藏失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun doFollowUp(uid: Long) {
        lifecycleScope.launch {
            repository.toggleFollowUser(uid).onSuccess { r ->
                followingUp = r.following
                Toast.makeText(this@PlayerActivity,
                    if (r.following) "已关注" else "已取消关注", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@PlayerActivity, "操作失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openRelated() {
        val v = video ?: return
        startActivity(
            Intent(this, VideoListActivity::class.java)
                .putExtra(VideoListActivity.EXTRA_TYPE, VideoListActivity.TYPE_RELATED)
                .putExtra(VideoListActivity.EXTRA_VIDEO_ID, v.id)
        )
    }

    /** 拉取正在观看人数；showToast=true 时用 Toast 展示结果（菜单点击） */
    private fun refreshViewers(showToast: Boolean = false) {
        val v = video ?: return
        lifecycleScope.launch {
            repository.getViewers(v.id).onSuccess { count ->
                viewersCount = count
                if (showToast) {
                    Toast.makeText(this@PlayerActivity,
                        "当前 $count 人正在观看", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                if (showToast) {
                    Toast.makeText(this@PlayerActivity, "获取观看人数失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==================== 播放上报 ====================

    /**
     * 进入播放即上报一次播放量，之后每 15s 上报观看进度（登录时写入历史），
     * 每 30s 再补一次播放量上报（服务端按独立观众幂等计数）。
     */
    private fun startReporting() {
        stopReporting()
        val v = video ?: return
        reportJob = lifecycleScope.launch {
            repository.reportView(v.id, 0, 0)
            var tick = 0
            while (isActive) {
                delay(15_000)
                tick++
                val pos = player?.currentPosition ?: continue
                val dur = player?.duration?.takeIf { it > 0 } ?: 0
                if (AuthManager.isLoggedIn && dur > 0) {
                    repository.reportHistory(v.id, pos, dur)
                }
                if (tick % 2 == 0) {
                    repository.reportView(v.id, pos, dur)
                }
            }
        }
    }

    private fun stopReporting() {
        reportJob?.cancel()
        reportJob = null
    }

    /** 退出前终报一次观看进度，保证历史记录不丢 */
    private fun reportHistoryOnce() {
        val v = video ?: return
        if (!AuthManager.isLoggedIn) return
        val pos = player?.currentPosition ?: return
        val dur = player?.duration ?: 0
        if (pos <= 0 || dur <= 0) return
        lifecycleScope.launch { repository.reportHistory(v.id, pos, dur) }
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
     * 避免 PlayerView 的播放控制器吞掉菜单键/上键/下键
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_UP -> {
                    showOptionsDialog()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (feedMode) {
                        nextFeed()
                    } else {
                        playerView.showController()
                    }
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
        stopReporting()
        reportHistoryOnce()
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回后应用最新弹幕设置
        danmakuView.applySettings()
        player?.play()
        danmakuView.setPlaying(true)
        startProgressUpdate()
        // onPause 停了上报协程，这里恢复（协程内部幂等，重复启动会先取消旧的）
        if (video != null) startReporting()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdate()
        stopReporting()
        danmakuView.setPlaying(false)
        optionsDialog?.dismiss()
        optionsDialog = null
        player?.release()
        player = null
    }
}
