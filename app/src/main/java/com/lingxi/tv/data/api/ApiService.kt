package com.lingxi.tv.data.api

import com.lingxi.tv.data.model.ActionResult
import com.lingxi.tv.data.model.AdminComment
import com.lingxi.tv.data.model.AdminDanmaku
import com.lingxi.tv.data.model.AdminOverview
import com.lingxi.tv.data.model.AdminQueue
import com.lingxi.tv.data.model.AdminReport
import com.lingxi.tv.data.model.AdminUser
import com.lingxi.tv.data.model.ApiResponse
import com.lingxi.tv.data.model.BanRequest
import com.lingxi.tv.data.model.Captcha
import com.lingxi.tv.data.model.CheckinResult
import com.lingxi.tv.data.model.Conversation
import com.lingxi.tv.data.model.DanmakuList
import com.lingxi.tv.data.model.FavoriteFolder
import com.lingxi.tv.data.model.FavoriteRequest
import com.lingxi.tv.data.model.FavoriteResult
import com.lingxi.tv.data.model.GatewayInfo
import com.lingxi.tv.data.model.HandleReportRequest
import com.lingxi.tv.data.model.HandoffRequest
import com.lingxi.tv.data.model.HandoffResult
import com.lingxi.tv.data.model.HistoryItem
import com.lingxi.tv.data.model.LoginRequest
import com.lingxi.tv.data.model.LoginResult
import com.lingxi.tv.data.model.MeResponse
import com.lingxi.tv.data.model.PageData
import com.lingxi.tv.data.model.Partition
import com.lingxi.tv.data.model.PinRequest
import com.lingxi.tv.data.model.ReviewRequest
import com.lingxi.tv.data.model.RoleRequest
import com.lingxi.tv.data.model.SearchResult
import com.lingxi.tv.data.model.SendCodeRequest
import com.lingxi.tv.data.model.SpaceInfo
import com.lingxi.tv.data.model.Verify2faRequest
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.data.model.VideoDetail
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 凌汐弹幕网 API 服务
 * 端点与参数以线上网页端/手机端实际请求为准
 */
interface ApiService {

    companion object {
        const val BASE_URL = "https://lingxi.xiguastudio.top"
    }

    // ==================== 视频相关 ====================

    @GET("/api/v1/videos")
    suspend fun getVideos(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("sort") sort: String? = null,
        @Query("partition") partition: String? = null
    ): ApiResponse<PageData<Video>>

    @GET("/api/v1/videos/{id}")
    suspend fun getVideoDetail(@Path("id") id: Long): ApiResponse<VideoDetail>

    @GET("/api/v1/partitions")
    suspend fun getPartitions(): ApiResponse<PageData<Partition>>

    @GET("/api/v1/search")
    suspend fun search(
        @Query("q") keyword: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<SearchResult>

    @GET("/api/v1/danmaku")
    suspend fun getDanmaku(
        @Query("videoId") videoId: Long,
        @Query("segment") segment: Int? = null
    ): ApiResponse<DanmakuList>

    // ==================== 排行榜 ====================

    @GET("/api/v1/videos/ranking")
    suspend fun getRanking(): ApiResponse<PageData<Video>>

    // ==================== 认证相关 ====================

    /** 获取图形验证码（SVG） */
    @GET("/api/v1/captcha")
    suspend fun getCaptcha(): ApiResponse<Captcha>

    /** 账号密码登录，站点强制两步验证：400 响应体返回 mfaRequired/mfaTicket/emailMasked */
    @POST("/api/v1/auth/login")
    suspend fun login(
        @Body body: LoginRequest
    ): ApiResponse<LoginResult>

    /** 发送邮箱验证码到绑定邮箱（需图形验证码） */
    @POST("/api/v1/auth/send-code")
    suspend fun sendEmailCode(
        @Body body: SendCodeRequest
    ): ApiResponse<ActionResult>

    /** 验证邮箱验证码完成两步验证登录 */
    @POST("/api/v1/auth/verify-2fa")
    suspend fun verify2fa(
        @Body body: Verify2faRequest
    ): ApiResponse<LoginResult>

    /** 刷新 Token */
    @POST("/api/v1/auth/refresh")
    suspend fun refreshToken(): ApiResponse<LoginResult>

    /** 退出登录 */
    @POST("/api/v1/auth/logout")
    suspend fun logout(): ApiResponse<ActionResult>

    // ==================== 节点（线路）====================

    /** 获取可用节点列表及当前接入状态 */
    @GET("/api/v1/gateway/nodes")
    suspend fun getGatewayNodes(): ApiResponse<GatewayInfo>

    /** 探测节点延迟 */
    @GET("/api/v1/gateway/probe")
    suspend fun probeNode(@Query("nodeId") nodeId: Long): ApiResponse<Any>

    /**
     * 节点授权：把当前公网 IP 授权到目标节点。
     * 未调用时节点对任意请求返回 HTTP 200 + {code:40301}，播放器会表现为无限加载。
     * nodeId = 0 表示回到主站。
     */
    @POST("/api/v1/gateway/handoff")
    suspend fun handoff(@Body body: HandoffRequest): ApiResponse<HandoffResult>

    // ==================== 用户内容（需登录）====================

    /** 观看历史（items 为 {video, progressMs, ...} 嵌套结构） */
    @GET("/api/v1/history")
    suspend fun getHistory(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<PageData<HistoryItem>>

    /** 某个用户的收藏夹列表：GET /users/{uid}/folders */
    @GET("/api/v1/users/{uid}/folders")
    suspend fun getFavoriteFolders(
        @Path("uid") uid: Long
    ): ApiResponse<PageData<FavoriteFolder>>

    /** 收藏夹内的视频：GET /favorites/folders/{folderId}/items */
    @GET("/api/v1/favorites/folders/{folderId}/items")
    suspend fun getFavoriteFolderItems(
        @Path("folderId") folderId: Long,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 24
    ): ApiResponse<PageData<Video>>

    /** 关注的 UP 主的视频 */
    @GET("/api/v1/videos/following")
    suspend fun getFollowingVideos(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<PageData<Video>>

    /** 收藏视频：POST /videos/{id}/favorite body { folderId } */
    @POST("/api/v1/videos/{id}/favorite")
    suspend fun favoriteVideo(
        @Path("id") videoId: Long,
        @Body body: FavoriteRequest
    ): ApiResponse<FavoriteResult>

    /** 取消收藏：DELETE /favorites/items/{id}（{id} 为收藏记录 ID） */
    @DELETE("/api/v1/favorites/items/{id}")
    suspend fun unfavoriteVideo(@Path("id") id: Long): ApiResponse<ActionResult>

    /** 关注/取消关注 UP 主 */
    @POST("/api/v1/follow")
    suspend fun toggleFollow(
        @Query("uid") uid: Long
    ): ApiResponse<ActionResult>

    // ==================== 用户资料 ====================

    /** 当前用户完整资料：{ user, level } */
    @GET("/api/v1/users/me")
    suspend fun getMyProfile(): ApiResponse<MeResponse>

    /** 用户空间统计：{ user, level, videoCount, totalViews, joinedAt, isFollowing } */
    @GET("/api/v1/users/{id}")
    suspend fun getUserSpace(@Path("id") id: Long): ApiResponse<SpaceInfo>

    /** 用户作品列表（含视频/文章/音频）：GET /users/{uid}/videos?page=&size= */
    @GET("/api/v1/users/{uid}/videos")
    suspend fun getUserVideos(
        @Path("uid") uid: Long,
        @Query("page") page: Int,
        @Query("size") size: Int
    ): ApiResponse<PageData<Video>>

    /** 更新昵称与签名：PATCH /users/me body */
    @HTTP(method = "PATCH", path = "/api/v1/users/me", hasBody = true)
    suspend fun updateMyProfile(
        @Body body: Map<String, String>
    ): ApiResponse<MeResponse>

    /** 每日签到：{ reward, streak } */
    @POST("/api/v1/users/me/checkin")
    suspend fun checkin(): ApiResponse<CheckinResult>

    /** 修改密码 */
    @POST("/api/v1/users/me/password")
    suspend fun changePassword(
        @Query("oldPassword") oldPassword: String,
        @Query("newPassword") newPassword: String
    ): ApiResponse<ActionResult>

    /** 消息会话列表：{ items:[{user,last,unread,updatedAt}], totalUnread } */
    @GET("/api/v1/messages/conversations")
    suspend fun getConversations(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<PageData<Conversation>>

    // ==================== 管理员 ====================

    @GET("/api/v1/admin/overview")
    suspend fun getAdminOverview(): ApiResponse<AdminOverview>

    /** 稿件审核队列：GET /admin/queue（不是 /admin/videos） */
    @GET("/api/v1/admin/queue")
    suspend fun getAdminQueue(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<AdminQueue>

    /** 稿件审核：POST /videos/{id}/review body {approve,reason} */
    @POST("/api/v1/videos/{id}/review")
    suspend fun reviewVideo(
        @Path("id") id: Long,
        @Body body: ReviewRequest
    ): ApiResponse<ActionResult>

    /** 稿件置顶/取消置顶：POST /admin/videos/{id}/pin body {pinned} */
    @POST("/api/v1/admin/videos/{id}/pin")
    suspend fun pinVideo(
        @Path("id") id: Long,
        @Body body: PinRequest
    ): ApiResponse<ActionResult>

    /** 评论列表 */
    @GET("/api/v1/admin/comments")
    suspend fun getAdminComments(
        @Query("keyword") keyword: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<PageData<AdminComment>>

    /** 删除评论：DELETE /admin/comments/{id} */
    @DELETE("/api/v1/admin/comments/{id}")
    suspend fun deleteComment(@Path("id") id: Long): ApiResponse<ActionResult>

    /** 弹幕列表 */
    @GET("/api/v1/admin/danmaku")
    suspend fun getAdminDanmaku(
        @Query("keyword") keyword: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<PageData<AdminDanmaku>>

    /** 删除弹幕：DELETE /admin/danmaku/{id} */
    @DELETE("/api/v1/admin/danmaku/{id}")
    suspend fun deleteDanmaku(@Path("id") id: Long): ApiResponse<ActionResult>

    /** 举报列表 */
    @GET("/api/v1/admin/reports")
    suspend fun getAdminReports(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<PageData<AdminReport>>

    /** 处理举报：POST /admin/reports/{id}/handle body {action}，action ∈ delete/ignore/ban */
    @POST("/api/v1/admin/reports/{id}/handle")
    suspend fun handleReport(
        @Path("id") id: Long,
        @Body body: HandleReportRequest
    ): ApiResponse<ActionResult>

    /** 用户管理列表 */
    @GET("/api/v1/admin/users")
    suspend fun getAdminUsers(
        @Query("keyword") keyword: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<PageData<AdminUser>>

    /** 封禁/解封用户：POST /admin/users/{id}/ban body {banned} */
    @POST("/api/v1/admin/users/{id}/ban")
    suspend fun banUser(
        @Path("id") id: Long,
        @Body body: BanRequest
    ): ApiResponse<ActionResult>

    /** 设为管理员/取消管理员：POST /admin/users/{id}/role body {isAdmin} */
    @POST("/api/v1/admin/users/{id}/role")
    suspend fun setUserRole(
        @Path("id") id: Long,
        @Body body: RoleRequest
    ): ApiResponse<ActionResult>
}
