package com.lingxi.tv.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.auth.AuthManager
import com.lingxi.tv.data.model.AdminComment
import com.lingxi.tv.data.model.AdminDanmaku
import com.lingxi.tv.data.model.AdminReport
import com.lingxi.tv.data.model.AdminUser
import com.lingxi.tv.data.model.AdminVideo
import com.lingxi.tv.data.model.displayName
import com.lingxi.tv.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/**
 * 管理后台（本地 UID 鉴权 + 服务端 401 双重校验）
 * 模块：稿件审核 / 评论 / 弹幕 / 举报处理 / 用户管理
 * 点击列表项弹出操作对话框
 */
class AdminActivity : AppCompatActivity() {

    private val repository = LingXiApp.get().repository

    private lateinit var tvOverview: TextView
    private lateinit var rvAdmin: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var searchRow: View
    private lateinit var etSearch: EditText
    private val adapter = AdminAdapter { onItemAction(it) }

    /** 当前模块 */
    private var module: Module = Module.VIDEOS

    enum class Module { VIDEOS, COMMENTS, DANMAKU, REPORTS, USERS }

    /** 列表项包装，标题 + 副标题 + 原始数据 */
    data class AdminItem(val title: String, val sub: String, val data: Any)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 本地 UID 鉴权：非管理员直接退出
        if (!AuthManager.isAdmin) {
            Toast.makeText(this, "无权限访问", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContentView(R.layout.activity_admin)

        tvOverview = findViewById(R.id.tvOverview)
        rvAdmin = findViewById(R.id.rvAdmin)
        tvEmpty = findViewById(R.id.tvEmpty)
        searchRow = findViewById(R.id.searchRow)
        etSearch = findViewById(R.id.etSearch)

        rvAdmin.layoutManager = LinearLayoutManager(this)
        rvAdmin.adapter = adapter

        findViewById<Button>(R.id.tabVideos).setOnClickListener { switchModule(Module.VIDEOS) }
        findViewById<Button>(R.id.tabComments).setOnClickListener { switchModule(Module.COMMENTS) }
        findViewById<Button>(R.id.tabDanmaku).setOnClickListener { switchModule(Module.DANMAKU) }
        findViewById<Button>(R.id.tabReports).setOnClickListener { switchModule(Module.REPORTS) }
        findViewById<Button>(R.id.tabUsers).setOnClickListener { switchModule(Module.USERS) }
        findViewById<Button>(R.id.btnSearch).setOnClickListener { loadModule() }
        // 遥控器确认键直接触发搜索
        etSearch.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                loadModule()
                true
            } else false
        }

        // 电视端遥控器：默认聚焦第一个模块标签
        findViewById<Button>(R.id.tabVideos).post {
            findViewById<Button>(R.id.tabVideos).requestFocus()
        }

        loadOverview()
        switchModule(Module.VIDEOS)
    }

    private fun loadOverview() {
        lifecycleScope.launch {
            repository.getAdminOverview().onSuccess { o ->
                val t = o.totals
                if (t == null) {
                    tvOverview.text = "总览暂无数据"
                    return@onSuccess
                }
                tvOverview.text = "待审稿件 ${t.pending} · 用户 ${t.users} · 稿件 ${t.videos}\n" +
                    "弹幕 ${t.danmaku} · 评论 ${t.comments} · 关注 ${t.follows} · 播放 ${t.views}"
            }.onFailure {
                tvOverview.text = "总览加载失败：${it.message}"
            }
        }
    }

    private fun switchModule(m: Module) {
        module = m
        // 评论/弹幕/用户支持关键词搜索
        searchRow.visibility = when (m) {
            Module.COMMENTS, Module.DANMAKU, Module.USERS -> View.VISIBLE
            else -> View.GONE
        }
        if (searchRow.visibility != View.VISIBLE) etSearch.setText("")
        loadModule()
    }

    private fun loadModule() {
        val keyword = etSearch.text.toString().trim().ifEmpty { null }
        lifecycleScope.launch {
            when (module) {
                Module.VIDEOS -> repository.getAdminVideos().onSuccess { page ->
                    showItems(page.items.map {
                        AdminItem(
                            it.title,
                            "${it.up?.nickname ?: "未知UP"} · ${it.partition} · ${it.statusLabel}",
                            it
                        )
                    })
                }
                Module.COMMENTS -> repository.getAdminComments(keyword).onSuccess { page ->
                    showItems(page.items.map {
                        val name = it.author.displayName()
                        AdminItem(
                            it.content,
                            "$name · 来自《${it.video?.title ?: "未知稿件"}》",
                            it
                        )
                    })
                }
                Module.DANMAKU -> repository.getAdminDanmaku(keyword).onSuccess { page ->
                    showItems(page.items.map {
                        val name = it.author.displayName()
                        val videoTitle = it.video?.title?.ifEmpty { null } ?: "未知稿件"
                        AdminItem(
                            it.content,
                            "$name · 视频《$videoTitle》",
                            it
                        )
                    })
                }
                Module.REPORTS -> repository.getAdminReports().onSuccess { page ->
                    showItems(page.items.map {
                        val reporter = it.reporter.displayName()
                        AdminItem(
                            "${it.targetType}: ${it.reason}",
                            "举报人：$reporter · ${it.status}",
                            it
                        )
                    })
                }
                Module.USERS -> repository.getAdminUsers(keyword).onSuccess { page ->
                    showItems(page.items.map {
                        val roleLabel = when {
                            it.isAdmin -> "管理员"
                            it.banned -> "已封禁"
                            else -> "普通用户"
                        }
                        AdminItem(
                            "${it.nickname.ifEmpty { it.username }}（UID ${it.id}）",
                            "@${it.username} · LV${it.level} · 稿件 ${it.videoCount} · 被举报 ${it.reportedCount} · $roleLabel",
                            it
                        )
                    })
                }
            }.onFailure {
                showItems(emptyList())
                Toast.makeText(this@AdminActivity, "加载失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showItems(items: List<AdminItem>) {
        adapter.setData(items)
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        // 列表加载完成后，焦点落到第一条，方便遥控器直接操作
        if (items.isNotEmpty()) {
            rvAdmin.post {
                rvAdmin.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        }
    }

    /** 点击列表项，根据类型弹出对应操作 */
    private fun onItemAction(item: AdminItem) {
        when (val data = item.data) {
            is AdminVideo -> showVideoActions(data)
            is AdminComment -> showCommentActions(data)
            is AdminDanmaku -> showDanmakuActions(data)
            is AdminReport -> showReportActions(data)
            is AdminUser -> showUserActions(data)
        }
    }

    private fun showVideoActions(video: AdminVideo) {
        val pinLabel = if (video.pinned) "取消置顶" else "置顶"
        val options = arrayOf("查看播放", "通过", "驳回", pinLabel)
        AlertDialog.Builder(this)
            .setTitle(video.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openVideo(video.id)
                    1 -> doAction({ repository.reviewVideo(video.id, true) }, "已通过")
                    2 -> showRejectDialog(video)
                    3 -> doAction(
                        { repository.pinVideo(video.id, !video.pinned) },
                        if (video.pinned) "已取消置顶" else "已置顶"
                    )
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showRejectDialog(video: AdminVideo) {
        val et = EditText(this).apply { hint = "驳回理由（可选）" }
        AlertDialog.Builder(this)
            .setTitle("驳回稿件")
            .setView(et)
            .setPositiveButton("确认驳回") { _, _ ->
                val reason = et.text.toString().trim().ifEmpty { null }
                doAction({ repository.reviewVideo(video.id, false, reason) }, "已驳回")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCommentActions(comment: AdminComment) {
        val videoId = comment.video?.id ?: 0L
        val options = if (videoId > 0) arrayOf("查看稿件", "删除") else arrayOf("删除")
        AlertDialog.Builder(this)
            .setTitle("评论：${comment.content}")
            .setItems(options) { _, which ->
                if (videoId > 0 && which == 0) {
                    openVideo(videoId)
                } else {
                    doAction({ repository.deleteComment(comment.id) }, "已删除")
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showDanmakuActions(danmaku: AdminDanmaku) {
        val videoId = danmaku.video?.id ?: 0L
        val options = if (videoId > 0) arrayOf("查看稿件", "删除") else arrayOf("删除")
        AlertDialog.Builder(this)
            .setTitle("弹幕：${danmaku.content}")
            .setItems(options) { _, which ->
                if (videoId > 0 && which == 0) {
                    openVideo(videoId)
                } else {
                    doAction({ repository.deleteDanmaku(danmaku.id) }, "已删除")
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showReportActions(report: AdminReport) {
        val videoId = report.target?.videoId ?: 0L
        val targetTitle = report.target?.title ?: report.target?.content ?: "-"
        val options = arrayOf("查看对象", "删除违规内容", "忽略")
        AlertDialog.Builder(this)
            .setTitle("举报：${report.reason}\n$targetTitle")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> if (videoId > 0) openVideo(videoId)
                    else Toast.makeText(this@AdminActivity, "该举报对象不是视频", Toast.LENGTH_SHORT).show()
                    1 -> doAction({ repository.handleReport(report.id, "delete") }, "已删除违规内容")
                    2 -> doAction({ repository.handleReport(report.id, "ignore") }, "已忽略")
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showUserActions(user: AdminUser) {
        val banLabel = if (user.banned) "解封" else "封禁"
        val roleLabel = if (user.isAdmin) "取消管理员" else "设为管理员"
        AlertDialog.Builder(this)
            .setTitle("${user.nickname.ifEmpty { user.username }}（UID ${user.id}）")
            .setItems(arrayOf(banLabel, roleLabel)) { _, which ->
                when (which) {
                    0 -> doAction({ repository.banUser(user.id, !user.banned) }, "已$banLabel")
                    1 -> doAction({ repository.setUserRole(user.id, !user.isAdmin) }, "已操作")
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun openVideo(videoId: Long) {
        if (videoId <= 0) {
            Toast.makeText(this, "视频ID无效", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_VIDEO_ID, videoId))
    }

    private fun doAction(action: suspend () -> Result<com.lingxi.tv.data.model.ActionResult>, successMsg: String) {
        lifecycleScope.launch {
            action().onSuccess {
                Toast.makeText(this@AdminActivity, successMsg, Toast.LENGTH_SHORT).show()
                loadModule()
                loadOverview()
            }.onFailure {
                Toast.makeText(this@AdminActivity, "操作失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rvAdmin.adapter = null
    }

    // ==================== Adapter ====================

    class AdminAdapter(private val onClick: (AdminItem) -> Unit) :
        RecyclerView.Adapter<AdminAdapter.VH>() {

        private var items: List<AdminItem> = emptyList()

        fun setData(list: List<AdminItem>) {
            items = list
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvItemTitle)
            val tvSub: TextView = view.findViewById(R.id.tvItemSub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_text_row, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.title
            holder.tvSub.text = item.sub
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
