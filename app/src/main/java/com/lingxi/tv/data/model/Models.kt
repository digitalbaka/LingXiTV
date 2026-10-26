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
 * 视频模型
 */
data class Video(
    val id: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val description: String = "",
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
    val isVideo: Boolean get() = kind == "video"

    fun getFullCoverUrl(baseUrl: String): String {
        return if (cover.startsWith("http")) cover else baseUrl + cover
    }

    fun getFullSrcUrl(baseUrl: String): String {
        return if (src.startsWith("http")) src else baseUrl + src
    }
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
    fun getFullUrl(baseUrl: String): String {
        return if (url.startsWith("http")) url else baseUrl + url
    }
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
    fun getFullAvatarUrl(baseUrl: String): String {
        return if (avatar.startsWith("http")) avatar else baseUrl + avatar
    }
}

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
    val progress: Long? = null
)
