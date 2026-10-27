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
 * 站点强制两步验证：
 * 1. 账号密码登录 → 服务器返回 400 + mfaTicket（Repository 已解析为成功结果）
 * 2. 切换到两步验证卡：图形验证码（SVG WebView 渲染）+ 邮箱验证码
 * 3. 发送验证码 → 发送到绑定邮箱，60 秒冷却后才能重发
 * 4. 验证并登录 → 获取 token 持久化，下次免登录
 * 「记住这台设备 30 天」勾选后，30 天内本设备登录不再需要邮箱验证码
 */
class LoginActivity : AppCompatActivity() {

    private val repository = LingXiApp.get().repository

    private lateinit var layoutLogin: LinearLayout
    private lateinit var layout2fa: LinearLayout
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var tv2faNotice: TextView
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
    private var captcha: Captcha? = null
    private var countdownJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        layoutLogin = findViewById(R.id.layoutLogin)
        layout2fa = findViewById(R.id.layout2fa)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tv2faNotice = findViewById(R.id.tv2faNotice)
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

        etUsername.requestFocus()
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
                    // 站点强制两步验证：进入验证流程
                    mfaTicket = result.mfaTicket
                    enter2fa(result.emailMasked)
                } else {
                    finishLogin(result)
                }
            }.onFailure {
                btnLogin.isEnabled = true
                showStatus("登录失败：${it.message}")
            }
        }
    }

    /** 切换到两步验证卡片 */
    private fun enter2fa(emailMasked: String?) {
        btnLogin.isEnabled = true
        showStatus(null)
        layoutLogin.visibility = View.GONE
        layout2fa.visibility = View.VISIBLE
        tv2faNotice.text = "检测到这台设备尚未通过验证，为保护账号安全，请输入发送到 ${emailMasked ?: "绑定邮箱"} 的验证码完成登录。"
        etCaptchaCode.setText("")
        etEmailCode.setText("")
        loadCaptcha()
        etCaptchaCode.requestFocus()
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

    private fun renderCaptcha(svg: String) {
        val html = """<html><head><meta name="viewport" content="width=150,initial-scale=1"/></head>
            <body style="margin:0;padding:0;background:#2A2A2A;display:flex;align-items:center;justify-content:center;">
            <div style="width:100%;max-width:150px;">${svg.replace("<svg", "<svg width=\"100%\" height=\"100%\" preserveAspectRatio=\"xMidYMid meet\"")}</div>
            </body></html>"""
        wvCaptcha.loadData(html, "image/svg+xml", "UTF-8")
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
        btnSendCode.isEnabled = false
        showStatus("验证码发送中…")
        lifecycleScope.launch {
            repository.sendEmailCode(ticket, captcha?.id ?: "", captchaCode)
                .onSuccess {
                    showStatus("验证码已发送到绑定邮箱，请查收")
                    startCountdown()
                }
                .onFailure {
                    btnSendCode.isEnabled = true
                    showStatus("发送失败：${it.message}")
                    // 图形验证码可能已被消耗，刷新
                    loadCaptcha()
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
        val ticket = mfaTicket
        val code = etEmailCode.text.toString().trim()
        if (ticket.isNullOrEmpty()) {
            showStatus("登录会话已失效，请返回重新登录")
            return
        }
        if (code.length != 6) {
            showStatus("请输入 6 位邮箱验证码")
            return
        }
        btnVerify.isEnabled = false
        showStatus("验证中…")
        lifecycleScope.launch {
            repository.verify2fa(ticket, code, cbRememberDevice.isChecked)
                .onSuccess { result ->
                    finishLogin(result)
                }
                .onFailure {
                    btnVerify.isEnabled = true
                    showStatus("验证失败：${it.message}")
                }
        }
    }

    /** 保存登录状态（持久化 token，下次免登录）并进入主界面 */
    private fun finishLogin(result: LoginResult) {
        val token = result.token
        val user = result.user
        if (token.isNullOrEmpty() || user == null) {
            showStatus("登录响应异常，请重试")
            btnVerify.isEnabled = true
            return
        }
        AuthManager.saveLogin(token, user)
        Toast.makeText(this, "登录成功，欢迎 ${user.nickname.ifEmpty { user.username }}", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun backToLogin() {
        countdownJob?.cancel()
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
