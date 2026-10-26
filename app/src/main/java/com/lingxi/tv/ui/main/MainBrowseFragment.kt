package com.lingxi.tv.ui.main

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
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
        Log.d(TAG, "onViewCreated")
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

        onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, row ->
            val video = item as? Video
            if (video == null) {
                return@OnItemViewSelectedListener
            }
            // row 可能不是 ListRow 类型，通过 headerId 从 rowsAdapter 中查找
            val headerId = row?.headerItem?.id
            if (headerId == null) {
                Log.d(TAG, "onItemSelected: headerId is null")
                return@OnItemViewSelectedListener
            }
            val listRow = findListRowByHeaderId(headerId)
            if (listRow == null) {
                Log.d(TAG, "onItemSelected: ListRow not found for headerId=$headerId")
                return@OnItemViewSelectedListener
            }
            val innerAdapter = listRow.adapter as? ArrayObjectAdapter
            if (innerAdapter == null) {
                Log.d(TAG, "onItemSelected: innerAdapter is null")
                return@OnItemViewSelectedListener
            }
            val position = innerAdapter.indexOf(video)
            val total = innerAdapter.size()
            Log.d(TAG, "onItemSelected: headerId=$headerId, position=$position, total=$total")
            if (total > 0 && position >= total - 3) {
                Log.d(TAG, "onItemSelected: near end, triggering loadPage for headerId=$headerId")
                loadPage(listRow, headerId)
            }
        }
        Log.d(TAG, "setupEventListeners: listeners set")
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
            }.onFailure {
                Log.e(TAG, "loadData: getPartitions failed", it)
            }
        }
    }

    private fun findListRowByHeaderId(headerId: Long): ListRow? {
        for (i in 0 until rowsAdapter.size()) {
            val row = rowsAdapter.get(i) as? ListRow ?: continue
            if (row.headerItem.id == headerId) return row
        }
        return null
    }

    private fun addRow(headerId: Long, name: String, sort: String? = null, partition: String? = null) {
        val headerItem = HeaderItem(headerId, name)
        val listRowAdapter = ArrayObjectAdapter(VideoCardPresenter())
        val listRow = ListRow(headerItem, listRowAdapter)
        rowsAdapter.add(listRow)

        pageStates[headerId] = PageState(sort = sort, partition = partition)
        loadPage(listRow, headerId)
    }

    private fun loadPage(row: ListRow, headerId: Long) {
        val state = pageStates[headerId]
        if (state == null) {
            Log.d(TAG, "loadPage: no state for headerId=$headerId")
            return
        }
        if (!state.hasMore) {
            Log.d(TAG, "loadPage: no more for headerId=$headerId")
            return
        }
        if (state.loading) {
            Log.d(TAG, "loadPage: already loading for headerId=$headerId")
            return
        }
        state.loading = true
        Log.d(TAG, "loadPage: loading page=${state.currentPage} for headerId=$headerId, sort=${state.sort}, partition=${state.partition}")

        val innerAdapter = row.adapter as? ArrayObjectAdapter
        if (innerAdapter == null) {
            Log.d(TAG, "loadPage: innerAdapter is null")
            state.loading = false
            return
        }

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
                    state.hasMore = pageData.hasMore && innerAdapter.size() < pageData.total
                    state.currentPage++
                    Log.d(TAG, "loadPage: headerId=$headerId added=$addedCount, total=${innerAdapter.size()}, hasMore=${state.hasMore}, nextPage=${state.currentPage}")
                }.onFailure {
                    Log.e(TAG, "loadPage: headerId=$headerId failed", it)
                }
            } finally {
                state.loading = false
            }
        }
    }

    companion object {
        private const val TAG = "LingXiBrowse"
        private const val HEADER_RECOMMEND = 1L
        private const val HEADER_NEWEST = 2L
        private const val HEADER_PARTITION_START = 100L
        private const val PAGE_SIZE = 30
    }
}
