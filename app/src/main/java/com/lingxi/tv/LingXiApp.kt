package com.lingxi.tv

import android.app.Application
import android.content.ComponentCallbacks2
import com.bumptech.glide.Glide
import com.lingxi.tv.data.repository.VideoRepository
import com.lingxi.tv.player.VideoCacheManager

/**
 * 凌汐弹幕网电视版 Application
 * 负责全局初始化，Repository 单例管理
 */
class LingXiApp : Application() {

    companion object {
        @Volatile
        private var instance: LingXiApp? = null
        fun get(): LingXiApp = instance!!
    }

    val repository by lazy { VideoRepository() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 启动自动清理：视频缓存超出用户设定的上限则清空（子线程，避免阻塞启动）
        Thread { VideoCacheManager.trimIfOverLimit(this) }.start()
    }

    /**
     * 内存优化：系统低内存时清理 Glide 图片缓存，避免 OOM
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Glide.get(this).clearMemory()
        }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            Thread {
                try {
                    Glide.get(this).clearDiskCache()
                } catch (_: Exception) {
                }
            }.start()
        }
    }
}
