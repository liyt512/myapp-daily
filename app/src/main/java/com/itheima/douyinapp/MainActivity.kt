package com.itheima.douyinapp

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("myapp_prefs", MODE_PRIVATE)
        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)

        setupWebView()
        setupSwipeRefresh()
        checkServerUrl()
    }

    private fun setupWebView() {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                allowFileAccess = false
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    swipeRefresh.isRefreshing = true
                    progressBar.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    swipeRefresh.isRefreshing = false
                    progressBar.visibility = View.GONE
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            resources.getColor(android.R.color.holo_orange_light, theme),
            resources.getColor(android.R.color.holo_orange_dark, theme)
        )
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }
    }

    private fun checkServerUrl() {
        val savedUrl = prefs.getString("server_url", "")
        if (savedUrl.isNullOrEmpty()) {
            // 默认使用服务器地址
            val defaultUrl = "http://47.112.23.214"
            prefs.edit().putString("server_url", defaultUrl).apply()
            webView.loadUrl(defaultUrl)
        } else {
            webView.loadUrl(savedUrl)
        }
    }

    private fun showUrlDialog() {
        val builder = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
        builder.setTitle("设置服务器地址")
        builder.setMessage("请输入你的服务器地址\n例如：http://47.112.23.214\n或 http://你的域名.com")

        val input = EditText(this).apply {
            hint = "服务器地址"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.getString("server_url", ""))
            setSelection(text?.length ?: 0)
            isSingleLine = true
        }

        builder.setView(input, 48, 16, 48, 16)

        builder.setPositiveButton("连接") { _, _ ->
            val url = input.text.toString().trim()
            if (url.isNotEmpty()) {
                val finalUrl = if (!url.startsWith("http")) "http://$url" else url
                prefs.edit().putString("server_url", finalUrl).apply()
                webView.loadUrl(finalUrl)
            } else {
                showUrlDialog()
            }
        }

        builder.setNegativeButton("退出") { _, _ -> finish() }
        builder.setCancelable(false)
        builder.show()
    }

    private var lastBackPress = 0L

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            val now = System.currentTimeMillis()
            if (now - lastBackPress > 2000) {
                lastBackPress = now
                Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}