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

    /** 缓存上限 512MB，超出后按最近最少使用淘汰 */
    private const val MAX_CACHE_BYTES = 512L * 1024 * 1024

    @Volatile
    private var simpleCache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        return simpleCache ?: synchronized(this) {
            simpleCache ?: SimpleCache(
                File(context.cacheDir, "video_cache"),
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { simpleCache = it }
        }
    }

    /**
     * 包装了磁盘缓存的数据源工厂。
     * FLAG_IGNORE_CACHE_ON_ERROR：缓存读取失败时自动回退网络，保证可用性。
     */
    fun cacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent("LingXiTV/1.0")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
