package com.lingxi.tv.data.repository

import com.google.gson.reflect.TypeToken
import com.lingxi.tv.data.api.ApiClient
import com.lingxi.tv.data.auth.AuthManager
import com.lingxi.tv.data.model.ActionResult
import com.lingxi.tv.data.model.ApiResponse
import com.lingxi.tv.data.model.AdminComment
import com.lingxi.tv.data.model.AdminDanmaku
import com.lingxi.tv.data.model.AdminOverview
import com.lingxi.tv.data.model.AdminReport
import com.lingxi.tv.data.model.AdminUser
import com.lingxi.tv.data.model.AdminVideo
import com.lingxi.tv.data.model.Captcha
import com.lingxi.tv.data.model.CheckinResult
import com.lingxi.tv.data.model.Conversation
import com.lingxi.tv.data.model.LoginRequest
import com.lingxi.tv.data.model.MeLevel
import com.lingxi.tv.data.model.MeUser
import com.lingxi.tv.data.model.SendCodeRequest
import com.lingxi.tv.data.model.UserProfile
import com.lingxi.tv.data.model.Verify2faRequest
import com.lingxi.tv.data.model.DanmakuList
import com.lingxi.tv.data.model.FavoriteRequest
import com.lingxi.tv.data.model.FavoriteResult
import com.lingxi.tv.data.model.GatewayInfo
import com.lingxi.tv.data.model.HandleReportRequest
import com.lingxi.tv.data.model.HandoffRequest
import com.lingxi.tv.data.model.HandoffResult
import com.lingxi.tv.data.model.LoginResult
import com.lingxi.tv.data.model.PageData
import com.lingxi.tv.data.model.Partition
import com.lingxi.tv.data.model.PinRequest
import com.lingxi.tv.data.model.ReviewRequest
import com.lingxi.tv.data.model.RoleRequest
import com.lingxi.tv.data.model.SearchResult
import com.lingxi.tv.data.model.SpaceInfo
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.data.model.VideoDetail
import com.lingxi.tv.data.model.BanRequest
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

    /** 用户空间信息（资料 + 统计） */
    suspend fun getUserSpaceInfo(uid: Long): Result<SpaceInfo> {
        return try {
            val resp = api.getUserSpace(uid)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取用户信息失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    /** 用户作品分页列表（含视频/文章/音频） */
    suspend fun getUserVideos(uid: Long, page: Int, size: Int = 20): Result<PageData<Video>> {
        return try {
            val resp = api.getUserVideos(uid, page, size)
            if (resp.isSuccess && resp.data != null) Result.success(resp.data)
            else Result.failure(Exception(resp.message.ifEmpty { "获取作品列表失败" }))
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
            val code = e.code()
            val data = parseErrorBody<LoginResult>(e)
            // HTTP 400 + mfaRequired → 两步验证（作为成功结果）
            if (data?.data?.mfaRequired == true) {
                Result.success(data.data)
            } else if (code in 500..599) {
                Result.failure(Exception("服务器暂时不可用（HTTP $code），请稍后重试"))
            } else if (code == 400 && data?.message != null) {
                Result.failure(Exception(data.message))
            } else if (code == 401) {
                Result.failure(Exception("登录已过期，请重新登录"))
            } else {
                Result.failure(Exception(data?.message?.ifEmpty { null } ?: "登录失败（HTTP $code）"))
            }
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("网络超时，请检查网络后重试"))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("网络连接失败，请检查网络"))
        } catch (e: Exception) {
            Result.failure(Exception("登录失败：${e.message}"))
        }
    }

    /** 发送邮箱验证码到绑定邮箱（登录二次验证：purpose=login + ticket） */
    suspend fun sendEmailCode(mfaTicket: String?, captchaId: String, captchaCode: String): Result<ActionResult> {
        return try {
            val resp = api.sendEmailCode(
                SendCodeRequest(
                    purpose = "login",
                    ticket = mfaTicket.orEmpty(),
                    captchaId = captchaId,
                    captchaCode = captchaCode
                )
            )
            Result.success(resp.data ?: ActionResult(true, resp.message.ifEmpty { "已发送" }))
        } catch (e: HttpException) {
            val code = e.code()
            val msg = parseErrorBody<ActionResult>(e)?.message
            if (code in 500..599) Result.failure(Exception("服务器暂时不可用（HTTP $code），请稍后重试"))
            else Result.failure(Exception(msg?.ifEmpty { null } ?: "发送失败（HTTP $code）"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("网络超时，请检查网络后重试"))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("网络连接失败，请检查网络"))
        } catch (e: Exception) {
            Result.failure(Exception("发送失败：${e.message}"))
        }
    }

    /** 验证邮箱验证码完成两步验证，返回 accessToken */
    suspend fun verify2fa(mfaTicket: String, code: String, rememberDevice: Boolean): Result<LoginResult> {
        return try {
            val resp = api.verify2fa(
                Verify2faRequest(
                    ticket = mfaTicket,
                    code = code,
                    useRecovery = false,
                    rememberDevice = rememberDevice
                )
            )
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

    /**
     * 节点授权。必须调用后才能从该节点拉流，否则节点返回 40301 JSON，
     * 播放器会一直缓冲无法播放。nodeId = 0 表示回主站。
     */
    suspend fun handoff(nodeId: Long): Result<HandoffResult> {
        return try {
            val resp = api.handoff(HandoffRequest(nodeId))
            if (resp.isSuccess) Result.success(resp.data ?: HandoffResult(nodeId))
            else Result.failure(Exception(resp.message.ifEmpty { "节点授权失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    // ==================== 用户内容 ====================

    suspend fun getHistory(page: Int = 1, pageSize: Int = 20): Result<PageData<Video>> {
        return try {
            val resp = api.getHistory(page, pageSize)
            val data = resp.data
            if (resp.isSuccess && data != null) {
                // 历史接口 items 是 {video, progressMs, ...} 嵌套结构，取出 video 字段
                Result.success(
                    PageData(
                        items = data.items.mapNotNull { it.video },
                        total = data.total,
                        page = data.page,
                        pageSize = data.pageSize,
                        hasMore = data.hasMore
                    )
                )
            } else Result.failure(Exception(resp.message.ifEmpty { "获取历史记录失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    /**
     * 获取收藏视频。
     * 服务端没有 /favorites 列表接口：先取当前用户的收藏夹，
     * 优先默认收藏夹，再取该收藏夹内的视频。
     */
    suspend fun getFavorites(page: Int = 1, pageSize: Int = 20): Result<PageData<Video>> {
        return try {
            val uid = AuthManager.user?.id
            if (uid == null || uid <= 0) {
                return Result.failure(Exception("请先登录"))
            }
            val foldersResp = api.getFavoriteFolders(uid)
            val folders = foldersResp.data?.items.orEmpty()
            val folder = folders.firstOrNull { it.isDefault } ?: folders.firstOrNull()
                ?: return Result.success(PageData(emptyList(), 0, page, pageSize, false))
            val resp = api.getFavoriteFolderItems(folder.id, page, pageSize)
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

    /** 收藏到默认收藏夹 */
    suspend fun favoriteVideo(videoId: Long): Result<FavoriteResult> {
        return try {
            val uid = AuthManager.user?.id ?: return Result.failure(Exception("请先登录"))
            val folders = api.getFavoriteFolders(uid).data?.items.orEmpty()
            val folder = folders.firstOrNull { it.isDefault } ?: folders.firstOrNull()
                ?: return Result.failure(Exception("没有可用的收藏夹"))
            val resp = api.favoriteVideo(videoId, FavoriteRequest(folder.id))
            if (resp.isSuccess) Result.success(resp.data ?: FavoriteResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "收藏失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    /** 取消收藏（id 为收藏记录 ID） */
    suspend fun unfavoriteVideo(favoriteItemId: Long): Result<ActionResult> {
        return try {
            val resp = api.unfavoriteVideo(favoriteItemId)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "取消收藏失败" }))
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

    /**
     * 当前用户资料：/users/me 只返回 {user,level}，
     * 稿件数/收藏数/历史数分别从 /users/{id}、收藏夹、历史列表补齐。
     */
    suspend fun getMyProfile(): Result<UserProfile> {
        return try {
            val resp = api.getMyProfile()
            val me = resp.data
            val u = me?.user
            if (!resp.isSuccess || me == null || u == null) {
                return Result.failure(Exception(resp.message.ifEmpty { "获取资料失败" }))
            }
            var videoCount = 0
            var favoriteCount = 0
            var historyCount = 0
            try {
                videoCount = api.getUserSpace(u.id).data?.videoCount ?: 0
            } catch (_: Exception) {
            }
            try {
                favoriteCount = api.getFavoriteFolders(u.id).data?.items?.sumOf { it.count } ?: 0
            } catch (_: Exception) {
            }
            try {
                historyCount = api.getHistory(1, 1).data?.total ?: 0
            } catch (_: Exception) {
            }
            Result.success(u.toProfile(me.level, videoCount, favoriteCount, historyCount))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun updateMyProfile(nickname: String?, bio: String?): Result<UserProfile> {
        return try {
            val body = mutableMapOf<String, String>()
            nickname?.let { body["nickname"] = it }
            bio?.let { body["bio"] = it }
            if (body.isEmpty()) return Result.failure(Exception("没有需要保存的内容"))
            val resp = api.updateMyProfile(body)
            val me = resp.data
            val u = me?.user
            if (resp.isSuccess && me != null && u != null) {
                Result.success(u.toProfile(me.level, 0, 0, 0))
            } else {
                Result.failure(Exception(resp.message.ifEmpty { "更新失败" }))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun MeUser.toProfile(
        level: MeLevel?,
        videoCount: Int,
        favoriteCount: Int,
        historyCount: Int
    ): UserProfile {
        return UserProfile(
            id = id,
            username = username,
            nickname = nickname,
            avatar = avatar,
            bio = bio,
            level = level?.level ?: this.level,
            exp = level?.current ?: exp,
            nextLevelExp = level?.next ?: 0,
            expPercent = level?.percent ?: 0,
            followerCount = followerCount,
            followingCount = followingCount,
            videoCount = videoCount,
            favoriteCount = favoriteCount,
            historyCount = historyCount,
            checkedIn = checkedInToday,
            badgeLabel = badgeLabel,
            isAdmin = isAdmin
        )
    }

    suspend fun checkin(): Result<CheckinResult> {
        return try {
            val resp = api.checkin()
            if (resp.isSuccess) Result.success(resp.data ?: CheckinResult(success = true))
            else Result.failure(Exception(resp.message.ifEmpty { "签到失败" }))
        } catch (e: HttpException) {
            Result.failure(Exception(parseErrorBody<ActionResult>(e)?.message ?: "签到失败（HTTP ${e.code()}）"))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun changePassword(oldPassword: String, newPassword: String): Result<ActionResult> {
        return try {
            val resp = api.changePassword(oldPassword, newPassword)
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "修改密码失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getConversations(page: Int = 1, pageSize: Int = 20): Result<PageData<Conversation>> {
        return try {
            val resp = api.getConversations(page, pageSize)
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

    /** 审核队列，转换为分页结构供列表使用 */
    suspend fun getAdminVideos(status: String? = null, page: Int = 1): Result<PageData<AdminVideo>> {
        return try {
            val resp = api.getAdminQueue(status, page)
            if (resp.isSuccess && resp.data != null) {
                val q = resp.data
                Result.success(
                    PageData(
                        items = q.items,
                        total = q.total,
                        page = q.page,
                        pageSize = 20,
                        hasMore = q.hasMore
                    )
                )
            } else Result.failure(Exception(resp.message.ifEmpty { "获取稿件失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun reviewVideo(id: Long, approve: Boolean, reason: String? = null): Result<ActionResult> {
        return try {
            val resp = api.reviewVideo(id, ReviewRequest(approve, reason))
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "操作失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun pinVideo(id: Long, pinned: Boolean): Result<ActionResult> {
        return try {
            val resp = api.pinVideo(id, PinRequest(pinned))
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

    suspend fun getAdminDanmaku(keyword: String? = null, page: Int = 1): Result<PageData<AdminDanmaku>> {
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
            val resp = api.handleReport(id, HandleReportRequest(action))
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

    suspend fun banUser(id: Long, banned: Boolean): Result<ActionResult> {
        return try {
            val resp = api.banUser(id, BanRequest(banned))
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "操作失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun setUserRole(id: Long, isAdmin: Boolean): Result<ActionResult> {
        return try {
            val resp = api.setUserRole(id, RoleRequest(isAdmin))
            if (resp.isSuccess) Result.success(resp.data ?: ActionResult(true))
            else Result.failure(Exception(resp.message.ifEmpty { "操作失败" }))
        } catch (e: Exception) { Result.failure(e) }
    }
}
