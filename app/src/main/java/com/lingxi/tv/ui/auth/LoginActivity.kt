package com.lingxi.tv.ui.auth

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.auth.AuthManager
import com.lingxi.tv.data.model.Captcha
import com.lingxi.tv.data.model.LoginResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 登录页（电视遥控器友好）
 *
 * 1. 账号密码登录 → 响应 data.mfaRequired=true 时进入验证码步骤，
 *    按 mfaMethod 分流：totp（认证器 6 位码 / 恢复码）或 email（图形验证码 + 邮箱验证码）
 * 2. 邮箱验证码发送到绑定邮箱，60 秒冷却后才能重发；设备受信时可直接登录
 * 3. 验证并登录 → 持久化 accessToken / refreshToken / deviceToken，下次免登录
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        private const val METHOD_TOTP = "totp"
        private const val METHOD_EMAIL = "email"
    }

    private val repository = LingXiApp.get().repository

    private lateinit var layoutLogin: LinearLayout
    private lateinit var layout2fa: LinearLayout
    private lateinit var layoutTotp: LinearLayout
    private lateinit var layoutEmail: LinearLayout
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tv2faNotice: TextView
    private lateinit var tvTotpCodeLabel: TextView
    private lateinit var etTotpCode: EditText
    private lateinit var cbUseRecovery: CheckBox
    private lateinit var wvCaptcha: WebView
    private lateinit var etCaptchaCode: EditText
    private lateinit var btnRefreshCaptcha: Button
    private lateinit var etEmailCode: EditText
    private lateinit var btnSendCode: Button
    private lateinit var cbRememberDevice: CheckBox
    private lateinit var btnVerify: Button
    private lateinit var tvBackLogin: TextView
    private lateinit var tvStatus: TextView

    private var mfaTicket: String? = null
    private var mfaMethod: String = METHOD_TOTP
    private var captcha: Captcha? = null
    private var countdownJob: Job? = null
    private var verifying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        layoutLogin = findViewById(R.id.layoutLogin)
        layout2fa = findViewById(R.id.layout2fa)
        layoutTotp = findViewById(R.id.layoutTotp)
        layoutEmail = findViewById(R.id.layoutEmail)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tv2faNotice = findViewById(R.id.tv2faNotice)
        tvTotpCodeLabel = findViewById(R.id.tvTotpCodeLabel)
        etTotpCode = findViewById(R.id.etTotpCode)
        cbUseRecovery = findViewById(R.id.cbUseRecovery)
        wvCaptcha = findViewById(R.id.wvCaptcha)
        etCaptchaCode = findViewById(R.id.etCaptchaCode)
        btnRefreshCaptcha = findViewById(R.id.btnRefreshCaptcha)
        etEmailCode = findViewById(R.id.etEmailCode)
        btnSendCode = findViewById(R.id.btnSendCode)
        cbRememberDevice = findViewById(R.id.cbRememberDevice)
        btnVerify = findViewById(R.id.btnVerify)
        tvBackLogin = findViewById(R.id.tvBackLogin)
        tvStatus = findViewById(R.id.tvStatus)

        // WebView 只渲染静态 SVG，禁用一切多余能力省内存
        wvCaptcha.settings.javaScriptEnabled = false
        wvCaptcha.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        wvCaptcha.settings.domStorageEnabled = false
        wvCaptcha.settings.databaseEnabled = false
        wvCaptcha.webViewClient = WebViewClient()
        wvCaptcha.isVerticalScrollBarEnabled = false
        wvCaptcha.isHorizontalScrollBarEnabled = false

        btnLogin.setOnClickListener { doLogin() }
        btnRefreshCaptcha.setOnClickListener { loadCaptcha() }
        btnSendCode.setOnClickListener { sendCode() }
        btnVerify.setOnClickListener { verifyAndLogin() }
        tvBackLogin.setOnClickListener { backToLogin() }
        cbUseRecovery.setOnCheckedChangeListener { _, checked -> applyRecoveryMode(checked) }

        etUsername.requestFocus()
    }

    /** 切换「认证器验证码 / 恢复码」输入形态 */
    private fun applyRecoveryMode(useRecovery: Boolean) {
        if (useRecovery) {
            tvTotpCodeLabel.text = "一次性恢复码（格式 A1B2-C3D4）"
            etTotpCode.hint = "输入恢复码"
            etTotpCode.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            etTotpCode.filters = emptyArray()
        } else {
            tvTotpCodeLabel.text = "认证器验证码（6 位数字）"
            etTotpCode.hint = "6 位数字"
            etTotpCode.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            etTotpCode.filters = arrayOf<android.text.InputFilter>(
                android.text.InputFilter.LengthFilter(6)
            )
        }
        etTotpCode.setText("")
        etTotpCode.requestFocus()
    }

    private fun showStatus(msg: String?) {
        if (msg.isNullOrEmpty()) {
            tvStatus.visibility = View.GONE
        } else {
            tvStatus.text = msg
            tvStatus.visibility = View.VISIBLE
        }
    }

    // ==================== 第一步：账号密码登录 ====================

    private fun doLogin() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            showStatus("请输入账号和密码")
            return
        }
        btnLogin.isEnabled = false
        showStatus("登录中…")
        lifecycleScope.launch {
            repository.login(username, password).onSuccess { result ->
                if (result.mfaRequired) {
                    // 密码正确 ≠ 拿到登录态：站点要求二次验证，切到验证码步骤
                    enter2fa(result)
                } else {
                    finishLogin(result)
                }
            }.onFailure {
                btnLogin.isEnabled = true
                showStatus("登录失败：${it.message}")
            }
        }
    }

    /** 切换到两步验证卡片：mfaMethod 决定走认证器还是邮箱验证码 */
    private fun enter2fa(result: LoginResult) {
        btnLogin.isEnabled = true
        showStatus(null)
        mfaTicket = result.mfaTicket
        mfaMethod = when {
            result.mfaMethod == METHOD_EMAIL -> METHOD_EMAIL
            // 兼容旧服务端：没有 mfaMethod 但给了脱敏邮箱，说明走邮箱验证码
            result.mfaMethod.isNullOrEmpty() && !result.emailMasked.isNullOrEmpty() -> METHOD_EMAIL
            else -> METHOD_TOTP
        }
        layoutLogin.visibility = View.GONE
        layout2fa.visibility = View.VISIBLE
        etTotpCode.setText("")
        etEmailCode.setText("")
        etCaptchaCode.setText("")

        if (mfaMethod == METHOD_EMAIL) {
            layoutEmail.visibility = View.VISIBLE
            layoutTotp.visibility = View.GONE
            tv2faNotice.text = "为保障账号安全，请完成邮箱二次验证。" +
                "验证码将发送到 ${result.emailMasked ?: "绑定邮箱"}。"
            loadCaptcha()
            etCaptchaCode.requestFocus()
        } else {
            layoutEmail.visibility = View.GONE
            layoutTotp.visibility = View.VISIBLE
            tv2faNotice.text = "该账号已开启认证器（TOTP）双因素认证。" +
                "请输入认证器 App 中显示的 6 位验证码；手机丢失时可勾选下方恢复码选项。"
            // 还没生成过恢复码（或已用完）时不显示恢复码入口
            cbUseRecovery.visibility = if (result.canUseRecovery) View.VISIBLE else View.GONE
            cbUseRecovery.isChecked = false
            applyRecoveryMode(false)
        }
    }

    // ==================== 图形验证码 ====================

    private fun loadCaptcha() {
        btnRefreshCaptcha.isEnabled = false
        lifecycleScope.launch {
            repository.getCaptcha().onSuccess { c ->
                captcha = c
                btnRefreshCaptcha.isEnabled = true
                renderCaptcha(c.svg)
            }.onFailure {
                btnRefreshCaptcha.isEnabled = true
                Toast.makeText(this@LoginActivity, "验证码加载失败：${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderCaptcha(raw: String) {
        // 服务端 SVG 根标签自带 width/height 属性，若直接追加同名属性会被 WebView
        // 严格 XML 解析判为 "Attribute width redefined" 导致整页报错。先清洗再注入。
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

        val html = """<html><head><meta name="viewport" content="width=150,initial-scale=1"/></head>
            <body style="margin:0;padding:0;background:#2A2A2A;display:flex;align-items:center;justify-content:center;height:100%;">$svg</body></html>"""
        // 必须用 text/html + loadDataWithBaseURL：
        // image/svg+xml 会触发严格 XML 解析；loadData 会把 % 当 URL 编码导致 100% 尺寸失效
        wvCaptcha.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    // ==================== 第二步：发送邮箱验证码（60 秒冷却） ====================

    private fun sendCode() {
        val ticket = mfaTicket
        val captchaCode = etCaptchaCode.text.toString().trim()
        if (ticket.isNullOrEmpty()) {
            showStatus("登录会话已失效，请返回重新登录")
            return
        }
        if (captchaCode.isEmpty()) {
            showStatus("请先输入图形验证码")
            return
        }
        // 图形验证码已被消耗（null）或加载过久（id 为空），必须先刷新
        val captchaId = captcha?.id
        if (captchaId.isNullOrEmpty()) {
            showStatus("图形验证码已过期，请稍候…")
            loadCaptcha()
            return
        }
        btnSendCode.isEnabled = false
        btnSendCode.text = "发送中…"
        showStatus("验证码发送中…")
        lifecycleScope.launch {
            repository.sendEmailCode(ticket, captchaId, captchaCode)
                .onSuccess {
                    showStatus("验证码已发送到绑定邮箱，请查收")
                    Toast.makeText(this@LoginActivity, "验证码已发送，请查收邮件", Toast.LENGTH_SHORT).show()
                    startCountdown()
                }
                .onFailure { e ->
                    btnSendCode.isEnabled = true
                    btnSendCode.text = "发送验证码"
                    val msg = e.message ?: "发送失败"
                    showStatus("发送失败：$msg")
                    Toast.makeText(this@LoginActivity, "发送失败：$msg", Toast.LENGTH_LONG).show()
                    // 判断失败原因：
                    // - 图形验证码相关错误（400 + captcha 关键字）→ 刷新图形验证码，清空用户输入
                    // - 服务器 5xx / 网络错误 → 不刷新，保持用户输入让其重试
                    // - 票据无效 / 其他 → 不刷新
                    val isCaptchaError = msg.contains("图形验证码") || msg.contains("captcha", ignoreCase = true) ||
                        msg.contains("验证码不正确") || msg.contains("验证码已过期")
                    if (isCaptchaError) {
                        loadCaptcha()
                        etCaptchaCode.setText("")
                    }
                }
        }
    }

    /** 发送冷却 60 秒倒计时 */
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            for (sec in 60 downTo 1) {
                btnSendCode.text = "${sec}s 后重发"
                delay(1000)
            }
            btnSendCode.text = "发送验证码"
            btnSendCode.isEnabled = true
        }
    }

    // ==================== 第三步：验证并登录 ====================

    private fun verifyAndLogin() {
        if (verifying) return
        val ticket = mfaTicket
        if (ticket.isNullOrEmpty()) {
            showStatus("登录会话已失效，请返回重新登录")
            return
        }
        val useRecovery = mfaMethod == METHOD_TOTP && cbUseRecovery.isChecked
        val rawCode = if (mfaMethod == METHOD_TOTP) {
            etTotpCode.text.toString()
        } else {
            etEmailCode.text.toString()
        }.trim()

        if (useRecovery) {
            // 恢复码格式 A1B2-C3D4，横线可省略、大小写不敏感
            val normalized = rawCode.replace("-", "").replace(" ", "")
            if (normalized.length != 8 || !normalized.all { it.isLetterOrDigit() }) {
                showStatus("请输入 8 位恢复码，例如 A1B2-C3D4")
                return
            }
        } else if (rawCode.length != 6 || !rawCode.all { it.isDigit() }) {
            showStatus(if (mfaMethod == METHOD_TOTP) "请输入认证器 App 中的 6 位数字验证码" else "请输入 6 位邮箱验证码")
            return
        }

        verifying = true
        btnVerify.isEnabled = false
        showStatus("验证中…")
        lifecycleScope.launch {
            repository.verify2fa(
                mfaTicket = ticket,
                code = rawCode,
                useRecovery = useRecovery,
                rememberDevice = if (mfaMethod == METHOD_EMAIL) cbRememberDevice.isChecked else true
            ).onSuccess { result ->
                verifying = false
                finishLogin(result)
            }.onFailure { e ->
                verifying = false
                btnVerify.isEnabled = true
                handleVerifyError(e.message ?: "验证失败")
            }
        }
    }

    /**
     * 验证失败提示：重点区分票据过期、验证码重放、恢复码无效。
     * 一律不自动重试 —— 服务端对验证码有重放保护和限频，重试只会烧额度。
     */
    private fun handleVerifyError(message: String) {
        when {
            message.contains("票据") || message.contains("重新登录") -> {
                backToLogin()
                showStatus("登录票据已过期（5 分钟有效），请重新输入密码登录")
            }
            message.contains("已经用过") -> {
                showStatus("$message\n请等待下一个验证码（约 30 秒）后再试，不要重复提交同一个码")
            }
            message.contains("恢复码") -> {
                showStatus("$message\n请检查是否抄错，或改用认证器验证码登录")
            }
            else -> showStatus("验证失败：$message")
        }
    }

    /** 保存登录状态（持久化 token，下次免登录）并进入主界面 */
    private fun finishLogin(result: LoginResult) {
        val token = result.accessToken
        val user = result.user
        if (token.isNullOrEmpty() || user == null) {
            showStatus("登录响应异常，请重试")
            btnVerify.isEnabled = true
            return
        }
        // verify-2fa 响应里会更新 deviceToken；对 TOTP 账号它不生效，但照常保存
        AuthManager.saveLogin(token, user, result.refreshToken, result.deviceToken)
        Toast.makeText(this, "登录成功，欢迎 ${user.nickname.ifEmpty { user.username }}", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun backToLogin() {
        countdownJob?.cancel()
        verifying = false
        btnVerify.isEnabled = true
        btnSendCode.isEnabled = true
        btnSendCode.text = "发送验证码"
        layout2fa.visibility = View.GONE
        layoutLogin.visibility = View.VISIBLE
        mfaTicket = null
        showStatus(null)
        etPassword.setText("")
        etPassword.requestFocus()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownJob?.cancel()
        // WebView 显式销毁，避免内存泄露
        wvCaptcha.stopLoading()
        wvCaptcha.loadUrl("about:blank")
        wvCaptcha.clearHistory()
        (wvCaptcha.parent as? ViewGroup)?.removeView(wvCaptcha)
        wvCaptcha.destroy()
        captcha = null
    }
}
