package com.lingxi.tv.data.model

// ==================== 我的资料 ====================

/**
 * GET /users/me 真实响应：{ user, level }
 */
data class MeResponse(
    val user: MeUser? = null,
    val level: MeLevel? = null
)

/**
 * /users/me 里的用户对象
 */
data class MeUser(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val banner: String = "",
    val bio: String = "",
    val gender: String = "",
    val level: Int = 0,
    val exp: Long = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val likeReceived: Long = 0,
    val viewReceived: Long = 0,
    val isAdmin: Boolean = false,
    val badgeType: String = "",
    val badgeLabel: String = "",
    val banned: Boolean = false,
    val emailBound: Boolean = false,
    val vip: Boolean = false,
    val vipTierLabel: String = "",
    val checkedInToday: Boolean = false,
    /** 注册时间（毫秒时间戳），用于计算加入天数 */
    val createdAt: Long = 0
) {
    fun getFullAvatarUrl(baseUrl: String): String = joinUrl(baseUrl, avatar)
}

/**
 * 等级/经验进度
 */
data class MeLevel(
    val level: Int = 0,
    val exp: Long = 0,
    val current: Long = 0,
    val next: Long = 0,
    val maxed: Boolean = false,
    val percent: Int = 0
)

/**
 * GET /users/{id} 响应里的空间统计
 */
data class SpaceInfo(
    val user: MeUser? = null,
    val level: MeLevel? = null,
    val videoCount: Int = 0,
    val totalViews: Long = 0,
    val joinedAt: String = "",
    val isFollowing: Boolean = false
)

/**
 * 我的页聚合资料（UI 直接使用）
 */
data class UserProfile(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val bio: String = "",
    val level: Int = 0,
    val exp: Long = 0,
    val nextLevelExp: Long = 0,
    val expPercent: Int = 0,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val videoCount: Int = 0,
    val favoriteCount: Int = 0,
    val historyCount: Int = 0,
    val checkedIn: Boolean = false,
    val badgeLabel: String = "",
    val isAdmin: Boolean = false
) {
    fun getFullAvatarUrl(baseUrl: String): String = joinUrl(baseUrl, avatar)
}

/** 拼接资源地址：空串返回空，绝对地址原样返回，相对地址补全 baseUrl */
internal fun joinUrl(baseUrl: String, path: String): String {
    if (path.isEmpty()) return ""
    if (path.startsWith("http")) return path
    return if (path.startsWith("/")) baseUrl + path else "$baseUrl/$path"
}

/**
 * 签到结果：POST /users/me/checkin → { reward, streak }
 */
data class CheckinResult(
    val reward: Long = 0,
    val streak: Int = 0,
    val success: Boolean = true,
    val message: String = ""
)

// ==================== 收藏夹 ====================

/**
 * 收藏夹：GET /users/{uid}/folders
 */
data class FavoriteFolder(
    val id: Long = 0,
    val name: String = "",
    val isDefault: Boolean = false,
    val isPublic: Boolean = false,
    val count: Int = 0
)

data class FavoriteRequest(
    val folderId: Long
)

data class FavoriteResult(
    val faved: Boolean = false
)

// ==================== 消息中心 ====================

/**
 * 会话：GET /messages/conversations
 * { user: {id,nickname,avatar,...}, updatedAt, last: {content,...}, unread }
 */
data class Conversation(
    val user: UserInfo? = null,
    val updatedAt: String = "",
    val last: ConversationLast? = null,
    val unread: Int = 0
)

data class ConversationLast(
    val id: Long = 0,
    val mine: Boolean = false,
    val content: String = "",
    val createdAt: String = ""
)

// ==================== 管理员 ====================

/**
 * 站务总览：GET /admin/overview → { totals: {...}, recentUsers, recentVideos }
 */
data class AdminOverview(
    val totals: AdminTotals? = null
)

data class AdminTotals(
    val users: Int = 0,
    val videos: Int = 0,
    val pending: Int = 0,
    val danmaku: Int = 0,
    val comments: Int = 0,
    val follows: Int = 0,
    val views: Long = 0
)

/**
 * 稿件审核队列：GET /admin/queue
 */
data class AdminQueue(
    val items: List<AdminVideo> = emptyList(),
    val counts: AdminCounts? = null,
    val page: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false
)

data class AdminCounts(
    val reviewing: Int = 0,
    val published: Int = 0,
    val rejected: Int = 0
)

/**
 * 待审稿件
 */
data class AdminVideo(
    val id: Long = 0,
    val title: String = "",
    val cover: String = "",
    val duration: Long = 0,
    val status: Int = 0,
    val visibility: Int = 0,
    val pinned: Boolean = false,
    val description: String = "",
    val partition: String = "",
    val qualityLabel: String = "",
    val createdAt: String = "",
    val up: UserInfo? = null
) {
    val statusLabel: String
        get() = when (status) {
            0 -> "待审核"
            1 -> "已通过"
            2 -> "已驳回"
            else -> "状态 $status"
        }
}

/**
 * 管理后台评论：{id,content,author:{...},video:{id,title},isReply,createdAt}
 */
data class AdminComment(
    val id: Long = 0,
    val content: String = "",
    val author: UserInfo? = null,
    val video: AdminVideoRef? = null,
    val isReply: Boolean = false,
    val createdAt: String = ""
)

/**
 * 管理后台弹幕：{id,content,author,video,timeMs,createdAt}
 */
data class AdminDanmaku(
    val id: Long = 0,
    val content: String = "",
    val author: UserInfo? = null,
    val video: AdminVideoRef? = null,
    val timeMs: Long = 0,
    val createdAt: String = ""
)

data class AdminVideoRef(
    val id: Long = 0,
    val title: String = ""
)

/**
 * 举报：{id,reporter:{...},target:{id,videoId,label,title,content},targetType,reason,detail,status,createdAt}
 */
data class AdminReport(
    val id: Long = 0,
    val reporter: UserInfo? = null,
    val target: AdminReportTarget? = null,
    val targetType: String = "",
    val reason: String = "",
    val detail: String = "",
    val status: String = "",
    val createdAt: String = ""
)

data class AdminReportTarget(
    val id: Long = 0,
    val videoId: Long = 0,
    val label: String = "",
    val title: String = "",
    val content: String = ""
)

/**
 * 用户管理条目
 */
data class AdminUser(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val level: Int = 0,
    val exp: Long = 0,
    val coins: Long = 0,
    val videoCount: Int = 0,
    val followerCount: Int = 0,
    val reportedCount: Int = 0,
    val banned: Boolean = false,
    val isAdmin: Boolean = false
)

// ==================== 管理后台请求体 ====================

data class ReviewRequest(
    val approve: Boolean,
    val reason: String? = null
)

data class PinRequest(
    val pinned: Boolean
)

data class HandleReportRequest(
    val action: String
)

data class BanRequest(
    val banned: Boolean
)

data class RoleRequest(
    val isAdmin: Boolean
)
