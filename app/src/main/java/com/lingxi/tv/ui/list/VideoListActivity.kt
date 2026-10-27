package com.lingxi.tv.ui.list

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.app.RowsSupportFragment
import androidx.lifecycle.lifecycleScope
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.auth.AuthManager
import com.lingxi.tv.data.model.PageData
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.ui.auth.LoginActivity
import com.lingxi.tv.ui.article.ArticleActivity
import com.lingxi.tv.ui.main.VideoCardPresenter
import com.lingxi.tv.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/**
 * 通用视频列表页 - 用于历史记录、收藏、关注动态、排行榜
 * 通过 EXTRA_TYPE 区分数据来源
 */
class VideoListActivity : FragmentActivity() {

    companion object {
        const val EXTRA_TYPE = "extra_type"
        const val TYPE_HISTORY = "history"
        const val TYPE_FAVORITES = "favorites"
        const val TYPE_FOLLOWING = "following"
        const val TYPE_RANKING = "ranking"

        private val TITLES = mapOf(
            TYPE_HISTORY to "历史记录",
            TYPE_FAVORITES to "我的收藏",
            TYPE_FOLLOWING to "关注动态",
            TYPE_RANKING to "排行榜"
        )
    }

    private val repository = LingXiApp.get().repository
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var type: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_list)

        type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_RANKING
        title = TITLES[type] ?: "视频列表"

        val rowsFragment = RowsSupportFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.rows_container, rowsFragment)
            .commit()

        rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        rowsFragment.adapter = rowsAdapter

        rowsFragment.onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val video = item as? Video ?: return@OnItemViewClickedListener
            // 文章走阅读页，视频/音频走播放器
            if (video.isArticle) {
                startActivity(Intent(this, ArticleActivity::class.java)
                    .putExtra(ArticleActivity.EXTRA_ARTICLE_ID, video.id))
                return@OnItemViewClickedListener
            }
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_VIDEO_ID, video.id)
            }
            startActivity(intent)
        }

        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 内存优化：清空适配器，释放视频卡片引用
        rowsAdapter.clear()
    }

    private fun loadData() {
        // 历史/收藏/关注需要登录
        if (type != TYPE_RANKING && !AuthManager.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // 关注动态：直接全屏播放，按向下键连续浏览下一条
        if (type == TYPE_FOLLOWING) {
            startActivity(Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_FEED_MODE, true))
            finish()
            return
        }

        lifecycleScope.launch {
            val result: Result<PageData<Video>> = when (type) {
                TYPE_HISTORY -> repository.getHistory()
                TYPE_FAVORITES -> repository.getFavorites()
                TYPE_FOLLOWING -> repository.getFollowingVideos()
                else -> repository.getRanking()
            }
            result.onSuccess { pageData ->
                val listAdapter = ArrayObjectAdapter(VideoCardPresenter())
                pageData.items.filter { it.isVideo || it.isAudio || it.isArticle }.forEach { listAdapter.add(it) }
                val header = HeaderItem(0, TITLES[type] ?: "")
                rowsAdapter.add(ListRow(header, listAdapter))
            }.onFailure {
                findViewById<View>(R.id.tvEmpty)?.visibility = View.VISIBLE
            }
        }
    }
}
