package com.lingxi.tv.data.model

/**
 * 网关节点信息
 */
data class GatewayInfo(
    val enabled: Boolean = false,
    val origin: NodeOrigin? = null,
    val current: NodeCurrent? = null,
    val granted: Boolean = false,
    val defaultQuota: Long = 0,
    val nodes: List<GatewayNode> = emptyList(),
    val quota: Quota? = null
)

/**
 * 节点（线路）
 */
data class GatewayNode(
    val id: Long = 0,
    val name: String = "",
    val kind: String = "",
    val note: String = "",
    val url: String = "",
    val host: String = "",
    val port: Int = 0,
    val multiplier: Double = 1.0,
    val enabled: Boolean = true
) {
    val isDirect: Boolean get() = kind == "direct"
}

/**
 * 主站信息
 */
data class NodeOrigin(
    val name: String = "",
    val url: String = ""
)

/**
 * 当前接入状态
 */
data class NodeCurrent(
    val direct: Boolean = false,
    val ip: String = "",
    val nodeId: Long = 0,
    val via: String = ""
)

/**
 * 节点授权请求体：POST /gateway/handoff { nodeId }
 */
data class HandoffRequest(
    val nodeId: Long
)

/**
 * 节点授权响应：{ nodeId, url, ip }
 * url 带 lx_ticket（浏览器落地页用）；ip 为服务端记录的本机公网 IP，
 * 直连节点据此校验 X-Forwarded-For 请求头
 */
data class HandoffResult(
    val nodeId: Long = 0,
    val url: String = "",
    val ip: String = ""
)

/**
 * 流量配额
 */
data class Quota(
    val used: Long = 0,
    val limit: Long = 0,
    val resetAt: Long = 0
) {
    val remain: Long get() = (limit - used).coerceAtLeast(0)
}
