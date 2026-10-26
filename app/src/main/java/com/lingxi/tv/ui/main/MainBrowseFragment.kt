package com.lingxi.tv.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/**
 * 主浏览界面 - Leanback Browse 风格
 * 左侧分区导航，右侧视频网格
 */
class MainBrowseFragment : BrowseSupportFragment() {

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val repository = LingXiApp.get().repository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupEventListeners()
        loadData()
    }

    private fun setupUI() {
        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = Color.parseColor("#1a8a5c")

        // 搜索按钮
        setOnSearchClickedListener {
            (requireActivity() as? MainActivity)?.openSearch()
        }

        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is Video) {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_VIDEO_ID, item.id)
                }
                startActivity(intent)
            }
        }

        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, row ->
            // 切换行时可触发预加载，这里保持简单
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 先加载推荐和最新
            addRow(HEADER_RECOMMEND, "推荐", sort = "hot")
            addRow(HEADER_NEWEST, "最新", sort = "new")

            // 加载分区
            val result = repository.getPartitions()
            result.onSuccess { pageData ->
                pageData.items.forEachIndexed { index, partition ->
                    addRow(
                        headerId = HEADER_PARTITION_START + index.toLong(),
                        name = partition.name,
                        partition = partition.slug
                    )
                }
            }
        }
    }

    /**
     * 添加一个视频行
     */
    private fun addRow(headerId: Long, name: String, sort: String? = null, partition: String? = null) {
        val headerItem = HeaderItem(headerId, name)
        val listRowAdapter = ArrayObjectAdapter(VideoCardPresenter())
        val listRow = ListRow(headerItem, listRowAdapter)
        rowsAdapter.add(listRow)

        // 异步加载该行视频
        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.getVideos(page = 1, pageSize = 20, sort = sort, partition = partition)
            result.onSuccess { pageData ->
                pageData.items.filter { it.isVideo }.forEach { video ->
                    listRowAdapter.add(video)
                }
            }
        }
    }

    companion object {
        private const val HEADER_RECOMMEND = 1L
        private const val HEADER_NEWEST = 2L
        private const val HEADER_PARTITION_START = 100L
    }
}
