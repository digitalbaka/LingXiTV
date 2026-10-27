package com.lingxi.tv.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * 视频磁盘缓存管理器（全局单例）
 * 使用 SimpleCache + LRU 淘汰策略，将视频数据持久缓存到应用私有目录，
 * 重复播放/循环播放/重试时直接读本地，不再重新下载。
 *
 * 注意：SimpleCache 会对缓存目录加文件锁，全局只允许一个实例，因此必须单例。
 */
object VideoCacheManager {

    /** 可选缓存上限（MB），用户在设置页选择 */
    val CACHE_LIMIT_OPTIONS_MB = intArrayOf(50, 100, 150, 200)
    private const val DEFAULT_CACHE_LIMIT_MB = 200
    private const val PREFS_NAME = "lingxi_cache"
    private const val KEY_LIMIT_MB = "cache_limit_mb"

    @Volatile
    private var simpleCache: SimpleCache? = null

    /** 当前缓存上限（字节） */
    fun getCacheLimitBytes(context: Context): Long {
        val mb = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LIMIT_MB, DEFAULT_CACHE_LIMIT_MB)
        return mb * 1024L * 1024L
    }

    fun getCacheLimitMb(context: Context): Int =
        (getCacheLimitBytes(context) / 1024 / 1024).toInt()

    /**
     * 设置缓存上限（MB）。保存后释放旧缓存实例，下次使用按新上限重建
     * （SimpleCache 的 LRU 淘汰器会自动把存量缓存修剪到新上限以内）
     */
    fun setCacheLimitMb(context: Context, mb: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LIMIT_MB, mb).apply()
        synchronized(this) {
            try {
                simpleCache?.release()
            } catch (_: Exception) {
            }
            simpleCache = null
        }
    }

    /**
     * 启动时自动清理：缓存目录超出用户选择的上限则整体删除。
     * 必须在任何 SimpleCache 实例创建之前调用（Application.onCreate）。
     */
    fun trimIfOverLimit(context: Context) {
        try {
            val dir = File(context.cacheDir, "video_cache")
            if (!dir.exists()) return
            val limit = getCacheLimitBytes(context)
            val size = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            if (size > limit) {
                dir.deleteRecursively()
            }
        } catch (_: Exception) {
        }
    }

    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: SimpleCache(
                File(context.cacheDir, "video_cache"),
                LeastRecentlyUsedCacheEvictor(getCacheLimitBytes(context)),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { simpleCache = it }
        }
    }

    /**
     * 包装了磁盘缓存的数据源工厂。
     * FLAG_IGNORE_CACHE_ON_ERROR：缓存读取失败时自动回退网络，保证可用性。
     *
     * defaultHeaders：对所有上游请求附加的默认请求头。直连节点场景需要
     * X-Forwarded-For（节点靠它识别 handoff 授权的公网 IP）。
     */
    fun cacheDataSourceFactory(
        context: Context,
        defaultHeaders: Map<String, String> = emptyMap()
    ): CacheDataSource.Factory {
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent("LingXiTV/1.0")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        if (defaultHeaders.isNotEmpty()) {
            upstream.setDefaultRequestProperties(defaultHeaders)
        }
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** 当前缓存大小（字节），未初始化返回 0 */
    fun getCacheSizeBytes(context: Context): Long {
        return try {
            getCache(context).cacheSpace
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 清空视频磁盘缓存。必须在无播放器使用时调用（设置页中调用安全）。
     * 释放并重建 SimpleCache 以重新加目录锁。
     */
    fun clearCache(context: Context) {
        synchronized(this) {
            try {
                simpleCache?.release()
            } catch (_: Exception) {
            }
            simpleCache = null
            val dir = File(context.cacheDir, "video_cache")
            dir.deleteRecursively()
        }
    }
}
