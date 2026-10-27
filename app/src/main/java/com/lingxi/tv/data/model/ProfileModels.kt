package com.lingxi.tv.data.model

/**
 * 我的页用户资料（GET /users/me），字段宽松解析
 */
data class UserProfile(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val email: String = "",
    val bio: String = "",
    val level: Int = 0,
    val exp: Long = 0,
    val nextLevelExp: Long = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val videoCount: Int = 0,
    val favoriteCount: Int = 0,
    val historyCount: Int = 0,
    val danmakuCount: Int = 0,
    val commentCount: Int = 0,
    val checkedIn: Boolean = false,
    val verified: Boolean = false,
    val verifyName: String = ""
) {
    fun getFullAvatarUrl(baseUrl: String): String =
        if (avatar.startsWith("http")) avatar else baseUrl + avatar
}

/**
 * 签到结果
 */
data class CheckinResult(
    val success: Boolean = false,
    val message: String = "",
    val exp: Long = 0,
    val checkedIn: Boolean = false
)

/**
 * 会话（消息中心）
 */
data class Conversation(
    val id: Long = 0,
    val userId: Long = 0,
    val nickname: String = "",
    val username: String = "",
    val avatar: String = "",
    val lastMessage: String = "",
    val unread: Int = 0,
    val updatedAt: String = ""
)

// ==================== 管理员 ====================

/**
 * 站务总览（GET /admin/overview），字段宽松解析
 */
data class AdminOverview(
    val pendingVideos: Int = 0,
    val pendingReports: Int = 0,
    val pendingVerifications: Int = 0,
    val pendingCreators: Int = 0,
    val totalUsers: Int = 0,
    val totalVideos: Int = 0,
    val todayUsers: Int = 0,
    val todayVideos: Int = 0,
    val todayDanmaku: Int = 0,
    val todayComments: Int = 0
)

/**
 * 待审稿件（宽松解析，兼容不同字段名）
 */
data class AdminVideo(
    val id: Long = 0,
    val title: String = "",
    val author: String = "",
    val uid: Long = 0,
    val partition: String = "",
    val duration: Int = 0,
    val status: String = "",
    val cover: String = "",
    val createdAt: String = ""
)

/**
 * 评论 / 弹幕（管理员）
 */
data class AdminComment(
    val id: Long = 0,
    val content: String = "",
    val nickname: String = "",
    val uid: Long = 0,
    val videoId: Long = 0,
    val videoTitle: String = "",
    val likes: Int = 0,
    val createdAt: String = ""
)

/**
 * 举报
 */
data class AdminReport(
    val id: Long = 0,
    val targetType: String = "",
    val targetId: Long = 0,
    val reason: String = "",
    val reporter: String = "",
    val status: String = "",
    val createdAt: String = ""
)

/**
 * 用户管理条目
 */
data class AdminUser(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val level: Int = 0,
    val exp: Long = 0,
    val videoCount: Int = 0,
    val reportCount: Int = 0,
    val role: String = "",
    val banned: Boolean = false,
    val avatar: String = ""
)
