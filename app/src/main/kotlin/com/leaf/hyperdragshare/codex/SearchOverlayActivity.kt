package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat

class SearchOverlayActivity : ComponentActivity() {
    private var query by mutableStateOf("")
    private var selectedEngine by mutableStateOf(SearchEngineCatalog.defaultForType(BoomActionHandler.SEARCH_WEB))
    private var panelFraction by mutableStateOf(DEFAULT_PANEL_FRACTION)
    private var navigationTarget by mutableStateOf<String?>(null)
    private var navigationRequest by mutableIntStateOf(0)
    private var canNavigateBack by mutableStateOf(false)
    private var canNavigateForward by mutableStateOf(false)
    private var pageError by mutableStateOf<String?>(null)
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        query = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        selectedEngine = SearchEngineCatalog.defaultForType(
            intent.getIntExtra(EXTRA_SEARCH_TYPE, BoomActionHandler.SEARCH_WEB),
        )
        panelFraction = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .getFloat(PANEL_HEIGHT_KEY, DEFAULT_PANEL_FRACTION)
            .coerceIn(MIN_PANEL_FRACTION, MAX_PANEL_FRACTION)
        navigationTarget = SearchEngineCatalog.destination(selectedEngine, query)

        setContent {
            SearchPanel(
                query = query,
                selectedEngine = selectedEngine,
                panelFraction = panelFraction,
                screenHeightPx = with(LocalDensity.current) {
                    LocalConfiguration.current.screenHeightDp.dp.toPx()
                },
                engines = SearchEngineCatalog.engines,
                navigationTarget = navigationTarget,
                navigationRequest = navigationRequest,
                canNavigateBack = canNavigateBack,
                canNavigateForward = canNavigateForward,
                pageError = pageError,
                onQueryChange = { query = it },
                onSubmit = { submitSearch() },
                onEngineSelected = {
                    selectedEngine = it
                    submitSearch()
                },
                onHeightDrag = ::adjustPanelHeight,
                onClose = ::finish,
                onBack = ::navigateBack,
                onForward = ::navigateForward,
                onRefresh = { webView?.reload() },
                onOpenInBrowser = ::openInBrowser,
                createWebView = ::createWebView,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        query = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        selectedEngine = SearchEngineCatalog.defaultForType(
            intent.getIntExtra(EXTRA_SEARCH_TYPE, BoomActionHandler.SEARCH_WEB),
        )
        submitSearch()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            webChromeClient = null
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun submitSearch() {
        val value = query.trim()
        if (value.isEmpty()) {
            Toast.makeText(this, R.string.search_no_query, Toast.LENGTH_SHORT).show()
            return
        }
        val destination = SearchEngineCatalog.destination(selectedEngine, value)
        if (destination == null) {
            Toast.makeText(this, R.string.search_https_only, Toast.LENGTH_SHORT).show()
            return
        }
        pageError = null
        navigationTarget = destination
        navigationRequest++
        window.currentFocus?.clearFocus()
    }

    private fun adjustPanelHeight(deltaY: Float, screenHeightPx: Float) {
        if (screenHeightPx <= 0f) return
        panelFraction = (panelFraction - deltaY / screenHeightPx)
            .coerceIn(MIN_PANEL_FRACTION, MAX_PANEL_FRACTION)
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .edit()
            .putFloat(PANEL_HEIGHT_KEY, panelFraction)
            .apply()
    }

    private fun navigateBack() {
        val page = webView
        if (page?.canGoBack() == true) {
            page.goBack()
        } else {
            finish()
        }
    }

    private fun navigateForward() {
        webView?.takeIf { it.canGoForward() }?.goForward()
    }

    private fun openInBrowser() {
        val currentUrl = webView?.url ?: navigationTarget
        if (currentUrl.isNullOrBlank() || !currentUrl.startsWith("https://", ignoreCase = true)) {
            Toast.makeText(this, R.string.search_https_only, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
        } catch (_: Throwable) {
            Toast.makeText(this, R.string.search_browser_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createWebView(context: Context): WebView = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        setBackgroundColor(AndroidColor.WHITE)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme
                return !scheme.equals("https", ignoreCase = true)
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                pageError = null
                updateNavigationState(view)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                updateNavigationState(view)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) pageError = getString(R.string.search_page_failed)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                handler.cancel()
                pageError = getString(R.string.search_page_failed)
            }
        }
        webView = this
    }

    private fun updateNavigationState(view: WebView) {
        canNavigateBack = view.canGoBack()
        canNavigateForward = view.canGoForward()
    }

    companion object {
        const val EXTRA_SEARCH_TYPE = "search_type"
        private const val PREFERENCES = "search_overlay"
        private const val PANEL_HEIGHT_KEY = "panel_height_fraction"
        private const val DEFAULT_PANEL_FRACTION = 0.68f
        private const val MIN_PANEL_FRACTION = 0.35f
        private const val MAX_PANEL_FRACTION = 0.95f
    }
}

@Composable
private fun SearchPanel(
    query: String,
    selectedEngine: SearchEngine,
    panelFraction: Float,
    screenHeightPx: Float,
    engines: List<SearchEngine>,
    navigationTarget: String?,
    navigationRequest: Int,
    canNavigateBack: Boolean,
    canNavigateForward: Boolean,
    pageError: String?,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onEngineSelected: (SearchEngine) -> Unit,
    onHeightDrag: (Float, Float) -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onOpenInBrowser: () -> Unit,
    createWebView: (Context) -> WebView,
) {
    val focusManager = LocalFocusManager.current
    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(panelFraction)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Color(0xFFF6F7F9))
                .clickable(onClick = {})
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .pointerInput(screenHeightPx) {
                        detectVerticalDragGestures { _, deltaY ->
                            onHeightDrag(deltaY, screenHeightPx)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFB8BEC7)),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.search_close))
                }
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_query_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        onSubmit()
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(0xFF2675E8),
                    ),
                    shape = RoundedCornerShape(20.dp),
                )
                IconButton(onClick = {
                    focusManager.clearFocus()
                    onSubmit()
                }) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search_submit))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                engines.forEach { engine ->
                    FilterChip(
                        selected = engine.id == selectedEngine.id,
                        onClick = { onEngineSelected(engine) },
                        label = { Text(engine.label, fontSize = 13.sp) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 6.dp, bottom = 2.dp)
                    .background(Color.White),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = createWebView,
                    update = { view ->
                        if (navigationTarget != null && view.tag != navigationRequest) {
                            view.tag = navigationRequest
                            view.loadUrl(navigationTarget)
                        }
                    },
                )
                if (pageError != null) {
                    Text(
                        text = pageError,
                        color = Color(0xFF667080),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, enabled = canNavigateBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.search_back))
                }
                IconButton(onClick = onForward, enabled = canNavigateForward) {
                    Icon(Icons.Outlined.ArrowForward, contentDescription = stringResource(R.string.search_forward))
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.search_refresh))
                }
                IconButton(onClick = onOpenInBrowser) {
                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = stringResource(R.string.search_open_browser))
                }
            }
        }
    }
}
