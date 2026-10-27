package com.lingxi.tv.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.data.api.ApiClient
import com.lingxi.tv.data.model.User

/**
 * 认证管理器 - 持久化 Token、用户信息、当前选中节点
 * 使用 SharedPreferences 存储，避免每次启动重新登录
 */
object AuthManager {

    private const val PREFS_NAME = "lingxi_auth"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user_json"
    private const val KEY_NODE_ID = "node_id"
    private const val KEY_NODE_URL = "node_url"
    private const val KEY_NODE_NAME = "node_name"

    private val prefs: SharedPreferences by lazy {
        LingXiApp.get().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_TOKEN, value).apply()
        }

    var user: User?
        get() {
            val json = prefs.getString(KEY_USER, null) ?: return null
            return try {
                ApiClient.gson.fromJson(json, User::class.java)
            } catch (e: Exception) {
                null
            }
        }
        set(value) {
            val json = value?.let { ApiClient.gson.toJson(it) }
            prefs.edit().putString(KEY_USER, json).apply()
        }

    /** 当前选中的节点 URL（用于播放地址拼接），null 表示使用主站 */
    var nodeUrl: String?
        get() = prefs.getString(KEY_NODE_URL, null)
        set(value) = prefs.edit().putString(KEY_NODE_URL, value).apply()

    var nodeName: String?
        get() = prefs.getString(KEY_NODE_NAME, null)
        set(value) = prefs.edit().putString(KEY_NODE_NAME, value).apply()

    var nodeId: Long
        get() = prefs.getLong(KEY_NODE_ID, 0)
        set(value) = prefs.edit().putLong(KEY_NODE_ID, value).apply()

    val isLoggedIn: Boolean get() = !token.isNullOrEmpty()

    /** 管理员 UID 名单（本地鉴权） */
    private val ADMIN_UIDS = setOf(1L, 3L, 12L, 62L, 106L)

    /** 当前登录用户是否为管理员 */
    val isAdmin: Boolean get() = isLoggedIn && (user?.id ?: 0L) in ADMIN_UIDS

    /**
     * 获取播放用的基础 URL：选中节点则用节点 URL，否则用主站
     */
    fun getPlaybackBaseUrl(): String {
        return nodeUrl ?: ApiClient.BASE_URL
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}
