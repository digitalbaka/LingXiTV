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
 * deviceToken 为上次「记住这台设备」拿到的令牌，仅对未绑定认证器的邮箱 2FA 账号生效
 */
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceToken: String? = null
)

/**
 * 站点双因素认证策略状态（twoFactor 对象）
 */
data class TwoFactorStatus(
    val enabled: Boolean = false,
    /** 站点是否强制全体绑定认证器 */
    val required: Boolean = false,
    /** 宽限期截止时间戳（毫秒，不适用时为 0） */
    val deadline: Long = 0,
    val graceDays: Int = 0,
    /** 宽限期剩余天数（向上取整，不适用/已过期为 0） */
    val daysLeft: Int = 0,
    /** 已过宽限期且未绑定认证器：业务接口会被 403 拦截 */
    val blocked: Boolean = false
)

/**
 * 登录响应
 * 直接登录成功：accessToken + refreshToken + user
 * 需要二次验证：mfaRequired=true、mfaMethod（totp/email）、mfaTicket、canUseRecovery
 */
data class LoginResult(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val deviceToken: String? = null,
    val user: User? = null,
    val twoFactor: TwoFactorStatus? = null,
    val mfaRequired: Boolean = false,
    val mfaMethod: String? = null,
    val mfaTicket: String? = null,
    val emailMasked: String? = null,
    val canUseRecovery: Boolean = false
)

/**
 * 发送邮箱验证码请求体
 * purpose=login 时用 ticket 而不是邮箱；图形验证码在这一步不消费
 */
data class SendCodeRequest(
    val purpose: String,
    val ticket: String,
    val captchaId: String,
    val captchaCode: String
)

/**
 * 两步验证请求体（站点字段名为 ticket，不是 mfaTicket）
 * useRecovery=true 时 code 按恢复码校验；TOTP 账号 rememberDevice 无效，但仍可照常传
 */
data class Verify2faRequest(
    val ticket: String,
    val code: String,
    val useRecovery: Boolean = false,
    val rememberDevice: Boolean = true
)

/**
 * GET /users/me/totp：绑定状态 + 站点强制策略 + 剩余恢复码数量
 */
data class TotpStatus(
    val enabled: Boolean = false,
    val required: Boolean = false,
    val deadline: Long = 0,
    val graceDays: Int = 0,
    val daysLeft: Int = 0,
    val blocked: Boolean = false,
    val recoveryRemaining: Int = 0
)

/** POST /users/me/totp/setup 请求体 */
data class TotpSetupRequest(
    val password: String
)

/** POST /users/me/totp/setup 响应：qr 是原始 SVG 源码，secret 供手输 */
data class TotpSetupResult(
    val secret: String = "",
    val uri: String = "",
    val qr: String = "",
    val digits: Int = 6,
    val period: Int = 30,
    val issuer: String = "",
    val account: String = ""
)

/** POST /users/me/totp/enable 请求体 */
data class TotpEnableRequest(
    val code: String
)

/** POST /users/me/totp/enable 响应：恢复码明文只出现这一次 */
data class TotpEnableResult(
    val enabled: Boolean = false,
    val codes: List<String> = emptyList(),
    val recoveryRemaining: Int = 0
)

/** POST /users/me/totp/disable 请求体：密码 + 当前有效验证码 */
data class TotpDisableRequest(
    val password: String,
    val code: String
)

data class TotpDisableResult(
    val enabled: Boolean = false
)

/**
 * 恢复码查询/生成响应
 * GET 只返回 remaining/email；POST 返回明文 codes
 */
data class RecoveryCodesResult(
    val remaining: Int = 0,
    val email: String = "",
    val codes: List<String> = emptyList()
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
