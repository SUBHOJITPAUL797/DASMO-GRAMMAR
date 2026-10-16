package com.dasmo.grammar.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enterprise-grade offline grammar engine bridge for Dasmo Grammar.
 * Handles headless Harper WASM execution, process recovery, debouncing, and Samsung/OEM stability.
 */
class DasmoEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "DasmoEngine"
        private const val HOST_HTML = "file:///android_asset/dasmo-engine/dasmo-engine.html"
        private const val REQUEST_TIMEOUT_MS = 5000L

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: DasmoEngine? = null

        fun getInstance(context: Context): DasmoEngine {
            return instance ?: synchronized(this) {
                instance ?: DasmoEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val requestIdCounter = AtomicInteger(1)

    private val pendingLintRequests = ConcurrentHashMap<Int, CompletableDeferred<List<DasmoLint>>>()
    private val pendingStatsRequests = ConcurrentHashMap<Int, CompletableDeferred<DasmoStats>>()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var webView: WebView? = null
    val dictionary = DasmoDictionary(appContext)

    var currentDialect: DasmoDialect = DasmoDialect.AMERICAN
        private set

    private var debounceJob: Job? = null

    init {
        DasmoLogger.i(TAG, "Initializing Dasmo Grammar Engine on device ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        mainHandler.post {
            setupWebView()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        try {
            // Safely clean up any existing webview instance
            webView?.apply {
                stopLoading()
                removeJavascriptInterface("DasmoNativeBridge")
                destroy()
            }
            webView = null

            val wv = WebView(appContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        val msg = consoleMessage?.message() ?: ""
                        val line = consoleMessage?.lineNumber() ?: 0
                        val src = consoleMessage?.sourceId() ?: ""
                        when (consoleMessage?.messageLevel()) {
                            android.webkit.ConsoleMessage.MessageLevel.ERROR ->
                                DasmoLogger.e("EngineJS", "[$line:$src] $msg")
                            android.webkit.ConsoleMessage.MessageLevel.WARNING ->
                                DasmoLogger.w("EngineJS", "[$line:$src] $msg")
                            else ->
                                DasmoLogger.d("EngineJS", "[$line:$src] $msg")
                        }
                        return true
                    }
                }
                addJavascriptInterface(DasmoNativeBridge(), "DasmoNativeBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        DasmoLogger.i(TAG, "Engine host page loaded successfully: $url")
                        syncDictionaryWords()
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        DasmoLogger.e(TAG, "WebView resource error: ${error?.description} on ${request?.url}")
                    }

                    // CRITICAL FOR SAMSUNG / XIAOMI / LOW-RAM DEVICES:
                    // If the OS terminates the render process due to memory pressure, recover gracefully!
                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                        DasmoLogger.w(TAG, "WebView render process gone! Did crash: ${detail?.didCrash()}. Re-initializing...")
                        _isReady.value = false
                        mainHandler.postDelayed({
                            setupWebView()
                        }, 1000)
                        return true // Return true so the host application does not crash
                    }
                }
            }
            webView = wv
            wv.loadUrl(HOST_HTML)
        } catch (e: Exception) {
            DasmoLogger.e(TAG, "Fatal error setting up Dasmo Grammar WebView", e)
        }
    }

    private fun syncDictionaryWords() {
        val words = dictionary.getWords()
        DasmoLogger.d(TAG, "Syncing ${words.size} custom dictionary words into engine")
        for (w in words) {
            evaluateJavascript("if (typeof DasmoEngine !== 'undefined' && DasmoEngine.addWord) { DasmoEngine.addWord(${JSONObject.quote(w)}); }")
        }
    }

    private fun evaluateJavascript(script: String) {
        mainHandler.post {
            try {
                webView?.evaluateJavascript(script) { result ->
                    // Optional evaluation callback
                }
            } catch (e: Exception) {
                DasmoLogger.e(TAG, "Error executing script in Dasmo Engine", e)
            }
        }
    }

    fun setDialect(dialect: DasmoDialect) {
        currentDialect = dialect
        DasmoLogger.i(TAG, "Switching dialect to: ${dialect.displayName}")
        evaluateJavascript("if (typeof DasmoEngine !== 'undefined' && DasmoEngine.setDialect) { DasmoEngine.setDialect(${JSONObject.quote(dialect.engineName)}); }")
    }

    fun addCustomWord(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        dictionary.addWord(trimmed)
        DasmoLogger.i(TAG, "Added custom word to dictionary: $trimmed")
        evaluateJavascript("if (typeof DasmoEngine !== 'undefined' && DasmoEngine.addWord) { DasmoEngine.addWord(${JSONObject.quote(trimmed)}); }")
    }

    suspend fun lint(text: String): List<DasmoLint> = withContext(Dispatchers.Main) {
        if (text.isBlank()) return@withContext emptyList()

        if (!_isReady.value) {
            withTimeoutOrNull(4000L) {
                _isReady.first { it }
            }
        }

        val reqId = requestIdCounter.incrementAndGet()
        val deferred = CompletableDeferred<List<DasmoLint>>()
        pendingLintRequests[reqId] = deferred

        evaluateJavascript("if (typeof DasmoEngine !== 'undefined' && DasmoEngine.lint) { DasmoEngine.lint($reqId, ${JSONObject.quote(text)}); }")

        try {
            // Guard against indefinitely hung calls with a safe timeout
            val result = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                deferred.await()
            }
            result ?: run {
                DasmoLogger.w(TAG, "Lint request $reqId timed out after ${REQUEST_TIMEOUT_MS}ms")
                emptyList()
            }
        } catch (e: Exception) {
            DasmoLogger.e(TAG, "Error waiting for lint request $reqId", e)
            emptyList()
        } finally {
            pendingLintRequests.remove(reqId)
        }
    }

    suspend fun getStats(text: String): DasmoStats = withContext(Dispatchers.Main) {
        if (text.isBlank()) return@withContext DasmoStats.empty()

        val reqId = requestIdCounter.incrementAndGet()
        val deferred = CompletableDeferred<DasmoStats>()
        pendingStatsRequests[reqId] = deferred

        evaluateJavascript("DasmoEngine.getStats($reqId, ${JSONObject.quote(text)})")

        try {
            val result = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                deferred.await()
            }
            result ?: DasmoStats.empty()
        } catch (e: Exception) {
            DasmoLogger.e(TAG, "Error waiting for stats request $reqId", e)
            DasmoStats.empty()
        } finally {
            pendingStatsRequests.remove(reqId)
        }
    }

    fun lintDebounced(text: String, delayMs: Long = 250L, onResult: (List<DasmoLint>) -> Unit) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(delayMs)
            val lints = lint(text)
            onResult(lints)
        }
    }

    private inner class DasmoNativeBridge {
        @JavascriptInterface
        fun onEngineReady(ready: Boolean) {
            DasmoLogger.i(TAG, "Bridge: Dasmo Grammar Core Ready status = $ready")
            _isReady.value = ready
        }

        @JavascriptInterface
        fun onEngineError(error: String) {
            DasmoLogger.e(TAG, "Bridge: Engine reported internal error: $error")
        }

        @JavascriptInterface
        fun onLintResult(requestId: Int, jsonResult: String) {
            val deferred = pendingLintRequests[requestId]
            if (deferred != null) {
                try {
                    val array = JSONArray(jsonResult)
                    val lints = mutableListOf<DasmoLint>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        lints.add(DasmoLint.fromJsonObject(obj))
                    }
                    deferred.complete(lints)
                } catch (e: Exception) {
                    DasmoLogger.e(TAG, "Bridge: Error parsing lint JSON for reqId=$requestId", e)
                    deferred.complete(emptyList())
                }
            }
        }

        @JavascriptInterface
        fun onLintError(requestId: Int, errorMsg: String) {
            DasmoLogger.e(TAG, "Bridge: Lint error for reqId=$requestId: $errorMsg")
            pendingLintRequests[requestId]?.complete(emptyList())
        }

        @JavascriptInterface
        fun onStatsResult(requestId: Int, jsonResult: String) {
            val deferred = pendingStatsRequests[requestId]
            if (deferred != null) {
                try {
                    val obj = JSONObject(jsonResult)
                    deferred.complete(DasmoStats.fromJsonObject(obj))
                } catch (e: Exception) {
                    DasmoLogger.e(TAG, "Bridge: Error parsing stats JSON for reqId=$requestId", e)
                    deferred.complete(DasmoStats.empty())
                }
            }
        }
    }
}
