package com.lingxi.tv.data.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.lingxi.tv.data.auth.AuthManager
import com.lingxi.tv.data.model.ApiResponse
import com.lingxi.tv.data.model.LoginResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API 客户端 - 单例，避免重复创建连接池
 */
object ApiClient {

    /** 服务器基础地址，供 UI 层拼接封面/视频完整 URL 使用 */
    const val BASE_URL = ApiService.BASE_URL

    private const val CONNECT_TIMEOUT = 15L
    private const val READ_TIMEOUT = 30L
    private const val WRITE_TIMEOUT = 30L

    val gson: Gson by lazy {
        GsonBuilder()
            .serializeNulls()
            .create()
    }

    private const val TAG = "LingXiApi"

    /** 可重试的服务端错误码（Bad Gateway 等上游抖动） */
    private val RETRYABLE_HTTP_CODES = setOf(502, 503, 504)
    private const val MAX_SERVER_RETRY = 3

    /** 专门用于刷新令牌的客户端：不带鉴权与 401 处理，避免刷新请求自身再触发刷新 */
    private val tokenClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 用 refreshToken 换取新的 accessToken。
     * 站点 accessToken 有效期只有 2 小时（JWT 的 exp - iat = 7200s），过期后所有需要
     * 登录的接口都会返回 401 {"message":"请先登录"}，因此必须自动续期。
     */
    @Synchronized
    private fun refreshAccessToken(): String? {
        val refresh = AuthManager.refreshToken
        if (refresh.isNullOrEmpty()) {
            // 老版本从未保存过 refreshToken，续期无从下手，直接清空会话让界面引导重新登录
            Log.w(TAG, "no refreshToken, clearing session")
            AuthManager.clearSession()
            return null
        }
        return try {
            val payload = gson.toJson(mapOf("refreshToken" to refresh))
            val request = Request.Builder()
                .url(ApiService.BASE_URL + "/api/v1/auth/refresh")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("X-Device-Id", AuthManager.deviceId)
                .header("X-Device-Label", AuthManager.DEVICE_LABEL)
                .build()
            tokenClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "refresh failed: HTTP ${response.code}")
                    // refreshToken 失效（400/401）：会话无法恢复，清空登录态，界面会引导重新登录
                    if (response.code == 400 || response.code == 401) AuthManager.clearSession()
                    return null
                }
                val body = response.body?.string() ?: return null
                val type = object : TypeToken<ApiResponse<LoginResult>>() {}.type
                val result = gson.fromJson<ApiResponse<LoginResult>>(body, type)
                val newToken = result?.data?.accessToken
                if (newToken.isNullOrEmpty()) return null
                AuthManager.token = newToken
                result.data.refreshToken?.let { AuthManager.refreshToken = it }
                result.data.user?.let { AuthManager.user = it }
                Log.i(TAG, "accessToken refreshed")
                newToken
            }
        } catch (e: Exception) {
            Log.w(TAG, "refresh error: ${e.message}")
            null
        }
    }

    /** 统计该响应之前已经历的鉴权重试次数 */
    private fun authRetryCount(response: okhttp3.Response): Int {
        var count = 0
        var current: okhttp3.Response? = response
        while (current != null) {
            count++
            current = current.priorResponse
        }
        return count
    }

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            // 自动重试：服务器 5xx / 连接失败 / SSL 超时，最多重试 2 次，间隔 1 秒
            .retryOnConnectionFailure(true)
            // 主站上游抖动：偶发 502/503/504（Bad Gateway 说明请求没到应用层，重发安全），
            // 自动重试最多 3 次，间隔递增（600ms/1200ms/1800ms）
            .addInterceptor { chain ->
                val request = chain.request()
                var response = chain.proceed(request)
                var attempt = 0
                while (response.code in RETRYABLE_HTTP_CODES && attempt < MAX_SERVER_RETRY) {
                    attempt++
                    response.close()
                    Thread.sleep(600L * attempt)
                    response = chain.proceed(request)
                }
                response
            }
            // 自动附加 Authorization 头
            .addInterceptor { chain ->
                val original = chain.request()
                val token = AuthManager.token
                val request = if (!token.isNullOrEmpty() && original.header("Authorization") == null) {
                    original.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else original
                chain.proceed(request)
            }
            // 设备标识头：两步验证「记住这台设备」信任机制依赖
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-Device-Id", AuthManager.deviceId)
                    .header("X-Device-Label", AuthManager.DEVICE_LABEL)
                    .build()
                chain.proceed(request)
            }
            // 401 自动续期：用 refreshToken 换新 accessToken 后重试原请求
            .authenticator { _, response ->
                if (response.request.header("Authorization") == null) return@authenticator null
                if (authRetryCount(response) > 2) return@authenticator null
                val newToken = refreshAccessToken() ?: return@authenticator null
                response.request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
            }
            // 网络请求日志（debugBuild 输出完整请求/响应，releaseBuild 输出简洁行）
            .addInterceptor(loggingInterceptor)

        builder.build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ApiService.BASE_URL + "/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    /**
     * 获取完整 URL
     */
    fun fullUrl(path: String): String {
        if (path.startsWith("http")) return path
        return if (path.startsWith("/")) ApiService.BASE_URL + path
        else ApiService.BASE_URL + "/" + path
    }
}
