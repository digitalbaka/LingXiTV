package com.lingxi.tv.data.api

import com.lingxi.tv.data.model.ActionResult
import com.lingxi.tv.data.model.ApiResponse
import com.lingxi.tv.data.model.Captcha
import com.lingxi.tv.data.model.DanmakuList
import com.lingxi.tv.data.model.GatewayInfo
import com.lingxi.tv.data.model.LoginResult
import com.lingxi.tv.data.model.PageData
import com.lingxi.tv.data.model.Partition
import com.lingxi.tv.data.model.SearchResult
import com.lingxi.tv.data.model.User
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.data.model.VideoDetail
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 凌汐弹幕网 API 服务
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

    /** 账号密码登录，可能返回 need2fa=true 需要二次验证 */
    @POST("/api/v1/auth/login")
    suspend fun login(
        @Query("username") username: String,
        @Query("password") password: String
    ): ApiResponse<LoginResult>

    /** 发送邮箱验证码（需图形验证码） */
    @POST("/api/v1/auth/send-code")
    suspend fun sendEmailCode(
        @Query("email") email: String? = null,
        @Query("captchaId") captchaId: String,
        @Query("captchaCode") captchaCode: String
    ): ApiResponse<ActionResult>

    /** 验证邮箱验证码（二次验证登录） */
    @POST("/api/v1/auth/verify-2fa")
    suspend fun verify2fa(
        @Query("code") code: String,
        @Query("remember") remember: Boolean = true
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

    // ==================== 用户内容（需登录）====================

    /** 观看历史 */
    @GET("/api/v1/history")
    suspend fun getHistory(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<PageData<Video>>

    /** 收藏夹列表 */
    @GET("/api/v1/favorites/folders")
    suspend fun getFavoriteFolders(): ApiResponse<PageData<Any>>

    /** 收藏的视频 */
    @GET("/api/v1/favorites")
    suspend fun getFavorites(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<PageData<Video>>

    /** 关注的 UP 主的视频 */
    @GET("/api/v1/videos/following")
    suspend fun getFollowingVideos(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<PageData<Video>>

    /** 收藏/取消收藏视频 */
    @POST("/api/v1/favorite")
    suspend fun toggleFavorite(
        @Query("videoId") videoId: Long
    ): ApiResponse<ActionResult>

    /** 关注/取消关注 UP 主 */
    @POST("/api/v1/follow")
    suspend fun toggleFollow(
        @Query("uid") uid: Long
    ): ApiResponse<ActionResult>
}
