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
import androidx.leanback.widget.Row
import androidx.lifecycle.lifecycleScope
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/**
 * 主浏览界面 - Leanback Browse 风格
 * 左侧分区导航，右侧视频网格，支持滚动到底部自动加载下一页
 */
class MainBrowseFragment : BrowseSupportFragment() {

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val repository = LingXiApp.get().repository

    // 每行的分页状态
    private data class PageState(
        var currentPage: Int = 1,
        var hasMore: Boolean = true,
        var loading: Boolean = false,
        var sort: String? = null,
        var partition: String? = null
    )
    private val pageStates = HashMap<Long, PageState>()

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

        setOnSearchClickedListener {
            (requireActivity() as? MainActivity)?.openSearch()
        }

        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        adapter = rowsAdapter
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val video = item as? Video ?: return@OnItemViewClickedListener
            val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_VIDEO_ID, video.id)
            }
            startActivity(intent)
        }

        // 选中项接近行尾时自动加载下一页
        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, row, _ ->
            val video = item as? Video ?: return@OnItemViewSelectedListener
            val listRow = row as? ListRow ?: return@OnItemViewSelectedListener
            val headerId = listRow.headerItem.id
            val innerAdapter = listRow.adapter as? ArrayObjectAdapter ?: return@OnItemViewSelectedListener
            val position = innerAdapter.indexOf(video)
            val total = innerAdapter.size()
            // 当选中倒数第 3 个以内时，触发加载下一页
            if (total > 0 && position >= total - 3) {
                loadPage(listRow, headerId)
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            addRow(HEADER_RECOMMEND, "推荐", sort = "hot")
            addRow(HEADER_NEWEST, "最新", sort = "new")

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

    private fun addRow(headerId: Long, name: String, sort: String? = null, partition: String? = null) {
        val headerItem = HeaderItem(headerId, name)
        val listRowAdapter = ArrayObjectAdapter(VideoCardPresenter())
        val listRow = ListRow(headerItem, listRowAdapter)
        rowsAdapter.add(listRow)

        pageStates[headerId] = PageState(sort = sort, partition = partition)
        loadPage(listRow, headerId)
    }

    /**
     * 加载一页视频数据
     */
    private fun loadPage(row: ListRow, headerId: Long) {
        val state = pageStates[headerId] ?: return
        if (!state.hasMore || state.loading) return
        state.loading = true

        val innerAdapter = row.adapter as? ArrayObjectAdapter ?: return

        lifecycleScope.launch {
            try {
                val result = repository.getVideos(
                    page = state.currentPage,
                    pageSize = PAGE_SIZE,
                    sort = state.sort,
                    partition = state.partition
                )
                result.onSuccess { pageData ->
                    val beforeSize = innerAdapter.size()
                    pageData.items.filter { it.isVideo }.forEach { video ->
                        innerAdapter.add(video)
                    }
                    val addedCount = innerAdapter.size() - beforeSize
                    // 双重判断 hasMore：API 字段 + 已加载数量 vs 总数
                    state.hasMore = pageData.hasMore && innerAdapter.size() < pageData.total
                    state.currentPage++
                    // 强制刷新行视图，确保 Leanback HorizontalGridView 显示新增项
                    if (addedCount > 0) {
                        val rowIndex = rowsAdapter.indexOf(row)
                        if (rowIndex >= 0) {
                            rowsAdapter.notifyItemRangeChanged(rowIndex, 1)
                        }
                    }
                }.onFailure {
                    // 单次失败不终止分页，仅重置 loading 允许下次重试
                }
            } finally {
                state.loading = false
            }
        }
    }

    companion object {
        private const val HEADER_RECOMMEND = 1L
        private const val HEADER_NEWEST = 2L
        private const val HEADER_PARTITION_START = 100L
        private const val PAGE_SIZE = 30
    }
}
