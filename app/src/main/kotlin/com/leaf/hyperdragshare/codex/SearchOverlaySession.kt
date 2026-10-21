package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.ThemeController

/** One search state and WebView implementation shared by the overlay and legacy Activity host. */
internal class SearchOverlaySession(
    private val context: Context,
    text: String,
    searchType: Int,
    private val onClose: () -> Unit,
    private val openExternal: (Intent) -> Unit,
    savedState: Bundle? = null,
) {
    private var query by mutableStateOf("")
    private var selectedEngine by mutableStateOf(SearchEngineCatalog.defaultForType(BoomActionHandler.SEARCH_WEB))
    private var panelFraction by mutableStateOf(DEFAULT_PANEL_FRACTION)
    private var navigationTarget by mutableStateOf<String?>(null)
    private var navigationRequest by mutableIntStateOf(0)
    private var canNavigateBack by mutableStateOf(false)
    private var canNavigateForward by mutableStateOf(false)
    private var pageError by mutableStateOf<String?>(null)
    private var webView: WebView? = null
    private var restoredWebState: Bundle? = null

    init {
        query = savedState?.getString("query") ?: text
        selectedEngine = SearchEngineCatalog.engines.firstOrNull {
            it.id == savedState?.getString("engine")
        } ?: SearchEngineCatalog.defaultForType(
            searchType,
        )
        panelFraction = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getFloat(PANEL_HEIGHT_KEY, DEFAULT_PANEL_FRACTION)
            .coerceIn(MIN_PANEL_FRACTION, MAX_PANEL_FRACTION)
        navigationTarget = SearchEngineCatalog.destination(selectedEngine, query)
        restoredWebState = savedState?.getBundle("web")
    }

    @Composable
    fun Content() {
        val dark = DragShareSettings.readLocal(context).isDarkThemeInComposition()
        MiuixTheme(
            controller = remember(dark) { ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light) },
        ) {
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
                onClose = onClose,
                onBack = ::navigateBack,
                onForward = ::navigateForward,
                onRefresh = { webView?.reload() },
                onOpenInBrowser = ::openInBrowser,
                createWebView = { requireNotNull(webView) },
            )
        }
    }

    /** WebView is a system component boundary; failure leaves the source panel untouched. */
    fun prepareWebView(viewContext: Context): Boolean {
        return try {
            createWebView(viewContext)
            true
        } catch (error: RuntimeException) {
            DragShareLog.w("DragShare/Search", "WebView initialization failed", error)
            Toast.makeText(context, "搜索网页组件无法启动，请检查 Android System WebView", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun open(text: String, searchType: Int) {
        restoredWebState = null
        query = text
        selectedEngine = SearchEngineCatalog.defaultForType(searchType)
        submitSearch()
    }

    fun resume() { webView?.onResume() }

    fun pause() { webView?.onPause() }

    fun close() {
        val page = webView ?: return
        webView = null
        context.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(page.windowToken, 0)
        page.clearFocus()
        page.stopLoading()
        page.onPause()
        page.webChromeClient = null
        (page.parent as? ViewGroup)?.removeView(page)
        page.destroy()
    }

    fun saveState(outState: Bundle) {
        outState.putString("query", query)
        outState.putString("engine", selectedEngine.id)
        val history = Bundle()
        if (webView?.saveState(history) != null) outState.putBundle("web", history)
        else restoredWebState?.let { outState.putBundle("web", it) }
    }

    private fun submitSearch() {
        val value = query.trim()
        if (value.isEmpty()) {
            Toast.makeText(context, R.string.search_no_query, Toast.LENGTH_SHORT).show()
            return
        }
        val destination = SearchEngineCatalog.destination(selectedEngine, value)
        if (destination == null) {
            Toast.makeText(context, R.string.search_https_only, Toast.LENGTH_SHORT).show()
            return
        }
        pageError = null
        navigationTarget = destination
        navigationRequest++
        webView?.clearFocus()
    }

    private fun adjustPanelHeight(deltaY: Float, screenHeightPx: Float) {
        if (screenHeightPx <= 0f) return
        panelFraction = (panelFraction - deltaY / screenHeightPx)
            .coerceIn(MIN_PANEL_FRACTION, MAX_PANEL_FRACTION)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putFloat(PANEL_HEIGHT_KEY, panelFraction)
            .apply()
    }

    private fun navigateBack() {
        val page = webView
        if (page?.canGoBack() == true) {
            page.goBack()
        }
    }

    private fun navigateForward() {
        webView?.takeIf { it.canGoForward() }?.goForward()
    }

    private fun openInBrowser() {
        val currentUrl = webView?.url ?: navigationTarget
        if (currentUrl.isNullOrBlank() || !currentUrl.startsWith("https://", ignoreCase = true)) {
            Toast.makeText(context, R.string.search_https_only, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            openExternal(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
        } catch (error: android.content.ActivityNotFoundException) {
            DragShareLog.w("DragShare/Search", "browser unavailable", error)
            Toast.makeText(context, R.string.search_browser_unavailable, Toast.LENGTH_SHORT).show()
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Search", "browser launch denied", error)
            Toast.makeText(context, R.string.search_browser_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createWebView(context: Context): WebView = WebView(context).apply {
        webView = this
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
                if (request.isForMainFrame) pageError = context.getString(R.string.search_page_failed)
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                handler.cancel()
                pageError = context.getString(R.string.search_page_failed)
            }
        }
        restoredWebState?.let { history ->
            if (restoreState(history) != null) {
                tag = navigationRequest
                updateNavigationState(this)
            } else {
                DragShareLog.w("DragShare/Search", "WebView could not restore saved history")
                Toast.makeText(context, "浏览记录无法恢复，已重新搜索", Toast.LENGTH_SHORT).show()
            }
        }
        restoredWebState = null
    }

    private fun updateNavigationState(view: WebView) {
        canNavigateBack = view.canGoBack()
        canNavigateForward = view.canGoForward()
    }

    companion object {
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
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val close = {
        focusManager.clearFocus()
        keyboard?.hide()
        onClose()
    }
    // Exit query input first; otherwise Back and Close both return to the retained selection.
    BackHandler {
        if (imeVisible) {
            focusManager.clearFocus()
            keyboard?.hide()
        } else close()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (MiuixTheme.colorScheme.background.luminance() < .5f) .62f else .48f))
            .clickable(onClick = close),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(panelFraction)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(MiuixTheme.colorScheme.background)
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
                        .background(MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.38f)),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = MiuixTheme.textStyles.body2.copy(
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            onSubmit()
                        }),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MiuixTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (query.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_query_hint),
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                OverlayIconAction(MiuixIcons.Search, MiuixTheme.colorScheme.primary,
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onSubmit()
                    }, contentDescription = stringResource(R.string.search_submit))
                OverlayIconAction(MiuixIcons.Close, MiuixTheme.colorScheme.onSurface,
                    onClick = close, contentDescription = stringResource(R.string.search_close))
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
                    val selected = engine.id == selectedEngine.id
                    Card(
                        modifier = Modifier.height(44.dp),
                        colors = CardDefaults.defaultColors(
                            color = if (selected) {
                                MiuixTheme.colorScheme.primaryContainer
                            } else {
                                MiuixTheme.colorScheme.surfaceContainer
                            },
                        ),
                        onClick = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            onEngineSelected(engine)
                        },
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = engine.label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                },
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 6.dp, bottom = 2.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainer),
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
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .background(
                                MiuixTheme.colorScheme.background.copy(alpha = 0.96f),
                                RoundedCornerShape(12.dp),
                            )
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
                OverlayIconAction(MiuixIcons.Back, MiuixTheme.colorScheme.onSurface,
                    enabled = canNavigateBack, onClick = onBack,
                    contentDescription = stringResource(R.string.search_back))
                OverlayIconAction(MiuixIcons.Forward, MiuixTheme.colorScheme.onSurface,
                    enabled = canNavigateForward, onClick = onForward,
                    contentDescription = stringResource(R.string.search_forward))
                OverlayIconAction(MiuixIcons.Refresh, MiuixTheme.colorScheme.onSurface,
                    onClick = onRefresh, contentDescription = stringResource(R.string.search_refresh))
                OverlayIconAction(Icons.Outlined.OpenInBrowser, MiuixTheme.colorScheme.onSurface,
                    onClick = onOpenInBrowser, contentDescription = stringResource(R.string.search_open_browser))
            }
        }
    }
}
