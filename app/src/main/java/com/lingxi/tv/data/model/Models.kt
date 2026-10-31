package com.lingxi.tv.data.model

/**
 * 通用 API 响应包装
 */
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null
) {
    val isSuccess: Boolean get() = code == 0
}

/**
 * 分页数据包装
 */
data class PageData<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val hasMore: Boolean = false
)

/**
 * 观看历史条目：GET /history 的 items 是 { video, progressMs, durationMs, percent, updatedAt }
 * 嵌套结构，其中 video 是精简字段（无 src/qualityOptions，播放时需重新取详情）
 */
data class HistoryItem(
    val video: Video? = null,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val percent: Int = 0,
    val updatedAt: Long = 0
)

/**
 * 视频模型
 */
data class Video(
    val id: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val description: String = "",
    /** 文章正文（Markdown，kind=article 时才有内容，与简介 description 是两个字段） */
    val content: String = "",
    val cover: String = "",
    val src: String = "",
    val duration: Long = 0,
    val partition: String = "",
    val tags: List<String> = emptyList(),
    val viewCount: Int = 0,
    val likeCount: Int = 0,
    val coinCount: Int = 0,
    val favCount: Int = 0,
    val danmakuCount: Int = 0,
    val commentCount: Int = 0,
    val status: Int = 0,
    val kind: String = "video",
    val watermarkUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val quality: String = "",
    val qualityLabel: String = "",
    val qualityTier: Int = 0,
    val ultraHd: Boolean = false,
    val highFrameRate: Boolean = false,
    val qualityOptions: List<VideoQuality> = emptyList(),
    val qualityBusy: Boolean = false,
    val freeMaxHeight: Int = 1080,
    val previewPercent: Int = 25,
    val previewGated: Boolean = false,
    val contentLabels: List<String> = emptyList(),
    val visibility: Int = 0,
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val publishedAt: Long = 0,
    val up: UserInfo? = null,
    val viewer: ViewerInfo? = null
) {
    val isArticle: Boolean get() = kind == "article"
    val isAudio: Boolean get() = kind == "audio"
    val isVideo: Boolean get() = kind == "video"

    fun getFullCoverUrl(baseUrl: String): String = joinUrl(baseUrl, cover)

    fun getFullSrcUrl(baseUrl: String): String = joinUrl(baseUrl, src)
}

/**
 * 画质选项
 */
data class VideoQuality(
    val key: String = "",
    val label: String = "",
    val height: Int = 0,
    val fps: Int = 0,
    val url: String = ""
) {
    fun getFullUrl(baseUrl: String): String = joinUrl(baseUrl, url)
}

/**
 * UP主信息
 */
data class UserInfo(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val level: Int = 0,
    val followerCount: Int = 0,
    val badgeType: String = "",
    val badgeLabel: String = "",
    val vip: Boolean = false,
    val vipTier: String = "",
    val vipTierLabel: String = ""
) {
    fun getFullAvatarUrl(baseUrl: String): String = joinUrl(baseUrl, avatar)
}

/**
 * 用户展示名：昵称优先，其次用户名，都为空时用兜底文案
 */
fun UserInfo?.displayName(fallback: String = "匿名"): String =
    this?.nickname?.takeIf { it.isNotBlank() }
        ?: this?.username?.takeIf { it.isNotBlank() }
        ?: fallback

/**
 * 观看者信息
 */
data class ViewerInfo(
    val liked: Boolean = false,
    val coined: Int = 0,
    val faved: Boolean = false,
    val folderId: Int? = null
)

/**
 * 视频详情响应
 */
data class VideoDetail(
    val video: Video? = null,
    val following: Boolean = false,
    /**
     * 观看进度。未登录时为 null；已登录时服务端返回的是对象而不是数字，
     * 若声明为 Long 会让整个详情响应解析失败（Expected a long but was BEGIN_OBJECT），
     * 表现为任何视频都提示"加载失败"。这里用 Any 同时兼容数字与对象。
     */
    val progress: Any? = null
)

// ==================== 互动（点赞/投币/关注） ====================

/** 仅含 items 的列表响应（置顶、相关视频等不分页接口） */
data class ItemList<T>(
    val items: List<T> = emptyList()
)

/** 点赞结果：POST /videos/{id}/like → { liked, likeCount } */
data class LikeResult(
    val liked: Boolean = false,
    val likeCount: Int = 0
)

/** 投币请求：POST /videos/{id}/coin，单稿上限 2 条 */
data class CoinRequest(val amount: Int)

/** 投币结果：{ coined 我已投数量, coinCount 稿件总投币, coins 我的余额 } */
data class CoinResult(
    val coined: Int = 0,
    val coinCount: Int = 0,
    val coins: Long = 0
)

/** 关注结果：POST /users/{id}/follow */
data class FollowResult(
    val following: Boolean = false
)

// ==================== 播放上报 ====================

/** 正在观看人数：GET /videos/{id}/viewers → { videoId, count } */
data class ViewersResult(
    val videoId: Long = 0,
    val count: Int = 0
)

/** 播放量上报：POST /videos/{id}/view（服务端按独立观众幂等计数） */
data class ViewReportRequest(
    val progressMs: Long,
    val durationMs: Long
)

data class ViewReportResult(
    val viewCount: Long = 0
)

/** 观看进度上报：POST /history */
data class HistoryReportRequest(
    val videoId: Long,
    val progressMs: Long,
    val durationMs: Long
)

// ==================== 站点状态 ====================

/** 站点概览：GET /stats → { online, watching, ... } */
data class SiteStats(
    val online: Int = 0,
    val watching: Int = 0
)

/** 在线心跳：POST /presence → { online, watching, ts } */
data class PresenceResult(
    val online: Int = 0,
    val watching: Int = 0
)

// ==================== 我的页扩展 ====================

/** 小鱼干余额与近期变动：GET /users/me/coins → { coins, level, recent } */
data class CoinsOverview(
    val coins: Long = 0,
    val recent: List<CoinLog> = emptyList()
)

data class CoinLog(
    val reason: String = "",
    val delta: Long = 0,
    val amount: Long = 0,
    val balanceAfter: Long = 0,
    val createdAt: Long = 0
) {
    /** 变动量：不同版本字段名可能为 delta 或 amount */
    val change: Long get() = if (delta != 0L) delta else amount
}

/** 未读通知概览：GET /notifications/summary → { unread, messages, lobby, byType } */
data class NotificationSummary(
    val unread: Int = 0,
    val messages: Int = 0,
    val lobby: Int = 0
)

/** 大会员状态与兑换记录：GET /vip/me → { history, uploadMaxBytes } */
data class VipMe(
    val uploadMaxBytes: Long = 0,
    val history: List<VipRedeemRecord> = emptyList()
)

data class VipRedeemRecord(
    val code: String = "",
    val plan: String = "",
    val days: Int = 0,
    val createdAt: Long = 0
)

/** 大会员兑换请求：POST /vip/redeem */
data class VipRedeemRequest(val code: String)
