package com.lingxi.tv.data.auth

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.ui.security.SecurityActivity

/**
 * 全局门禁：识别 HTTP 403 + data.code === 'TOTP_REQUIRED'。
 *
 * 封禁也是 403，所以只看状态码不够，必须认 data.code。
 * 命中后跳转到绑定/安全页并展示剩余天数，而不是提示「没有权限」。
 */
object TotpGate {

    private const val TAG = "TotpGate"
    private const val DEBOUNCE_MS = 3000L

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 安全页在前台时不再重复拉起 */
    @Volatile
    var isSecurityPageVisible: Boolean = false

    private var lastNotifyAt: Long = 0L

    fun onRequired(daysLeft: Int, deadline: Long) {
        val now = SystemClock.elapsedRealtime()
        if (isSecurityPageVisible || now - lastNotifyAt < DEBOUNCE_MS) return
        lastNotifyAt = now
        Log.i(TAG, "TOTP_REQUIRED gate hit, daysLeft=$daysLeft")
        mainHandler.post {
            try {
                val ctx = LingXiApp.get()
                val intent = Intent(ctx, SecurityActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(SecurityActivity.EXTRA_FORCE, true)
                    .putExtra(SecurityActivity.EXTRA_DAYS_LEFT, daysLeft)
                    .putExtra(SecurityActivity.EXTRA_DEADLINE, deadline)
                ctx.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "open security page failed: ${e.message}")
            }
        }
    }
}
