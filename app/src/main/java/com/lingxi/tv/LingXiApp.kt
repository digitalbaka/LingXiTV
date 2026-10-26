package com.lingxi.tv

import android.app.Application
import com.lingxi.tv.data.repository.VideoRepository

/**
 * 凌汐弹幕网电视版 Application
 * 负责全局初始化，Repository 单例管理
 */
class LingXiApp : Application() {

    companion object {
        @Volatile
        private var instance: LingXiApp? = null
        fun get(): LingXiApp = instance!!
    }

    val repository by lazy { VideoRepository() }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
