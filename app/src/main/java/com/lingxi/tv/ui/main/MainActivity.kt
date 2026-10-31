package com.lingxi.tv.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.ui.search.SearchActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 主 Activity - 承载 BrowseFragment
 */
class MainActivity : FragmentActivity() {

    private val repository = LingXiApp.get().repository

    /** 在线心跳：保持「在线」状态，供站内状态/正在观看统计 */
    private var presenceJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, MainBrowseFragment())
                .commit()
        }
        startPresence()
    }

    /** 每 60s 一次在线心跳（服务端 presence 窗口通常为分钟级） */
    private fun startPresence() {
        presenceJob?.cancel()
        presenceJob = lifecycleScope.launch {
            while (isActive) {
                repository.sendPresence()
                delay(60_000)
            }
        }
    }

    override fun onDestroy() {
        presenceJob?.cancel()
        presenceJob = null
        super.onDestroy()
    }

    fun openSearch() {
        startActivity(Intent(this, SearchActivity::class.java))
    }
}
