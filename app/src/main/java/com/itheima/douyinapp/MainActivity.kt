package com.itheima.douyinapp

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import android.view.KeyEvent
import android.view.View
import android.webkit.ValueCallback
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

    companion object {
        private const val FILE_CHOOSER_REQUEST_CODE = 1001
    }

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var prefs: SharedPreferences
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

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
            setBackgroundColor(android.graphics.Color.BLACK)
            overScrollMode = android.webkit.WebView.OVER_SCROLL_NEVER
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                // 允许读取上传时复制出的 file:// 文件路径
                allowFileAccess = true
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                // 总是从服务器获取最新页面，避免旧缓存导致"看不到更新"或旧功能
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
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

                // 支持文件上传（头像选择）
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    fileUploadCallback = filePathCallback
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
                    return true
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            resources.getColor(android.R.color.white, theme),
            resources.getColor(android.R.color.darker_gray, theme)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                val uris = mutableListOf<Uri>()
                val clip = data.clipData
                if (clip != null) {
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i)?.uri?.let { uris.add(it) }
                    }
                } else {
                    data.data?.let { uris.add(it) }
                }
                // 把 content:// 复制成真实文件，回传 file:// URI，
                // 这样网页端才能读到文件名、类型和内容（安卓 WebView 直接传 content:// 前端拿不到 name/type）
                val fileUris = uris.mapNotNull { copyUriToFile(it) }
                fileUploadCallback?.onReceiveValue(
                    if (fileUris.isEmpty()) null else fileUris.toTypedArray()
                )
            } else {
                fileUploadCallback?.onReceiveValue(null)
            }
            fileUploadCallback = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // 将 content:// URI 下载成 app 缓存目录里的真实文件，返回 file:// URI
    private fun copyUriToFile(uri: Uri): Uri? {
        return try {
            val resolver: ContentResolver = contentResolver
            val ext = getFileExtension(resolver, uri)
            val outFile = File(cacheDir, "select_${System.currentTimeMillis()}$ext")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            Uri.fromFile(outFile)
        } catch (e: Exception) {
            null
        }
    }

    // 尽量获取原文件扩展名，拿不到就按内容类型推断
    private fun getFileExtension(resolver: ContentResolver, uri: Uri): String {
        var name = ""
        try {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) {
                        name = c.getString(0) ?: ""
                    }
                }
        } catch (_: Exception) {
        }
        if (name.contains(".")) {
            return name.substring(name.lastIndexOf("."))
        }
        return when (resolver.getType(uri)?.lowercase()) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "image/heic" -> ".heic"
            "video/mp4" -> ".mp4"
            "video/webm" -> ".webm"
            "video/3gpp" -> ".3gp"
            else -> ""
        }
    }

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