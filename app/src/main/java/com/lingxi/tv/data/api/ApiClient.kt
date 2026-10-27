package com.lingxi.tv.data.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lingxi.tv.data.auth.AuthManager
import okhttp3.OkHttpClient
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

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            // 连接池复用，减少内存分配
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            // 设备标识头：两步验证「记住这台设备」信任机制依赖
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-Device-Id", AuthManager.deviceId)
                    .header("X-Device-Label", AuthManager.deviceLabel)
                    .build()
                chain.proceed(request)
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
