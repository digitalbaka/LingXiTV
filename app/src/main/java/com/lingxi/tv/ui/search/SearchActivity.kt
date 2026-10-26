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
 * 搜索 Activity - 使用 Leanback SearchFragment
 */
class SearchActivity : FragmentActivity(), SearchSupportFragment.SearchResultProvider {

    private lateinit var searchFragment: SearchSupportFragment
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private val repository = LingXiApp.get().repository

    private var searchJob: Job? = null

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
        lifecycleScope.launch {
            val result = repository.search(query, 1, 30)
            result.onSuccess { searchResult ->
                rowsAdapter.clear()
                if (searchResult.items.isNotEmpty()) {
                    val header = HeaderItem(0L, "搜索结果")
                    val listRowAdapter = ArrayObjectAdapter(VideoCardPresenter())
                    searchResult.items.filter { it.isVideo }.forEach {
                        listRowAdapter.add(it)
                    }
                    rowsAdapter.add(ListRow(header, listRowAdapter))
                }
            }
        }
    }
}
