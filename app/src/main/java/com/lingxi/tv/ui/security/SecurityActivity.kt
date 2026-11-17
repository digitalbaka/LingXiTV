package com.lingxi.tv.ui.security

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.auth.AuthManager
import com.lingxi.tv.data.auth.TotpGate
import com.lingxi.tv.data.model.TotpSetupResult
import com.lingxi.tv.data.model.TotpStatus
import kotlinx.coroutines.launch

/**
 * 账号安全 · 双因素认证（TOTP）
 *
 * 绑定：输入当前密码 → setup 拿二维码 SVG 与手输密钥 → 输入 App 6 位码 enable →
 *      展示 8 个一次性恢复码，必须勾选「我已抄写」才能关闭。
 * 解绑：密码 + 一枚有效验证码；解绑后恢复码全部作废，换绑会重新生成一批。
 *
 * 也可作为 403 TOTP_REQUIRED 门禁页：带 EXTRA_FORCE 打开，展示剩余天数并引导绑定。
 */
class SecurityActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FORCE = "force2fa"
        const val EXTRA_DAYS_LEFT = "daysLeft"
        const val EXTRA_DEADLINE = "deadline"
    }

    private val repository = LingXiApp.get().repository

    private lateinit var tvGateNotice: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvRecovery: TextView
    private lateinit var layoutBound: LinearLayout
    private lateinit var layoutBind: LinearLayout
    private lateinit var tvBindHint: TextView
    private lateinit var etSetupPassword: EditText
    private lateinit var btnGetQr: Button
    private lateinit var layoutQr: LinearLayout
    private lateinit var wvQr: WebView
    private lateinit var tvSecret: TextView
    private lateinit var etEnableCode: EditText
    private lateinit var btnEnable: Button
    private lateinit var layoutRecovery: LinearLayout
    private lateinit var tvRecoveryCodes: TextView
    private lateinit var cbRecoveryAck: CheckBox
    private lateinit var btnRecoveryDone: Button

    private var forceGate = false
    private var gateDaysLeft = 0
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)

        forceGate = intent.getBooleanExtra(EXTRA_FORCE, false)
        gateDaysLeft = intent.getIntExtra(EXTRA_DAYS_LEFT, 0)

        tvGateNotice = findViewById(R.id.tvGateNotice)
        tvStatus = findViewById(R.id.tvStatus)
        tvRecovery = findViewById(R.id.tvRecovery)
        layoutBound = findViewById(R.id.layoutBound)
        layoutBind = findViewById(R.id.layoutBind)
        tvBindHint = findViewById(R.id.tvBindHint)
        etSetupPassword = findViewById(R.id.etSetupPassword)
        btnGetQr = findViewById(R.id.btnGetQr)
        layoutQr = findViewById(R.id.layoutQr)
        wvQr = findViewById(R.id.wvQr)
        tvSecret = findViewById(R.id.tvSecret)
        etEnableCode = findViewById(R.id.etEnableCode)
        btnEnable = findViewById(R.id.btnEnable)
        layoutRecovery = findViewById(R.id.layoutRecovery)
        tvRecoveryCodes = findViewById(R.id.tvRecoveryCodes)
        cbRecoveryAck = findViewById(R.id.cbRecoveryAck)
        btnRecoveryDone = findViewById(R.id.btnRecoveryDone)

        wvQr.settings.javaScriptEnabled = false
        wvQr.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        wvQr.settings.domStorageEnabled = false
        wvQr.settings.databaseEnabled = false
        wvQr.webViewClient = WebViewClient()
        wvQr.isVerticalScrollBarEnabled = false
        wvQr.isHorizontalScrollBarEnabled = false

        findViewById<TextView>(R.id.tvDisable).setOnClickListener { showDisableDialog() }
        findViewById<TextView>(R.id.tvRegenerate).setOnClickListener { confirmRegenerateCodes() }
        btnGetQr.setOnClickListener { startSetup() }
        btnEnable.setOnClickListener { confirmEnable() }
        cbRecoveryAck.setOnCheckedChangeListener { _, checked ->
            btnRecoveryDone.isEnabled = checked
        }
        btnRecoveryDone.setOnClickListener { finishRecoveryStep() }

        if (forceGate) {
            showGateNotice(gateDaysLeft)
        }
        loadStatus()
    }

    override fun onResume() {
        super.onResume()
        TotpGate.isSecurityPageVisible = true
    }

    override fun onPause() {
        super.onPause()
        TotpGate.isSecurityPageVisible = false
    }

    // ==================== 状态 ====================

    private fun loadStatus() {
        if (!AuthManager.isLoggedIn) {
            tvStatus.text = "请先登录后再管理双因素认证"
            layoutBind.visibility = View.GONE
            layoutBound.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            repository.getTotpStatus().onSuccess { s ->
                renderStatus(s)
            }.onFailure {
                tvStatus.text = "获取状态失败：${it.message}"
            }
        }
    }

    private fun renderStatus(s: TotpStatus) {
        if (s.enabled) {
            tvStatus.text = "认证器：已绑定"
            layoutBound.visibility = View.VISIBLE
            layoutBind.visibility = View.GONE
            layoutQr.visibility = View.GONE
            tvBindHint.text = ""
            val remaining = s.recoveryRemaining
            tvRecovery.text = when {
                remaining <= 0 -> "恢复码：已用完，建议立即重新生成（每批 8 个，旧码会作废）"
                remaining <= 2 -> "恢复码剩余 ${remaining} 个，建议尽快重新生成"
                else -> "恢复码剩余 ${remaining} 个"
            }
            hideGateNotice()
        } else {
            tvStatus.text = "认证器：未绑定"
            layoutBound.visibility = View.GONE
            layoutBind.visibility = View.VISIBLE
            tvRecovery.text = ""
            if (forceGate || s.required) {
                // 门禁 403 里的 daysLeft 恒为 0；这里用 status 的 daysLeft 展示真实倒计时
                showGateNotice(s.daysLeft)
            } else {
                hideGateNotice()
                tvBindHint.text = "绑定后登录需要输入认证器 App 中显示的 6 位验证码，可有效防止密码泄露导致的盗号。"
            }
        }
    }

    private fun showGateNotice(daysLeft: Int) {
        tvGateNotice.visibility = View.VISIBLE
        tvGateNotice.text = if (daysLeft > 0) {
            "站点已启用强制双因素认证，你的账号还未绑定认证器。\n剩余 $daysLeft 天宽限期，逾期后业务接口将被拦截。"
        } else {
            "站点已启用强制双因素认证，宽限期已结束，未绑定认证器前业务功能受限。\n请立即完成绑定，绑定成功后限制立即解除，无需重新登录。"
        }
    }

    private fun hideGateNotice() {
        tvGateNotice.visibility = View.GONE
    }

    // ==================== 绑定：setup → enable ====================

    private fun startSetup() {
        if (busy) return
        val password = etSetupPassword.text.toString()
        if (password.isEmpty()) {
            tvBindHint.text = "请输入当前登录密码"
            etSetupPassword.requestFocus()
            return
        }
        busy = true
        btnGetQr.isEnabled = false
        btnGetQr.text = "获取中…"
        lifecycleScope.launch {
            repository.setupTotp(password).onSuccess { setup ->
                renderSetup(setup)
            }.onFailure { e ->
                val msg = e.message ?: "获取二维码失败"
                tvBindHint.text = msg
                Toast.makeText(this@SecurityActivity, msg, Toast.LENGTH_LONG).show()
                if (msg.contains("已经绑定过")) loadStatus()
                btnGetQr.isEnabled = true
                btnGetQr.text = "获取二维码"
            }
            busy = false
        }
    }

    private fun renderSetup(setup: TotpSetupResult) {
        layoutQr.visibility = View.VISIBLE
        etSetupPassword.isEnabled = false
        btnGetQr.isEnabled = false
        tvBindHint.text = "用认证器 App 扫描二维码；扫不了时手动输入下方密钥。"
        renderQr(setup.qr)
        tvSecret.text = "密钥（手动输入）：${formatSecret(setup.secret)}\n" +
            "账号：${setup.account.ifEmpty { "当前账号" }}　签发方：${setup.issuer.ifEmpty { "凌汐" }}"
        etEnableCode.setText("")
        etEnableCode.requestFocus()
    }

    /** 服务端 qr 是原始 SVG 源码；必须清洗根标签的 width/height，避免 WebView 严格 XML 解析报错 */
    private fun renderQr(raw: String) {
        var svg = raw.trim()
            .replace(Regex("^<\\?xml[^>]*\\?>\\s*"), "")
            .replace(Regex("<!DOCTYPE[^>]*>\\s*", RegexOption.IGNORE_CASE), "")
        val rootTagRegex = Regex("<svg\\b[^>]*>", RegexOption.IGNORE_CASE)
        val attrRegex = Regex("\\s(width|height|preserveAspectRatio)\\s*=\\s*(\"[^\"]*\"|'[^']*')")
        val root = rootTagRegex.find(svg)
        if (root != null) {
            val cleaned = attrRegex.replace(root.value, "")
                .replaceFirst("<svg", "<svg width=\"100%\" height=\"100%\" preserveAspectRatio=\"xMidYMid meet\"")
            svg = svg.replaceRange(root.range, cleaned)
        }
        val html = """<html><head><meta name="viewport" content="width=300,initial-scale=1"/></head>
            <body style="margin:0;padding:10px;background:#ffffff;display:flex;align-items:center;justify-content:center;height:100%;box-sizing:border-box;">$svg</body></html>"""
        wvQr.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    /** base32 密钥按 4 位分组，方便电视端手输核对 */
    private fun formatSecret(secret: String): String {
        if (secret.isEmpty()) return secret
        return secret.chunked(4).joinToString(" ")
    }

    private fun confirmEnable() {
        if (busy) return
        val code = etEnableCode.text.toString().trim()
        if (code.length != 6 || !code.all { it.isDigit() }) {
            tvBindHint.text = "请输入认证器 App 中的 6 位数字验证码"
            etEnableCode.requestFocus()
            return
        }
        busy = true
        btnEnable.isEnabled = false
        btnEnable.text = "绑定中…"
        lifecycleScope.launch {
            repository.enableTotp(code).onSuccess { result ->
                busy = false
                btnEnable.isEnabled = true
                btnEnable.text = "确认绑定"
                val codes = result.codes
                if (codes.isEmpty()) {
                    Toast.makeText(this@SecurityActivity, "绑定成功", Toast.LENGTH_SHORT).show()
                    loadStatus()
                } else {
                    showRecoveryCodes(codes)
                }
            }.onFailure { e ->
                busy = false
                btnEnable.isEnabled = true
                btnEnable.text = "确认绑定"
                val msg = e.message ?: "绑定失败"
                tvBindHint.text = if (msg.contains("已经用过")) {
                    "$msg\n请等下一个验证码再试，不要重复提交。"
                } else msg
                Toast.makeText(this@SecurityActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== 恢复码展示（只出现这一次） ====================

    private fun showRecoveryCodes(codes: List<String>) {
        layoutBind.visibility = View.GONE
        layoutBound.visibility = View.GONE
        layoutRecovery.visibility = View.VISIBLE
        // 两列展示，电视大屏更容易看清
        tvRecoveryCodes.text = codes.chunked(2).joinToString("\n") { row ->
            row.joinToString("      ")
        }
        cbRecoveryAck.isChecked = false
        btnRecoveryDone.isEnabled = false
        cbRecoveryAck.requestFocus()
    }

    private fun finishRecoveryStep() {
        if (!cbRecoveryAck.isChecked) return
        layoutRecovery.visibility = View.GONE
        if (forceGate) {
            Toast.makeText(this, "绑定完成，功能限制已解除", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            loadStatus()
        }
    }

    // ==================== 解绑 / 换绑 ====================

    private fun showDisableDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val etPassword = EditText(this).apply {
            hint = "当前登录密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val etCode = EditText(this).apply {
            hint = "认证器当前 6 位验证码"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            filters = arrayOf<android.text.InputFilter>(
                android.text.InputFilter.LengthFilter(6)
            )
        }
        layout.addView(etPassword)
        layout.addView(etCode)

        AlertDialog.Builder(this)
            .setTitle("解绑 / 更换认证器")
            .setMessage("解绑需要当前密码 + 一枚有效验证码。\n" +
                "解绑后本批恢复码立即全部作废；重新绑定会生成一批新的恢复码，旧码无法再使用。")
            .setView(layout)
            .setPositiveButton("确认解绑") { _, _ ->
                val password = etPassword.text.toString()
                val code = etCode.text.toString().trim()
                if (password.isEmpty() || code.length != 6) {
                    Toast.makeText(this, "请填写密码与 6 位验证码", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                doDisable(password, code)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doDisable(password: String, code: String) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            repository.disableTotp(password, code).onSuccess {
                busy = false
                Toast.makeText(this@SecurityActivity, "已解绑，恢复码已全部作废", Toast.LENGTH_LONG).show()
                etSetupPassword.isEnabled = true
                etSetupPassword.setText("")
                tvBindHint.text = "你已解绑认证器。如需换绑，请输入当前密码重新获取二维码。"
                loadStatus()
            }.onFailure { e ->
                busy = false
                val msg = e.message ?: "解绑失败"
                Toast.makeText(this@SecurityActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ==================== 重新生成恢复码 ====================

    private fun confirmRegenerateCodes() {
        AlertDialog.Builder(this)
            .setTitle("重新生成恢复码")
            .setMessage("将生成新的 8 个恢复码，旧的一批立即全部作废。\n" +
                "新码只显示一次，请准备好纸笔再继续。")
            .setPositiveButton("生成") { _, _ -> doRegenerate() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doRegenerate() {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            repository.regenerateRecoveryCodes().onSuccess { result ->
                busy = false
                showRecoveryCodes(result.codes)
            }.onFailure { e ->
                busy = false
                Toast.makeText(this@SecurityActivity, "生成失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wvQr.stopLoading()
        wvQr.loadUrl("about:blank")
        wvQr.clearHistory()
        (wvQr.parent as? ViewGroup)?.removeView(wvQr)
        wvQr.destroy()
    }
}
