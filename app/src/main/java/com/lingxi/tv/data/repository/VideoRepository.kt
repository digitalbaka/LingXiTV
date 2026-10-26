package com.lingxi.tv.data.repository

import com.lingxi.tv.data.api.ApiClient
import com.lingxi.tv.data.model.ActionResult
import com.lingxi.tv.data.model.Captcha
import com.lingxi.tv.data.model.DanmakuList
import com.lingxi.tv.data.model.GatewayInfo
import com.lingxi.tv.data.model.LoginResult
import com.lingxi.tv.data.model.PageData
import com.lingxi.tv.data.model.Partition
import com.lingxi.tv.data.model.SearchResult
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.data.model.VideoDetail

/**
 * 视频数据仓库
 * 封装网络请求，提供简单的内存缓存减少重复请求
 */
class VideoRepository {

    private val api = ApiClient.apiService

    // 简单的内存缓存：分区列表变化不频繁，缓存一段时间
    @Volatile
    private var partitionsCache: PageData<Partition>? = null
    private var partitionsCacheTime: Long = 0
    private val partitionsCacheTtl = 5 * 60 * 1000L // 5 分钟

    suspend fun getVideos(
        page: Int = 1,
        pageSize: Int = 20,
        sort: String? = null,
        partition: String? = null
    ): Result<PageData<Video>> {
        return try {
            val resp = api.getVideos(page, pageSize, sort, partition)
            if (resp.isSuccess && resp.data != null) {
                Result.success(resp.data)
            } else {
                Result.failure(Exception(resp.message.ifEmpty { "获取视频列表失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getVideoDetail(id: Long): Result<VideoDetail> {
        return try {
            val resp = api.getVideoDetail(id)
            if (resp.isSuccess && resp.data != null) {
                Result.success(resp.data)
            } else {
                Result.failure(Exception(resp.message.ifEmpty { "获取视频详情失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPartitions(forceRefresh: Boolean = false): Result<PageData<Partition>> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && partitionsCache != null &&
            now - partitionsCacheTime < partitionsCacheTtl
        ) {
            return Result.success(partitionsCache!!)
        }
        return try {
            val resp = api.getPartitions()
            if (resp.isSuccess && resp.data != null) {
                partitionsCache = resp.data
                partitionsCacheTime = now
                Result.success(resp.data)
            } else {
                Result.failure(Exception(resp.message.ifEmpty { "获取分区失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun search(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<SearchResult> {
        return try {
            val resp = api.search(keyword, page, pageSize)
            if (resp.isSuccess && resp.data != null) {
                Result.success(resp.data)
            } else {
                Result.failure(Exception(resp.message.ifEmpty { "搜索失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDanmaku(videoId: Long): Result<DanmakuList> {
        return try {
            val resp = api.getDanmaku(videoId, null)
            if (resp.isSuccess && resp.data != null) {
                Result.success(resp.data)
            } else {
                Result.failure(Exception(resp.message.ifEmpty { "获取弹幕失败" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 排行榜 ====================

    suspend fun getRanking(): Result<PageData<Video>> {
        return try {
            val resp = api.getRanking()
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取排行榜失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ==================== 认证 ====================

    suspend fun getCaptcha(): Result<Captcha> {
        return try {
            val resp = api.getCaptcha()
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取验证码失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun login(username: String, password: String): Result<LoginResult> {
        return try {
            val resp = api.login(username, password)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "登录失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun sendEmailCode(email: String?, captchaId: String, captchaCode: String): Result<ActionResult> {
        return try {
            val resp = api.sendEmailCode(email, captchaId, captchaCode)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true, "已发送"))
            else Result.failure(Exception(resp.message.ifEmpty { "发送失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun verify2fa(code: String, remember: Boolean = true): Result<LoginResult> {
        return try {
            val resp = api.verify2fa(code, remember)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "验证失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun logout(): Result<ActionResult> {
        return try {
            val resp = api.logout()
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "退出失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ==================== 节点 ====================

    suspend fun getGatewayNodes(): Result<GatewayInfo> {
        return try {
            val resp = api.getGatewayNodes()
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取节点失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ==================== 用户内容 ====================

    suspend fun getHistory(page: Int = 1, pageSize: Int = 20): Result<PageData<Video>> {
        return try {
            val resp = api.getHistory(page, pageSize)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取历史记录失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getFavorites(page: Int = 1, pageSize: Int = 20): Result<PageData<Video>> {
        return try {
            val resp = api.getFavorites(page, pageSize)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取收藏失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getFollowingVideos(page: Int = 1, pageSize: Int = 20): Result<PageData<Video>> {
        return try {
            val resp = api.getFollowingVideos(page, pageSize)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取关注动态失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleFavorite(videoId: Long): Result<ActionResult> {
        return try {
            val resp = api.toggleFavorite(videoId)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "操作失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun toggleFollow(uid: Long): Result<ActionResult> {
        return try {
            val resp = api.toggleFollow(uid)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "操作失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }
}
