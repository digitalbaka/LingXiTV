package com.lingxi.tv.util

import android.content.Context
import android.util.LruCache

/**
 * 全局内存缓存 - 用于缓存视频封面等，避免重复加载
 * 限制大小，防止内存溢出
 */
object MemoryCache {

    private const val MAX_SIZE = 20 // 最多缓存 20 个视频详情

    private val videoDetailCache: LruCache<Long, Any> by lazy {
        LruCache(MAX_SIZE)
    }

    fun <T> get(key: Long): T? {
        @Suppress("UNCHECKED_CAST")
        return videoDetailCache.get(key) as? T
    }

    fun <T> put(key: Long, value: T) {
        videoDetailCache.put(key, value)
    }

    fun clear() {
        videoDetailCache.evictAll()
    }
}
