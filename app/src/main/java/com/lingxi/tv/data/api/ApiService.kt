package com.lingxi.tv.data.api

import com.lingxi.tv.data.model.ApiResponse
import com.lingxi.tv.data.model.DanmakuList
import com.lingxi.tv.data.model.PageData
import com.lingxi.tv.data.model.Partition
import com.lingxi.tv.data.model.SearchResult
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.data.model.VideoDetail
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 凌汐弹幕网 API 服务
 * 仅包含电视端必要的接口，不包含后台管理相关接口
 */
interface ApiService {

    companion object {
        const val BASE_URL = "https://lingxi.xiguastudio.top"
    }

    /**
     * 获取视频列表
     * @param page 页码
     * @param pageSize 每页数量
     * @param sort 排序方式: hot(热门), new(最新)
     * @param partition 分区 slug，不传则为全部
     */
    @GET("/api/v1/videos")
    suspend fun getVideos(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("sort") sort: String? = null,
        @Query("partition") partition: String? = null
    ): ApiResponse<PageData<Video>>

    /**
     * 获取视频详情
     */
    @GET("/api/v1/videos/{id}")
    suspend fun getVideoDetail(
        @Path("id") id: Long
    ): ApiResponse<VideoDetail>

    /**
     * 获取分区列表
     */
    @GET("/api/v1/partitions")
    suspend fun getPartitions(): ApiResponse<PageData<Partition>>

    /**
     * 搜索
     */
    @GET("/api/v1/search")
    suspend fun search(
        @Query("q") keyword: String,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<SearchResult>

    /**
     * 获取视频弹幕
     * @param videoId 视频 ID
     * @param segment 分段索引（可选）
     */
    @GET("/api/v1/danmaku")
    suspend fun getDanmaku(
        @Query("videoId") videoId: Long,
        @Query("segment") segment: Int? = null
    ): ApiResponse<DanmakuList>
}
