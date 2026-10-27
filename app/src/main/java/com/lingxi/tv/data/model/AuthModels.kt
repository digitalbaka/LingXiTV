package com.lingxi.tv.data.model

/**
 * 图形验证码
 */
data class Captcha(
    val id: String = "",
    val svg: String = ""
)

/**
 * 登录请求体
 */
data class LoginRequest(
    val username: String,
    val password: String
)

/**
 * 登录响应
 * 直接登录成功：accessToken + refreshToken + user
 * 站点强制两步验证：data 中返回 mfaRequired=true、mfaTicket、emailMasked
 */
data class LoginResult(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val deviceToken: String? = null,
    val user: User? = null,
    val mfaRequired: Boolean = false,
    val mfaTicket: String? = null,
    val emailMasked: String? = null
)

/**
 * 发送邮箱验证码请求体
 * 站点接口要求：purpose 区分用途（login 走登录二次验证，此时必须带 ticket 而不是邮箱），
 * ticket 为账号密码登录返回的 mfaTicket
 */
data class SendCodeRequest(
    val purpose: String,
    val ticket: String,
    val captchaId: String,
    val captchaCode: String
)

/**
 * 两步验证请求体（站点字段名为 ticket，不是 mfaTicket）
 */
data class Verify2faRequest(
    val ticket: String,
    val code: String,
    val useRecovery: Boolean = false,
    val rememberDevice: Boolean
)

/**
 * 用户信息
 */
data class User(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val email: String = "",
    val level: Int = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val vip: Boolean = false
) {
    fun getFullAvatarUrl(baseUrl: String): String = joinUrl(baseUrl, avatar)
}

/**
 * 通用操作响应（收藏、关注等）
 */
data class ActionResult(
    val success: Boolean = false,
    val message: String = ""
)
