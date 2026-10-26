package com.lingxi.tv.data.model

/**
 * 图形验证码
 */
data class Captcha(
    val id: String = "",
    val svg: String = ""
)

/**
 * 登录响应
 * 直接登录成功：token + user 非空，need2fa=false
 * 需要二次验证：need2fa=true，返回 maskedEmail 和临时 ticket
 */
data class LoginResult(
    val token: String? = null,
    val user: User? = null,
    val need2fa: Boolean = false,
    val maskedEmail: String? = null,
    val ticket: String? = null
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
    fun getFullAvatarUrl(baseUrl: String): String {
        return if (avatar.startsWith("http")) avatar else baseUrl + avatar
    }
}

/**
 * 通用操作响应（收藏、关注等）
 */
data class ActionResult(
    val success: Boolean = false,
    val message: String = ""
)
