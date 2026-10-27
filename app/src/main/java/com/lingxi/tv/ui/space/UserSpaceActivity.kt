package com.lingxi.tv.ui.space

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.RowsSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.api.ApiClient
import com.lingxi.tv.data.model.SpaceInfo
import com.lingxi.tv.data.model.Video
import com.lingxi.tv.data.model.joinUrl
import com.lingxi.tv.ui.article.ArticleActivity
import com.lingxi.tv.ui.main.VideoCardPresenter
import com.lingxi.tv.ui.player.PlayerActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 个人空间页（对应手机端「主页」Tab）
 * 头部：横幅、头像、昵称/LV/认证、UID/粉丝/关注/获赞、简介、投稿/播放/加入天数
 * 下方：该用户全部作品（视频/文章/音频混合），分页加载控制内存；
 * 「关于」按钮弹出资料卡（UID/用户名/性别/注册时间/等级/认证）。
 */
class UserSpaceActivity : FragmentActivity() {

    companion object {
        const val EXTRA_UID = "extra_uid"
        private const val PAGE_SIZE = 20
    }

    private val repository = LingXiApp.get().repository
    private var uid: Long = 0

    private lateinit var listAdapter: ArrayObjectAdapter
    private var space: SpaceInfo? = null
    private var currentPage = 0
    private var loading = false
    private var noMore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_space)

        uid = intent.getLongExtra(EXTRA_UID, 0)
        if (uid <= 0) {
            finish()
            return
        }

        findViewById<TextView>(R.id.btnAbout).setOnClickListener { showAboutDialog() }

        val rowsFragment = RowsSupportFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.rows_container, rowsFragment)
            .commit()
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())
        rowsFragment.adapter = rowsAdapter
        listAdapter = ArrayObjectAdapter(VideoCardPresenter())
        rowsAdapter.add(ListRow(HeaderItem(0, "全部作品"), listAdapter))

        rowsFragment.onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            val video = item as? Video ?: return@OnItemViewClickedListener
            if (video.isArticle) {
                startActivity(Intent(this, ArticleActivity::class.java)
                    .putExtra(ArticleActivity.EXTRA_ARTICLE_ID, video.id))
                return@OnItemViewClickedListener
            }
            startActivity(Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_VIDEO_ID, video.id))
        }
        // 选中接近末尾时预取下一页（分页加载，控制内存与流量）
        rowsFragment.setOnItemViewSelectedListener { _, item, _, _ ->
            if (item == null) return@setOnItemViewSelectedListener
            val idx = listAdapter.indexOf(item)
            if (idx >= 0 && idx >= listAdapter.size() - 3) loadNextPage()
        }

        loadHeader()
        loadNextPage()
    }

    private fun loadHeader() {
        lifecycleScope.launch {
            repository.getUserSpaceInfo(uid).onSuccess { s ->
                space = s
                bindHeader(s)
            }
        }
    }

    private fun bindHeader(s: SpaceInfo) {
        val u = s.user ?: return
        val ivBanner = findViewById<ImageView>(R.id.ivBanner)
        val ivAvatar = findViewById<ImageView>(R.id.ivAvatar)
        val tvName = findViewById<TextView>(R.id.tvName)
        val tvMeta = findViewById<TextView>(R.id.tvMeta)
        val tvBio = findViewById<TextView>(R.id.tvBio)
        val tvStats = findViewById<TextView>(R.id.tvStats)

        val bannerUrl = joinUrl(ApiClient.BASE_URL, u.banner)
        if (bannerUrl.isNotEmpty()) {
            Glide.with(this).load(bannerUrl)
                .format(DecodeFormat.PREFER_RGB_565)
                .centerCrop().into(ivBanner)
        }
        val avatarUrl = u.getFullAvatarUrl(ApiClient.BASE_URL)
        if (avatarUrl.isNotEmpty()) {
            Glide.with(this).load(avatarUrl)
                .format(DecodeFormat.PREFER_RGB_565)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .circleCrop().into(ivAvatar)
        } else {
            ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
        }

        val badge = if (u.badgeLabel.isNotEmpty()) "  ⚡${u.badgeLabel}" else ""
        tvName.text = "${u.nickname.ifEmpty { u.username }}  LV${u.level}$badge"
        tvMeta.text = "UID ${u.id}  ·  ${u.followerCount} 粉丝  ·  ${u.followingCount} 关注  ·  ${u.likeReceived} 获赞"
        tvBio.text = u.bio
        tvBio.visibility = if (u.bio.isBlank()) View.GONE else View.VISIBLE

        val days = if (u.createdAt > 0)
            ((System.currentTimeMillis() - u.createdAt) / 86400000L).coerceAtLeast(1) else 0
        tvStats.text = buildString {
            append("投稿 ${s.videoCount}")
            append("  ·  播放 ${s.totalViews}")
            if (days > 0) append("  ·  加入 ${days} 天")
        }
    }

    private fun showAboutDialog() {
        val u = space?.user ?: return
        val genderText = when (u.gender.lowercase()) {
            "1", "male", "男" -> "男"
            "2", "female", "女" -> "女"
            else -> "保密"
        }
        val regDate = if (u.createdAt > 0)
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(u.createdAt))
        else "未知"
        val msg = buildString {
            append("UID：${u.id}\n")
            append("用户名：${u.username}\n")
            append("性别：$genderText\n")
            append("注册时间：$regDate\n")
            append("等级：LV${u.level}")
            if (u.badgeLabel.isNotEmpty()) append("\n认证：${u.badgeLabel}")
        }
        AlertDialog.Builder(this)
            .setTitle("关于 ${u.nickname.ifEmpty { u.username }}")
            .setMessage(msg)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun loadNextPage() {
        if (loading || noMore) return
        loading = true
        val nextPage = currentPage + 1
        lifecycleScope.launch {
            repository.getUserVideos(uid, nextPage, PAGE_SIZE).onSuccess { data ->
                val items = data.items.filter { it.isVideo || it.isAudio || it.isArticle }
                items.forEach { listAdapter.add(it) }
                currentPage = nextPage
                if (data.items.size < PAGE_SIZE) noMore = true
                if (currentPage == 1 && items.isEmpty()) {
                    findViewById<View>(R.id.tvEmpty).visibility = View.VISIBLE
                }
            }
            loading = false
        }
    }
}
