package com.lingxi.tv.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.api.ApiClient
import com.lingxi.tv.data.auth.AuthManager
import com.lingxi.tv.data.model.UserProfile
import com.lingxi.tv.player.DanmakuSettings
import com.lingxi.tv.player.VideoCacheManager
import com.lingxi.tv.ui.node.NodeSelectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页
 * 个人资料（编辑昵称与签名）/ 网络（接入节点、清除缓存）/
 * 播放与弹幕（弹幕设置、主题外观）/ 账号安全（修改密码、退出登录）
 */
class SettingsActivity : AppCompatActivity() {

    private val repository = LingXiApp.get().repository

    private lateinit var ivAvatar: ImageView
    private lateinit var tvNickname: TextView
    private lateinit var tvUidLevel: TextView
    private lateinit var tvNode: TextView
    private lateinit var tvClearCache: TextView
    private lateinit var tvDanmakuSettings: TextView
    private lateinit var tvVersion: TextView

    private var profile: UserProfile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        ivAvatar = findViewById(R.id.ivAvatar)
        tvNickname = findViewById(R.id.tvNickname)
        tvUidLevel = findViewById(R.id.tvUidLevel)
        tvNode = findViewById(R.id.tvNode)
        tvClearCache = findViewById(R.id.tvClearCache)
        tvDanmakuSettings = findViewById(R.id.tvDanmakuSettings)
        tvVersion = findViewById(R.id.tvVersion)

        findViewById<TextView>(R.id.tvEditProfile).setOnClickListener { showEditProfileDialog() }
        tvNode.setOnClickListener {
            startActivity(Intent(this, NodeSelectActivity::class.java))
        }
        tvClearCache.setOnClickListener { showClearCacheDialog() }
        tvDanmakuSettings.setOnClickListener { showDanmakuSettingsDialog() }
        findViewById<TextView>(R.id.tvTheme).setOnClickListener { showThemeDialog() }
        findViewById<TextView>(R.id.tvChangePassword).setOnClickListener { showChangePasswordDialog() }
        findViewById<TextView>(R.id.tvLogout).setOnClickListener { confirmLogout() }

        // 电视端遥控器初始焦点
        findViewById<TextView>(R.id.tvEditProfile).post {
            findViewById<TextView>(R.id.tvEditProfile).requestFocus()
        }

        tvVersion.text = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            "v${info.versionName} · 与网页端同源同风格"
        } catch (e: Exception) { "" }

        updateNodeLabel()
        updateCacheLabel()
        updateDanmakuLabel()
        loadProfile()
    }

    override fun onResume() {
        super.onResume()
        updateNodeLabel()
        updateCacheLabel()
    }

    private fun loadProfile() {
        if (!AuthManager.isLoggedIn) {
            tvNickname.text = "未登录"
            tvUidLevel.text = ""
            return
        }
        // 先用本地缓存展示，再从网络刷新
        AuthManager.user?.let {
            tvNickname.text = it.nickname.ifEmpty { it.username }
            tvUidLevel.text = "UID ${it.id} · LV${it.level}"
            val url = it.getFullAvatarUrl(ApiClient.BASE_URL)
            if (url.isEmpty()) ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
            else Glide.with(this).load(url)
                .placeholder(R.drawable.ic_avatar_placeholder)
                .error(R.drawable.ic_avatar_placeholder)
                .circleCrop().into(ivAvatar)
        }
        lifecycleScope.launch {
            repository.getMyProfile().onSuccess { p ->
                profile = p
                tvNickname.text = p.nickname.ifEmpty { p.username }
                tvUidLevel.text = "UID ${p.id} · LV${p.level}"
                val url = p.getFullAvatarUrl(ApiClient.BASE_URL)
                if (url.isEmpty()) {
                    ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
                } else {
                    Glide.with(this@SettingsActivity)
                        .load(url)
                        .placeholder(R.drawable.ic_avatar_placeholder)
                        .error(R.drawable.ic_avatar_placeholder)
                        .circleCrop().into(ivAvatar)
                }
            }
        }
    }

    private fun updateNodeLabel() {
        tvNode.text = "接入节点：${AuthManager.nodeName ?: "主站"}"
    }

    private fun updateCacheLabel() {
        val bytes = VideoCacheManager.getCacheSizeBytes(this)
        val mb = bytes / (1024 * 1024)
        tvClearCache.text = "清除缓存（视频缓存 ${mb} MB）"
    }

    private fun updateDanmakuLabel() {
        val alphaPercent = DanmakuSettings.alpha * 100 / 255
        tvDanmakuSettings.text = "弹幕设置（字号 ×${"%.1f".format(DanmakuSettings.textScale)}" +
            " · 速度 ×${"%.1f".format(DanmakuSettings.speedScale)}" +
            " · 不透明度 ${alphaPercent}%" +
            " · 屏蔽词 ${DanmakuSettings.blockWords.size} 个）"
    }

    // ==================== 编辑昵称与签名 ====================

    private fun showEditProfileDialog() {
        if (!AuthManager.isLoggedIn) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etNickname = EditText(this).apply {
            hint = "昵称"
            setText(profile?.nickname ?: AuthManager.user?.nickname ?: "")
        }
        val etBio = EditText(this).apply {
            hint = "签名"
            setText(profile?.bio ?: "")
        }
        layout.addView(etNickname)
        layout.addView(etBio)

        AlertDialog.Builder(this)
            .setTitle("编辑昵称与签名")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val nickname = etNickname.text.toString().trim()
                val bio = etBio.text.toString().trim()
                lifecycleScope.launch {
                    repository.updateMyProfile(
                        nickname.ifEmpty { null },
                        bio.ifEmpty { null }
                    ).onSuccess { p ->
                        profile = p
                        tvNickname.text = p.nickname.ifEmpty { p.username }
                        Toast.makeText(this@SettingsActivity, "已保存", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this@SettingsActivity, "保存失败：${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 清除缓存 ====================

    private fun showClearCacheDialog() {
        AlertDialog.Builder(this)
            .setTitle("清除缓存")
            .setMessage("将清除视频磁盘缓存和图片缓存，不影响登录状态。")
            .setPositiveButton("清除") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        VideoCacheManager.clearCache(this@SettingsActivity)
                        Glide.get(this@SettingsActivity).clearDiskCache()
                    }
                    Glide.get(this@SettingsActivity).clearMemory()
                    Toast.makeText(this@SettingsActivity, "缓存已清除", Toast.LENGTH_SHORT).show()
                    updateCacheLabel()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 弹幕设置 ====================

    private fun showDanmakuSettingsDialog() {
        val options = arrayOf(
            "字号：当前 ×${"%.1f".format(DanmakuSettings.textScale)}",
            "速度：当前 ×${"%.1f".format(DanmakuSettings.speedScale)}",
            "不透明度：当前 ${DanmakuSettings.alpha * 100 / 255}%",
            "屏蔽词：当前 ${DanmakuSettings.blockWords.size} 个"
        )
        AlertDialog.Builder(this)
            .setTitle("弹幕设置")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showTextScaleDialog()
                    1 -> showSpeedScaleDialog()
                    2 -> showAlphaDialog()
                    3 -> showBlockWordsDialog()
                }
            }
            .setNegativeButton("关闭") { _, _ -> updateDanmakuLabel() }
            .show()
    }

    private fun showTextScaleDialog() {
        val labels = arrayOf("小 (×1.0)", "标准 (×1.4)", "大 (×1.8)", "特大 (×2.2)")
        val values = floatArrayOf(1.0f, 1.4f, 1.8f, 2.2f)
        val current = values.indexOfFirst { it == DanmakuSettings.textScale }.coerceAtLeast(1)
        AlertDialog.Builder(this)
            .setTitle("弹幕字号")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                DanmakuSettings.textScale = values[which]
                dialog.dismiss()
                updateDanmakuLabel()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSpeedScaleDialog() {
        val labels = arrayOf("慢 (×0.5)", "标准 (×1.0)", "快 (×1.5)", "很快 (×2.0)")
        val values = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f)
        val current = values.indexOfFirst { it == DanmakuSettings.speedScale }.coerceAtLeast(1)
        AlertDialog.Builder(this)
            .setTitle("弹幕速度")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                DanmakuSettings.speedScale = values[which]
                dialog.dismiss()
                updateDanmakuLabel()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAlphaDialog() {
        val labels = arrayOf("25%", "50%", "75%", "100%")
        val values = intArrayOf(64, 128, 191, 255)
        val current = values.indexOfFirst { it == DanmakuSettings.alpha }.coerceAtLeast(3)
        AlertDialog.Builder(this)
            .setTitle("弹幕不透明度")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                DanmakuSettings.alpha = values[which]
                dialog.dismiss()
                updateDanmakuLabel()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showBlockWordsDialog() {
        val et = EditText(this).apply {
            hint = "多个词用逗号分隔"
            setText(DanmakuSettings.blockWords.joinToString(","))
        }
        AlertDialog.Builder(this)
            .setTitle("屏蔽词")
            .setView(et)
            .setPositiveButton("保存") { _, _ ->
                DanmakuSettings.blockWords = et.text.toString()
                    .split(",", "，")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                updateDanmakuLabel()
                Toast.makeText(this, "屏蔽词已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 主题外观 ====================

    private fun showThemeDialog() {
        AlertDialog.Builder(this)
            .setTitle("主题外观")
            .setMessage("TV 版固定使用深色主题，与网页端同源同风格。\n\n" +
                "浅色主题在电视大屏上对比度较低，且遥控器焦点不易识别，因此不提供切换。")
            .setPositiveButton("知道了", null)
            .show()
    }

    // ==================== 修改密码 ====================

    private fun showChangePasswordDialog() {
        if (!AuthManager.isLoggedIn) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etOld = EditText(this).apply {
            hint = "当前密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etNew = EditText(this).apply {
            hint = "新密码（至少 6 位）"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(etOld)
        layout.addView(etNew)

        AlertDialog.Builder(this)
            .setTitle("修改密码")
            .setView(layout)
            .setPositiveButton("确认修改") { _, _ ->
                val oldPwd = etOld.text.toString()
                val newPwd = etNew.text.toString()
                if (oldPwd.isEmpty() || newPwd.length < 6) {
                    Toast.makeText(this, "请完整输入，新密码至少 6 位", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    repository.changePassword(oldPwd, newPwd).onSuccess {
                        Toast.makeText(this@SettingsActivity, "密码已修改", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this@SettingsActivity, "修改失败：${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 退出登录 ====================

    private fun confirmLogout() {
        if (!AuthManager.isLoggedIn) {
            Toast.makeText(this, "当前未登录", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("退出后需要重新登录才能使用历史、收藏等功能。")
            .setPositiveButton("退出") { _, _ ->
                lifecycleScope.launch {
                    repository.logout()
                    AuthManager.logout()
                    loadProfile()
                    Toast.makeText(this@SettingsActivity, "已退出登录", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
