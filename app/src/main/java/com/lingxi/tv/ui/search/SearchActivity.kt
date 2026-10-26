package com.lingxi.tv.ui.search

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.SearchSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.ObjectAdapter
import androidx.lifecycle.lifecycleScope
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.ui.main.VideoCardPresenter
import com.lingxi.tv.ui.player.PlayerActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 搜索 Activity - 使用 Leanback SearchFragment，支持分页加载
 */
class SearchActivity : FragmentActivity(), SearchSupportFragment.SearchResultProvider {

    private lateinit var searchFragment: SearchSupportFragment
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val repository = LingXiApp.get().repository

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    // 当前搜索状态
    private var currentQuery: String = ""
    private var currentPage: Int = 1
    private var hasMore: Boolean = true
    private var loading: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

        searchFragment = SearchSupportFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.search_container, searchFragment)
            .commit()

        searchFragment.setSearchResultProvider(this)
        searchFragment.setOnItemViewClickedListener { _, item, _, _ ->
            if (item is Video) {
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_VIDEO_ID, item.id)
                }
                startActivity(intent)
            }
        }

        // 选中接近末尾时加载下一页
        searchFragment.setOnItemViewSelectedListener { _, item, row, _ ->
            if (item is Video && row is ListRow) {
                val adapter = row.adapter as? ArrayObjectAdapter ?: return@setOnItemViewSelectedListener
                val position = adapter.indexOf(item)
                val total = adapter.size()
                if (total - position <= 5) {
                    loadMoreResults()
                }
            }
        }
    }

    override fun getResultsAdapter(): ObjectAdapter = rowsAdapter

    override fun onQueryTextChange(newQuery: String): Boolean {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(300)
            if (newQuery.isNotBlank()) {
                performSearch(newQuery)
            } else {
                rowsAdapter.clear()
                currentQuery = ""
            }
        }
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        searchJob?.cancel()
        if (query.isNotBlank()) {
            performSearch(query)
        }
        return true
    }

    private fun performSearch(query: String) {
        currentQuery = query
        currentPage = 1
        hasMore = true
        loading = false
        rowsAdapter.clear()
        loadSearchPage(query, 1)
    }

    private fun loadMoreResults() {
        if (!hasMore || loading || currentQuery.isBlank()) return
        loadSearchPage(currentQuery, currentPage)
    }

    private fun loadSearchPage(query: String, page: Int) {
        if (loading) return
        loading = true
        loadMoreJob = lifecycleScope.launch {
            val result = repository.search(query, page, PAGE_SIZE)
            result.onSuccess { searchResult ->
                // 第一页时创建行，后续页追加到已有行
                val listRowAdapter: ArrayObjectAdapter
                if (page == 1) {
                    val header = HeaderItem(0L, "搜索结果")
                    listRowAdapter = ArrayObjectAdapter(VideoCardPresenter())
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                } else {
                    val firstRow = rowsAdapter.get(0) as? ListRow
                    listRowAdapter = firstRow?.adapter as? ArrayObjectAdapter ?: return@onSuccess
                }
                searchResult.items.filter { it.isVideo }.forEach {
                    listRowAdapter.add(it)
                }
                hasMore = searchResult.hasMore
                currentPage = page + 1
            }.onFailure {
                hasMore = false
            }
            loading = false
        }
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}
