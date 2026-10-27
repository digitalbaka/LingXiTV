package com.lingxi.tv.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
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
import kotlinx.coroutines.launch

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

        findViewById<TextView>(R.id.menuHistory).setOnClickListener {
            openVideoList(VideoListActivity.TYPE_HISTORY)
        }
        findViewById<TextView>(R.id.menuFavorites).setOnClickListener {
            openVideoList(VideoListActivity.TYPE_FAVORITES)
        }
        findViewById<TextView>(R.id.menuFollowing).setOnClickListener {
            openVideoList(VideoListActivity.TYPE_FOLLOWING)
        }
        findViewById<TextView>(R.id.menuMessages).setOnClickListener {
            startActivity(Intent(this, MessageActivity::class.java))
        }
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
    }

    private fun bindProfile(p: UserProfile) {
        tvNickname.text = p.nickname.ifEmpty { p.username }
        tvMeta.text = "UID ${p.id} · LV${p.level} · ${p.followerCount} 粉丝 · ${p.followingCount} 关注" +
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
