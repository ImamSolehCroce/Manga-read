package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Slate900

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MangaStoryWebViewScreen()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MangaStoryWebViewScreen() {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    // Launcher for file chooser in WebView
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val currentCallback = filePathCallback
        filePathCallback = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intentData = result.data
            val results: Array<Uri>? = when {
                intentData?.data != null -> arrayOf(intentData.data!!)
                intentData?.clipData != null -> {
                    val clip = intentData.clipData!!
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                }
                else -> null
            }
            currentCallback?.onReceiveValue(results)
        } else {
            currentCallback?.onReceiveValue(null)
        }
    }

    // Handle Android system back gesture
    BackHandler(enabled = webViewInstance?.canGoBack() == true) {
        webViewInstance?.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .systemBarsPadding()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(Slate900.toArgb())
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        databaseEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            view: WebView?,
                            callback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?
                        ): Boolean {
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = callback

                            val intent = try {
                                fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "image/*"
                                }
                            } catch (_: Exception) {
                                Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "image/*"
                                }
                            }

                            try {
                                fileChooserLauncher.launch(intent)
                                return true
                            } catch (e: Exception) {
                                filePathCallback?.onReceiveValue(null)
                                filePathCallback = null
                                return false
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            // Keep internal navigation in WebView
                            val url = request?.url?.toString() ?: ""
                            return if (url.startsWith("file://") || url.startsWith("data:")) {
                                false
                            } else {
                                // Allow external links if any or open internally
                                false
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // If BuildConfig GEMINI_API_KEY is configured and not default placeholder, auto-populate input field
                            try {
                                val field = BuildConfig::class.java.getField("GEMINI_API_KEY")
                                val apiKey = (field.get(null) as? String).orEmpty()
                                if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
                                    val safeKey = apiKey.replace("'", "\\'")
                                    view?.evaluateJavascript(
                                        """
                                        (function() {
                                            var keyInput = document.getElementById('apiKey');
                                            if (keyInput && !keyInput.value) {
                                                keyInput.value = '$safeKey';
                                            }
                                        })();
                                        """.trimIndent(),
                                        null
                                    )
                                }
                            } catch (_: Throwable) {
                                // Ignore if BuildConfig key is unavailable
                            }
                        }
                    }

                    loadUrl("file:///android_asset/index.html")
                    webViewInstance = this
                }
            },
            update = { webView ->
                webViewInstance = webView
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }
}
