package com.lingxi.tv.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.lingxi.tv.BuildConfig
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.api.ApiClient
import com.lingxi.tv.data.auth.AuthManager
import com.lingxi.tv.data.model.UserProfile
import com.lingxi.tv.ui.admin.AdminActivity
import com.lingxi.tv.ui.auth.LoginActivity
import com.lingxi.tv.ui.list.VideoListActivity
import com.lingxi.tv.ui.message.MessageActivity
import com.lingxi.tv.ui.settings.SettingsActivity
import com.lingxi.tv.ui.space.UserSpaceActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 我的页
 * 用户信息卡（头像/昵称/UID/等级/经验/签名）、每日签到、
 * 内容统计、功能菜单（历史/收藏/关注/消息/管理后台[UID鉴权]/关于）
 */
class ProfileActivity : AppCompatActivity() {

    private val repository = LingXiApp.get().repository

    private lateinit var ivAvatar: ImageView
    private lateinit var tvNickname: TextView
    private lateinit var tvMeta: TextView
    private lateinit var tvExp: TextView
    private lateinit var tvBio: TextView
    private lateinit var btnCheckin: Button
    private lateinit var menuMessages: TextView

    /** 大会员状态（bindProfile 时更新，VIP 弹窗展示用） */
    private var isVip: Boolean = false
    private var vipLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        if (!AuthManager.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        ivAvatar = findViewById(R.id.ivAvatar)
        tvNickname = findViewById(R.id.tvNickname)
        tvMeta = findViewById(R.id.tvMeta)
        tvExp = findViewById(R.id.tvExp)
        tvBio = findViewById(R.id.tvBio)
        btnCheckin = findViewById(R.id.btnCheckin)

        findViewById<TextView>(R.id.tvSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        btnCheckin.setOnClickListener { doCheckin() }

        findViewById<TextView>(R.id.menuMySpace).setOnClickListener {
            val uid = AuthManager.user?.id ?: 0
            if (uid > 0) {
                startActivity(Intent(this, UserSpaceActivity::class.java)
                    .putExtra(UserSpaceActivity.EXTRA_UID, uid))
            }
        }
        findViewById<TextView>(R.id.menuHistory).setOnClickListener {
            openVideoList(VideoListActivity.TYPE_HISTORY)
        }
        findViewById<TextView>(R.id.menuFavorites).setOnClickListener {
            openVideoList(VideoListActivity.TYPE_FAVORITES)
        }
        findViewById<TextView>(R.id.menuFollowing).setOnClickListener {
            openVideoList(VideoListActivity.TYPE_FOLLOWING)
        }
        findViewById<TextView>(R.id.menuMessages).also { menuMessages = it }.setOnClickListener {
            startActivity(Intent(this, MessageActivity::class.java))
        }
        findViewById<TextView>(R.id.menuCoins).setOnClickListener { showCoins() }
        findViewById<TextView>(R.id.menuVip).setOnClickListener { showVip() }
        // 管理员后台：本地 UID 鉴权，非管理员不显示
        val menuAdmin = findViewById<TextView>(R.id.menuAdmin)
        if (AuthManager.isAdmin) {
            menuAdmin.visibility = View.VISIBLE
            menuAdmin.setOnClickListener {
                startActivity(Intent(this, AdminActivity::class.java))
            }
        }
        findViewById<TextView>(R.id.menuAbout).setOnClickListener { showAbout() }

        // 电视端遥控器：给一个明确的初始焦点，避免进入页面后按键无处可去
        btnCheckin.post { btnCheckin.requestFocus() }

        loadProfile()
    }

    override fun onResume() {
        super.onResume()
        if (AuthManager.isLoggedIn) loadProfile()
    }

    private fun openVideoList(type: String) {
        startActivity(Intent(this, VideoListActivity::class.java)
            .putExtra(VideoListActivity.EXTRA_TYPE, type))
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            repository.getMyProfile().onSuccess { p ->
                bindProfile(p)
            }.onFailure {
                // 网络失败时用本地缓存兜底
                AuthManager.user?.let { u ->
                    tvNickname.text = u.nickname.ifEmpty { u.username }
                    tvMeta.text = "UID ${u.id} · LV${u.level}"
                }
            }
        }
        loadUnreadBadge()
    }

    /** 消息中心未读角标：失败时静默保持原文案 */
    private fun loadUnreadBadge() {
        lifecycleScope.launch {
            repository.getNotificationSummary().onSuccess { s ->
                menuMessages.text =
                    if (s.unread > 0) "消息中心（${s.unread} 条未读）" else "消息中心"
            }
        }
    }

    /** 小鱼干：余额 + 近期变动明细 */
    private fun showCoins() {
        lifecycleScope.launch {
            repository.getMyCoins().onSuccess { c ->
                val sb = StringBuilder("余额：${c.coins} 条小鱼干")
                if (c.recent.isNotEmpty()) {
                    sb.append("\n\n近期变动：")
                    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    c.recent.take(10).forEach { log ->
                        val sign = if (log.change >= 0) "+" else ""
                        val time = if (log.createdAt > 0) "（${fmt.format(Date(log.createdAt))}）" else ""
                        sb.append("\n$sign${log.change} · ${log.reason}$time")
                    }
                }
                AlertDialog.Builder(this@ProfileActivity)
                    .setTitle("我的小鱼干")
                    .setMessage(sb.toString())
                    .setPositiveButton("知道了", null)
                    .show()
            }.onFailure {
                Toast.makeText(this@ProfileActivity, "获取失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 大会员：状态 + 兑换记录，支持兑换码兑换 */
    private fun showVip() {
        lifecycleScope.launch {
            repository.getVipMe().onSuccess { v ->
                val sb = StringBuilder(
                    if (isVip) "当前状态：${vipLabel.ifEmpty { "大会员" }}生效中"
                    else "当前状态：未开通大会员"
                )
                if (v.history.isNotEmpty()) {
                    sb.append("\n\n兑换记录：")
                    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    v.history.take(5).forEach { r ->
                        val time = if (r.createdAt > 0) "（${fmt.format(Date(r.createdAt))}）" else ""
                        sb.append("\n${r.plan} · ${r.days}天$time")
                    }
                }
                AlertDialog.Builder(this@ProfileActivity)
                    .setTitle("大会员")
                    .setMessage(sb.toString())
                    .setPositiveButton("兑换兑换码") { _, _ -> showVipRedeemDialog() }
                    .setNegativeButton("关闭", null)
                    .show()
            }.onFailure {
                Toast.makeText(this@ProfileActivity, "获取会员状态失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showVipRedeemDialog() {
        val input = EditText(this).apply {
            hint = "请输入兑换码"
            setSingleLine()
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(pad, pad, pad, pad)
        AlertDialog.Builder(this)
            .setTitle("兑换大会员")
            .setView(input)
            .setPositiveButton("兑换") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    repository.redeemVip(code).onSuccess {
                        Toast.makeText(this@ProfileActivity, "兑换成功", Toast.LENGTH_SHORT).show()
                        loadProfile()
                    }.onFailure {
                        Toast.makeText(this@ProfileActivity, "兑换失败：${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun bindProfile(p: UserProfile) {
        isVip = p.vip
        vipLabel = p.vipTierLabel
        tvNickname.text = p.nickname.ifEmpty { p.username }
        tvMeta.text = "UID ${p.id} · LV${p.level}" +
            (if (p.vip) " · 👑${p.vipTierLabel.ifEmpty { "大会员" }}" else "") +
            " · ${p.followerCount} 粉丝 · ${p.followingCount} 关注" +
            if (p.badgeLabel.isNotEmpty()) " · ${p.badgeLabel}" else ""
        tvExp.text = if (p.nextLevelExp > 0) "LV${p.level} · ${p.exp}/${p.nextLevelExp} 经验" else "LV${p.level} · ${p.exp} 经验"
        tvBio.text = p.bio
        tvBio.visibility = if (p.bio.isEmpty()) View.GONE else View.VISIBLE

        // 头像为空时显示占位图，避免一片空白
        val avatarUrl = p.getFullAvatarUrl(ApiClient.BASE_URL)
        if (avatarUrl.isEmpty()) {
            ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
        } else {
            Glide.with(this).load(avatarUrl)
                .format(DecodeFormat.PREFER_RGB_565)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .circleCrop().into(ivAvatar)
        }

        findViewById<TextView>(R.id.tvStatWorks).text = "稿件\n${p.videoCount}"
        findViewById<TextView>(R.id.tvStatFav).text = "收藏\n${p.favoriteCount}"
        findViewById<TextView>(R.id.tvStatHistory).text = "历史\n${p.historyCount}"
        findViewById<TextView>(R.id.tvStatFans).text = "粉丝\n${p.followerCount}"
        findViewById<TextView>(R.id.tvStatFollow).text = "关注\n${p.followingCount}"

        btnCheckin.text = if (p.checkedIn) "已签到" else "每日签到"
        btnCheckin.isEnabled = !p.checkedIn
    }

    private fun doCheckin() {
        btnCheckin.isEnabled = false
        lifecycleScope.launch {
            repository.checkin().onSuccess { r ->
                btnCheckin.text = "已签到"
                val msg = when {
                    r.reward > 0 && r.streak > 0 -> "签到成功，经验 +${r.reward}，连续 ${r.streak} 天"
                    r.reward > 0 -> "签到成功，经验 +${r.reward}"
                    r.streak > 0 -> "签到成功，连续 ${r.streak} 天"
                    else -> "签到成功"
                }
                Toast.makeText(this@ProfileActivity, msg, Toast.LENGTH_SHORT).show()
                loadProfile()
            }.onFailure {
                btnCheckin.isEnabled = true
                Toast.makeText(this@ProfileActivity, "签到失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("关于本软件")
            .setMessage(
                "凌汐 TV  v${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）\n\n" +
                    "凌汐弹幕网的电视端客户端，与网页端同源同风格。\n" +
                    "支持视频浏览、弹幕播放、收藏、关注动态、消息与管理后台。\n\n" +
                    "站点：https://lingxi.xiguastudio.top\n" +
                    "本软件为非盈利性质，仅供学习与交流使用。"
            )
            .setPositiveButton("知道了", null)
            .show()
    }
}
