package com.lingxi.tv.ui.auth

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lingxi.tv.LingXiApp
import com.lingxi.tv.R
import com.lingxi.tv.data.auth.AuthManager
import com.lingxi.tv.data.model.Captcha
import kotlinx.coroutines.launch

/**
 * 登录界面
 * 流程：账号密码登录 → 若需二次验证则显示图形验证码+邮箱验证码 → 验证成功保存 Token
 */
class LoginActivity : AppCompatActivity() {

    private val repository = LingXiApp.get().repository

    private lateinit var loginForm: View
    private lateinit var twoFactorForm: View
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etCaptcha: EditText
    private lateinit var etEmailCode: EditText
    private lateinit var wvCaptcha: WebView
    private lateinit var tvStatus: TextView
    private lateinit var tvTwoFactorHint: TextView
    private lateinit var cbRemember: CheckBox

    private var captcha: Captcha? = null
    private var maskedEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        loginForm = findViewById(R.id.loginForm)
        twoFactorForm = findViewById(R.id.twoFactorForm)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etCaptcha = findViewById(R.id.etCaptcha)
        etEmailCode = findViewById(R.id.etEmailCode)
        wvCaptcha = findViewById(R.id.wvCaptcha)
        tvStatus = findViewById(R.id.tvStatus)
        tvTwoFactorHint = findViewById(R.id.tvTwoFactorHint)
        cbRemember = findViewById(R.id.cbRemember)

        wvCaptcha.settings.javaScriptEnabled = false
        wvCaptcha.settings.loadWithOverviewMode = true
        wvCaptcha.settings.useWideViewPort = true
        // 内存优化：禁用缓存、DOM 存储、数据库，验证码不需要持久化
        wvCaptcha.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        wvCaptcha.settings.domStorageEnabled = false
        wvCaptcha.settings.databaseEnabled = false
        wvCaptcha.webViewClient = WebViewClient()
        wvCaptcha.isVerticalScrollBarEnabled = false
        wvCaptcha.isHorizontalScrollBarEnabled = false

        findViewById<Button>(R.id.btnLogin).setOnClickListener { doLogin() }
        findViewById<Button>(R.id.btnRefreshCaptcha).setOnClickListener { loadCaptcha() }
        findViewById<Button>(R.id.btnSendCode).setOnClickListener { sendCode() }
        findViewById<Button>(R.id.btnVerify).setOnClickListener { verify() }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 内存优化：WebView 必须显式销毁，否则泄露大量内存
        wvCaptcha.stopLoading()
        wvCaptcha.loadUrl("about:blank")
        wvCaptcha.clearHistory()
        (wvCaptcha.parent as? ViewGroup)?.removeView(wvCaptcha)
        wvCaptcha.destroy()
        captcha = null
    }

    private fun setStatus(msg: String) {
        tvStatus.text = msg
    }

    private fun doLogin() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入账号和密码", Toast.LENGTH_SHORT).show()
            return
        }
        setStatus("登录中...")
        lifecycleScope.launch {
            val result = repository.login(username, password)
            result.onSuccess { loginResult ->
                if (loginResult.need2fa) {
                    // 需要二次验证
                    maskedEmail = loginResult.maskedEmail
                    tvTwoFactorHint.text = "检测到这台设备尚未通过验证，请输入发送到${loginResult.maskedEmail ?: "邮箱"}的验证码完成登录。"
                    loginForm.visibility = View.GONE
                    twoFactorForm.visibility = View.VISIBLE
                    loadCaptcha()
                    setStatus("")
                } else if (!loginResult.token.isNullOrEmpty()) {
                    // 直接登录成功
                    saveLogin(loginResult.token, loginResult.user)
                } else {
                    setStatus("登录失败：未知响应")
                }
            }.onFailure {
                setStatus("登录失败：${it.message}")
            }
        }
    }

    private fun loadCaptcha() {
        lifecycleScope.launch {
            val result = repository.getCaptcha()
            result.onSuccess { cap ->
                captcha = cap
                // SVG 直接加载到 WebView
                val html = "<html><body style='margin:0;padding:0;'>${cap.svg}</body></html>"
                wvCaptcha.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }.onFailure {
                setStatus("验证码加载失败：${it.message}")
            }
        }
    }

    private fun sendCode() {
        val cap = captcha
        val captchaCode = etCaptcha.text.toString().trim()
        if (cap == null) {
            Toast.makeText(this, "请先加载图形验证码", Toast.LENGTH_SHORT).show()
            return
        }
        if (captchaCode.isEmpty()) {
            Toast.makeText(this, "请输入图形验证码", Toast.LENGTH_SHORT).show()
            return
        }
        setStatus("发送中...")
        lifecycleScope.launch {
            val result = repository.sendEmailCode(null, cap.id, captchaCode)
            result.onSuccess {
                setStatus("验证码已发送，请查收邮箱")
                // 发送成功后刷新图形验证码
                loadCaptcha()
                etCaptcha.text.clear()
            }.onFailure {
                setStatus("发送失败：${it.message}")
                loadCaptcha()
                etCaptcha.text.clear()
            }
        }
    }

    private fun verify() {
        val code = etEmailCode.text.toString().trim()
        if (code.length < 4) {
            Toast.makeText(this, "请输入邮箱验证码", Toast.LENGTH_SHORT).show()
            return
        }
        setStatus("验证中...")
        lifecycleScope.launch {
            val result = repository.verify2fa(code, cbRemember.isChecked)
            result.onSuccess { loginResult ->
                if (!loginResult.token.isNullOrEmpty()) {
                    saveLogin(loginResult.token, loginResult.user)
                } else {
                    setStatus("验证失败：未返回 Token")
                }
            }.onFailure {
                setStatus("验证失败：${it.message}")
            }
        }
    }

    private fun saveLogin(token: String, user: com.lingxi.tv.data.model.User?) {
        AuthManager.token = token
        AuthManager.user = user
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }
}
