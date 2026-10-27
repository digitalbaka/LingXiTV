package com.lingxi.tv.data.repository

import com.google.gson.reflect.TypeToken
import com.lingxi.tv.data.api.ApiClient
import com.lingxi.tv.data.model.ActionResult
import com.lingxi.tv.data.model.ApiResponse
import com.lingxi.tv.data.model.AdminComment
import com.lingxi.tv.data.model.AdminOverview
import com.lingxi.tv.data.model.AdminReport
import com.lingxi.tv.data.model.AdminUser
import com.lingxi.tv.data.model.AdminVideo
import com.lingxi.tv.data.model.Captcha
import com.lingxi.tv.data.model.CheckinResult
import com.lingxi.tv.data.model.Conversation
import com.lingxi.tv.data.model.LoginRequest
import com.lingxi.tv.data.model.SendCodeRequest
import com.lingxi.tv.data.model.UserProfile
import com.lingxi.tv.data.model.Verify2faRequest
import com.lingxi.tv.data.model.DanmakuList
import com.lingxi.tv.data.model.GatewayInfo
import com.lingxi.tv.data.model.LoginResult
import com.lingxi.tv.data.model.PageData
import com.lingxi.tv.data.model.Partition
import com.lingxi.tv.data.model.SearchResult
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.data.model.VideoDetail
import retrofit2.HttpException

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

    /**
     * 账号密码登录。
     * 站点强制两步验证：正确的账号密码会返回 HTTP 400，错误体 data 中携带
     * mfaRequired=true / mfaTicket / emailMasked —— 此时作为成功结果返回，
     * 由界面跳转两步验证流程。
     */
    suspend fun login(username: String, password: String): Result<LoginResult> {
        return try {
            val resp = api.login(LoginRequest(username, password))
            if (resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "登录失败" }))
        } catch (e: HttpException) {
            // HTTP 400 等错误：解析错误体，检测两步验证要求
            val data = parseErrorBody<LoginResult>(e)?.data
            if (data?.mfaRequired == true) {
                Result.success(data)
            } else {
                Result.failure(Exception(parseErrorBody<LoginResult>(e)?.message?.ifEmpty { null } ?: "登录失败（HTTP ${e.code()}）"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 发送邮箱验证码到绑定邮箱 */
    suspend fun sendEmailCode(mfaTicket: String?, captchaId: String, captchaCode: String): Result<ActionResult> {
        return try {
            val resp = api.sendEmailCode(SendCodeRequest(mfaTicket, captchaId, captchaCode))
            Result.success(resp.data ?: ActionResult(true, resp.message.ifEmpty { "已发送" }))
        } catch (e: HttpException) {
            Result.failure(Exception(parseErrorBody<ActionResult>(e)?.message ?: "发送失败（HTTP ${e.code()}）"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 验证邮箱验证码完成两步验证，返回 token */
    suspend fun verify2fa(mfaTicket: String, code: String, rememberDevice: Boolean): Result<LoginResult> {
        return try {
            val resp = api.verify2fa(Verify2faRequest(mfaTicket, code, rememberDevice))
            if (resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "验证失败" }))
        } catch (e: HttpException) {
            Result.failure(Exception(parseErrorBody<LoginResult>(e)?.message ?: "验证失败（HTTP ${e.code()}）"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 解析 HTTP 错误响应体为 ApiResponse 结构，提取服务器给出的 message/data */
    private inline fun <reified T> parseErrorBody(e: HttpException): ApiResponse<T>? {
        return try {
            val body = e.response()?.errorBody()?.string() ?: return null
            val type = object : TypeToken<ApiResponse<T>>() {}.type
            ApiClient.gson.fromJson<ApiResponse<T>>(body, type)
        } catch (_: Exception) {
            null
        }
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

    // ==================== 用户资料 ====================

    suspend fun getMyProfile(): Result<UserProfile> {
        return try {
            val resp = api.getMyProfile()
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取资料失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateMyProfile(nickname: String?, bio: String?): Result<UserProfile> {
        return try {
            val resp = api.updateMyProfile(nickname, bio)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "更新失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun checkin(): Result<CheckinResult> {
        return try {
            val resp = api.checkin()
            if (resp.isSuccess) Result.success(resp.data ?: CheckinResult(success = true))
            else Result.failure(Exception(resp.message.ifEmpty { "签到失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<ActionResult> {
        return try {
            val resp = api.changePassword(oldPassword, newPassword)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "修改密码失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getConversations(): Result<PageData<Conversation>> {
        return try {
            val resp = api.getConversations()
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取消息失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ==================== 管理员 ====================

    suspend fun getAdminOverview(): Result<AdminOverview> {
        return try {
            val resp = api.getAdminOverview()
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取总览失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getAdminVideos(status: String? = null, page: Int = 1): Result<PageData<AdminVideo>> {
        return try {
            val resp = api.getAdminVideos(status, page)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取稿件失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun reviewVideo(id: Long, action: String, reason: String? = null): Result<ActionResult> {
        return try {
            val resp = api.reviewVideo(id, action, reason)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "操作失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun pinVideo(id: Long): Result<ActionResult> {
        return try {
            val resp = api.pinVideo(id)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "操作失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getAdminComments(keyword: String? = null, page: Int = 1): Result<PageData<AdminComment>> {
        return try {
            val resp = api.getAdminComments(keyword, page)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取评论失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteComment(id: Long): Result<ActionResult> {
        return try {
            val resp = api.deleteComment(id)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "删除失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getAdminDanmaku(keyword: String? = null, page: Int = 1): Result<PageData<AdminComment>> {
        return try {
            val resp = api.getAdminDanmaku(keyword, page)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取弹幕失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deleteDanmaku(id: Long): Result<ActionResult> {
        return try {
            val resp = api.deleteDanmaku(id)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "删除失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getAdminReports(status: String? = null, page: Int = 1): Result<PageData<AdminReport>> {
        return try {
            val resp = api.getAdminReports(status, page)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取举报失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun handleReport(id: Long, action: String): Result<ActionResult> {
        return try {
            val resp = api.handleReport(id, action)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "操作失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getAdminUsers(keyword: String? = null, page: Int = 1): Result<PageData<AdminUser>> {
        return try {
            val resp = api.getAdminUsers(keyword, page)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取用户失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun banUser(id: Long): Result<ActionResult> {
        return try {
            val resp = api.banUser(id)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "操作失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun setUserRole(id: Long, role: String): Result<ActionResult> {
        return try {
            val resp = api.setUserRole(id, role)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "操作失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }
}
