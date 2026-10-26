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
 * 左侧分区导航，右侧视频网格，支持滚动到底部自动加载下一页
 */
class MainBrowseFragment : BrowseSupportFragment() {

    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val repository = LingXiApp.get().repository

    // 每行的分页状态：headerId -> (当前页码, 是否还有更多, 加载中标志)
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
            if (item is Video) {
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_VIDEO_ID, item.id)
                }
                startActivity(intent)
            }
        }

        // 选中项接近行尾时自动加载下一页
        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, row, _ ->
            if (item is Video && row is ListRow) {
                val headerId = row.headerItem.id
                val adapter = row.adapter as? ArrayObjectAdapter ?: return@OnItemViewSelectedListener
                val position = adapter.indexOf(item)
                val total = adapter.size()
                // 当选中倒数第 5 个以内时，触发加载下一页
                if (total - position <= 5) {
                    loadMore(row, headerId)
                }
            }
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
     * 添加一个视频行并加载第一页
     */
    private fun addRow(headerId: Long, name: String, sort: String? = null, partition: String? = null) {
        val headerItem = HeaderItem(headerId, name)
        val listRowAdapter = ArrayObjectAdapter(VideoCardPresenter())
        val listRow = ListRow(headerItem, listRowAdapter)
        rowsAdapter.add(listRow)

        pageStates[headerId] = PageState(sort = sort, partition = partition)

        loadPage(listRow, headerId)
    }

    /**
     * 加载指定行的下一页
     */
    private fun loadMore(row: ListRow, headerId: Long) {
        loadPage(row, headerId)
    }

    /**
     * 加载一页视频数据
     */
    private fun loadPage(row: ListRow, headerId: Long) {
        val state = pageStates[headerId] ?: return
        if (!state.hasMore || state.loading) return
        state.loading = true

        val listRowAdapter = row.adapter as? ArrayObjectAdapter ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.getVideos(
                page = state.currentPage,
                pageSize = PAGE_SIZE,
                sort = state.sort,
                partition = state.partition
            )
            result.onSuccess { pageData ->
                pageData.items.filter { it.isVideo }.forEach { video ->
                    listRowAdapter.add(video)
                }
                state.hasMore = pageData.hasMore
                state.currentPage++
            }.onFailure {
                state.hasMore = false
            }
            state.loading = false
        }
    }

    companion object {
        private const val HEADER_RECOMMEND = 1L
        private const val HEADER_NEWEST = 2L
        private const val HEADER_PARTITION_START = 100L
        private const val PAGE_SIZE = 30
    }
}
