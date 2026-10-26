package com.lingxi.tv.data.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API 客户端 - 单例，避免重复创建连接池
 */
object ApiClient {

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
