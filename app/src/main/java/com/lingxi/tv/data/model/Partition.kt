package com.lingxi.tv.data.model

/**
 * 分区模型
 */
data class Partition(
    val slug: String = "",
    val name: String = "",
    val color: String = "",
    val children: List<String> = emptyList(),
    val videoCount: Int = 0
)

/**
 * 弹幕模型
 */
data class Danmaku(
    val id: Long = 0,
    val content: String = "",
    val timeMs: Long = 0,
    val mode: Int = 1,
    val color: String = "#FFFFFF",
    val fontSize: Int = 25,
    val uid: Long = 0,
    val createdAt: Long = 0,
    val avatar: String = "",
    val badgeType: String = "",
    val badgeLabel: String = "",
    val vip: Boolean = false,
    val vipTier: String = "",
    val vipTierLabel: String = ""
) {
    companion object {
        const val MODE_SCROLL = 1
        const val MODE_TOP = 5
        const val MODE_BOTTOM = 4
    }
}

/**
 * 弹幕列表响应
 */
data class DanmakuList(
    val items: List<Danmaku> = emptyList(),
    val segmentMs: Long = 60000,
    val total: Int = 0
)

/**
 * 搜索响应
 */
data class SearchResult(
    val type: String = "video",
    val items: List<Video> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val hasMore: Boolean = false
)
