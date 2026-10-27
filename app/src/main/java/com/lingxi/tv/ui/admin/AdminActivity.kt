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
import com.lingxi.tv.data.model.AdminReport
import com.lingxi.tv.data.model.AdminUser
import com.lingxi.tv.data.model.AdminVideo
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

        loadOverview()
        switchModule(Module.VIDEOS)
    }

    private fun loadOverview() {
        lifecycleScope.launch {
            repository.getAdminOverview().onSuccess { o ->
                tvOverview.text = "待审稿件 ${o.pendingVideos} · 待处理举报 ${o.pendingReports}" +
                    " · 待审核认证 ${o.pendingVerifications} · 创作者申请 ${o.pendingCreators}\n" +
                    "用户 ${o.totalUsers} · 稿件 ${o.totalVideos}" +
                    " · 今日新增用户 ${o.todayUsers} · 稿件 ${o.todayVideos}" +
                    " · 弹幕 ${o.todayDanmaku} · 评论 ${o.todayComments}"
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
        loadModule()
    }

    private fun loadModule() {
        val keyword = etSearch.text.toString().trim().ifEmpty { null }
        lifecycleScope.launch {
            when (module) {
                Module.VIDEOS -> repository.getAdminVideos().onSuccess { page ->
                    showItems(page.items.map {
                        AdminItem(it.title, "${it.author} · UID${it.uid} · ${it.partition} · ${it.status}", it)
                    })
                }
                Module.COMMENTS -> repository.getAdminComments(keyword).onSuccess { page ->
                    showItems(page.items.map {
                        AdminItem(it.content, "${it.nickname} · 来自《${it.videoTitle}》", it)
                    })
                }
                Module.DANMAKU -> repository.getAdminDanmaku(keyword).onSuccess { page ->
                    showItems(page.items.map {
                        AdminItem(it.content, "${it.nickname} · 视频 ${it.videoId}", it)
                    })
                }
                Module.REPORTS -> repository.getAdminReports().onSuccess { page ->
                    showItems(page.items.map {
                        AdminItem("${it.targetType}: ${it.reason}",
                            "举报人：${it.reporter} · ${it.status}", it)
                    })
                }
                Module.USERS -> repository.getAdminUsers(keyword).onSuccess { page ->
                    showItems(page.items.map {
                        val roleLabel = if (it.role == "admin") "管理员" else if (it.banned) "已封禁" else "普通用户"
                        AdminItem("${it.nickname.ifEmpty { it.username }}（UID ${it.id}）",
                            "@${it.username} · LV${it.level} · ${it.exp} 经验 · 稿件 ${it.videoCount} · $roleLabel", it)
                    })
                }
            }.onFailure {
                Toast.makeText(this@AdminActivity, "加载失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showItems(items: List<AdminItem>) {
        adapter.setData(items)
        tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    /** 点击列表项，根据类型弹出对应操作 */
    private fun onItemAction(item: AdminItem) {
        when (val data = item.data) {
            is AdminVideo -> showVideoActions(data)
            is AdminComment -> showCommentActions(data)
            is AdminReport -> showReportActions(data)
            is AdminUser -> showUserActions(data)
        }
    }

    private fun showVideoActions(video: AdminVideo) {
        val options = arrayOf("查看播放", "通过", "驳回", "置顶/取消置顶")
        AlertDialog.Builder(this)
            .setTitle(video.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_VIDEO_ID, video.id))
                    1 -> doAction({ repository.reviewVideo(video.id, "approve") }, "已通过")
                    2 -> showRejectDialog(video)
                    3 -> doAction({ repository.pinVideo(video.id) }, "已操作")
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
                doAction({ repository.reviewVideo(video.id, "reject", reason) }, "已驳回")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCommentActions(comment: AdminComment) {
        AlertDialog.Builder(this)
            .setTitle("评论：${comment.content}")
            .setItems(arrayOf("查看稿件", "删除")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_VIDEO_ID, comment.videoId))
                    1 -> doAction({ repository.deleteComment(comment.id) }, "已删除")
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showReportActions(report: AdminReport) {
        AlertDialog.Builder(this)
            .setTitle("举报：${report.reason}")
            .setItems(arrayOf("查看对象", "标记已处理", "忽略")) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, PlayerActivity::class.java)
                        .putExtra(PlayerActivity.EXTRA_VIDEO_ID, report.targetId))
                    1 -> doAction({ repository.handleReport(report.id, "processed") }, "已处理")
                    2 -> doAction({ repository.handleReport(report.id, "ignored") }, "已忽略")
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showUserActions(user: AdminUser) {
        val banLabel = if (user.banned) "解封" else "封禁"
        val roleLabel = if (user.role == "admin") "取消管理员" else "设为管理员"
        AlertDialog.Builder(this)
            .setTitle("${user.nickname.ifEmpty { user.username }}（UID ${user.id}）")
            .setItems(arrayOf(banLabel, roleLabel)) { _, which ->
                when (which) {
                    0 -> doAction({ repository.banUser(user.id) }, "已$banLabel")
                    1 -> doAction({
                        repository.setUserRole(user.id, if (user.role == "admin") "user" else "admin")
                    }, "已操作")
                }
            }
            .setNegativeButton("关闭", null)
            .show()
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
