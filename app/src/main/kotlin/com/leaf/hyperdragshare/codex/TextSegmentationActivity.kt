package com.leaf.hyperdragshare.codex

import android.content.ComponentName
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * DragShare's text-only BigBang surface. The selectable word-chip implementation is retained
 * from the reference project; Compose only supplies its floating overlay shell.
 */
class TextSegmentationActivity : ComponentActivity() {
    private var boomChipPage: BoomChipPage? = null
    private lateinit var legacyContentView: View
    private var launchTouchX by mutableIntStateOf(-1)
    private var launchTouchY by mutableIntStateOf(-1)
    private var textSizeSp by mutableFloatStateOf(BigBangSettings.DEFAULT_TEXT_SIZE_SP)
    private var panelHeightFraction by mutableFloatStateOf(BigBangSettings.DEFAULT_PANEL_HEIGHT_FRACTION)
    private var panelOpacity by mutableFloatStateOf(BigBangSettings.DEFAULT_PANEL_OPACITY)
    private var panelAdjustmentEnabled by mutableStateOf(false)
    private var panelOffsetXdp by mutableFloatStateOf(0f)
    private var panelOffsetYdp by mutableFloatStateOf(0f)
    private var sourceImageUri by mutableStateOf<Uri?>(null)
    private var legacyBackgroundColor = AndroidColor.WHITE
    private var legacyTextColor = AndroidColor.DKGRAY
    private var legacySummaryColor = AndroidColor.GRAY
    private var legacySurfaceColor = AndroidColor.LTGRAY
    private var legacySelectionColor = 0x223F7CFF
    private var legacyActionColor = AndroidColor.DKGRAY
    private var animatedDismissRequester: (() -> Unit)? = null
    private val requests = LatestRequest()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        updateLaunchPosition()

        legacyContentView = layoutInflater.inflate(R.layout.boom_activity_layout, null, false)
        legacyContentView.findViewById<View>(R.id.boom_page).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            BoomAnimator.makeFadeIn(this, BoomAnimator.BOOM_DURATION)
        }
        boomChipPage = BoomChipPage(this, legacyContentView, false).also { page ->
            page.setOnChipsChangedListener(::refreshLegacyTheme)
            page.restoreSelectedState(savedInstanceState?.getSerializable(SELECTED_STATE))
        }
        textSizeSp = BigBangSettings.getTextSizeSp(this)
        panelHeightFraction = BigBangSettings.getPanelHeightFraction(this)
        panelOpacity = BigBangSettings.getPanelOpacity(this)
        panelAdjustmentEnabled = BigBangSettings.isPanelAdjustmentEnabled(this)
        panelOffsetXdp = BigBangSettings.getPanelOffsetXDp(this)
        panelOffsetYdp = BigBangSettings.getPanelOffsetYDp(this)
        boomChipPage?.setTextSizeSp(textSizeSp)

        val dark = DragShareSettings.readLocal(this).colorMode == DragShareSettings.COLOR_DARK
        setContent {
            MiuixTheme(
                controller = ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light),
            ) {
                Scaffold(containerColor = Color.Transparent) {
                    BigBangOverlayContent(
                        contentView = legacyContentView,
                        touchX = launchTouchX,
                        touchY = launchTouchY,
                        sourceImageAvailable = sourceImageUri != null,
                        textSizeSp = textSizeSp,
                        panelHeightFraction = panelHeightFraction,
                        panelOpacity = panelOpacity,
                        panelAdjustmentEnabled = panelAdjustmentEnabled,
                        panelOffsetXdp = panelOffsetXdp,
                        panelOffsetYdp = panelOffsetYdp,
                        onLegacyThemeChanged = ::updateLegacyTheme,
                        onDismissRequesterChanged = { animatedDismissRequester = it },
                        onDismissRequest = { shouldDismissPage() },
                        onDismissFinished = { finish() },
                        originalText = { boomChipPage?.originalText.orEmpty() },
                        onEditText = { text ->
                            boomChipPage?.prepareForReinit()
                            segmentLocally(text)
                        },
                        onSelectAll = { selectAll() },
                        onMoreAction = ::handleMoreAction,
                        onPanelPositionChanged = ::savePanelPosition,
                        onOcr = ::reopenOcr,
                        )
                }
            }
        }

        val savedText = savedInstanceState?.getString(SAVED_TEXT)
        val savedSegment = savedInstanceState?.getIntArray(SAVED_SEGMENT)
        if (savedText != null && savedSegment != null) {
            handleSegmentResult(savedText, savedSegment)
        } else {
            reinitializeFromIntent()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reinitializeFromIntent()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    fun requestAnimatedDismissFromLegacy() {
        animatedDismissRequester?.invoke() ?: finish()
    }

    fun closeAfterCopyIfEnabled() {
        if (BigBangSettings.isExitAfterCopyEnabled(this)) {
            requestAnimatedDismissFromLegacy()
        }
    }

    /** Called by the imported selection toolbar. */
    fun openSearch(text: String, type: Int) {
        if (text.isBlank()) {
            return
        }
        startActivity(
            Intent(this, SearchOverlayActivity::class.java)
                .putExtra(Intent.EXTRA_TEXT, text.trim())
                .putExtra(SearchOverlayActivity.EXTRA_SEARCH_TYPE, type),
        )
    }

    private fun updateLaunchPosition() {
        launchTouchX = intent.getIntExtra(EXTRA_TOUCH_X, -1)
        launchTouchY = intent.getIntExtra(EXTRA_TOUCH_Y, -1)
        sourceImageUri = intent.getParcelableExtra(EXTRA_SOURCE_IMAGE_URI, Uri::class.java)
    }

    private fun reinitializeFromIntent() {
        val inputText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (inputText.isNullOrBlank()) {
            finish()
            return
        }
        updateLaunchPosition()
        boomChipPage?.prepareForReinit()
        segmentLocally(inputText)
    }

    private fun segmentLocally(text: String) {
        val request = requests.begin()
        Thread({
            try {
                val result = TextSegmenter.get(this).segment(text)
                runOnUiThread {
                    if (requests.accepts(request) && !isFinishing && !isDestroyed) {
                        handleSegmentResult(text, result)
                    }
                }
            } catch (error: Throwable) {
                DragShareLog.w(TAG, "local text segmentation failed", error)
                runOnUiThread {
                    if (requests.accepts(request) && !isFinishing && !isDestroyed) {
                        Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }, "drag-share-segmenter").start()
    }

    override fun onDestroy() {
        requests.invalidate()
        super.onDestroy()
    }

    private fun handleSegmentResult(
        text: String,
        result: IntArray?,
    ) {
        if (result == null || result.isEmpty()) {
            Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (boomChipPage?.initWords(result, text, -1, launchTouchX, launchTouchY) != true) {
            Toast.makeText(this, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    private fun shouldDismissPage(): Boolean = boomChipPage?.handleClick() != true

    private fun selectAll() {
        boomChipPage?.selectAll()
    }

    private fun updateLegacyTheme(
        backgroundColor: Int,
        textColor: Int,
        summaryColor: Int,
        surfaceColor: Int,
        selectionColor: Int,
        actionColor: Int,
    ) {
        legacyBackgroundColor = backgroundColor
        legacyTextColor = textColor
        legacySummaryColor = summaryColor
        legacySurfaceColor = surfaceColor
        legacySelectionColor = selectionColor
        legacyActionColor = actionColor
        refreshLegacyTheme()
    }

    private fun refreshLegacyTheme() {
        applyLegacySegmentationTheme(
            legacyContentView,
            legacyBackgroundColor,
            legacyTextColor,
            legacySummaryColor,
            legacySurfaceColor,
            legacySelectionColor,
            legacyActionColor,
        )
    }

    private fun selectedTextOrOriginal(): String =
        boomChipPage?.selectedTextOrOriginal().orEmpty()

    private fun handleMoreAction(action: BigBangMoreAction) {
        when (action) {
            BigBangMoreAction.TextSize -> {
                textSizeSp = BigBangSettings.nextTextSizeSp(textSizeSp)
                BigBangSettings.setTextSizeSp(this, textSizeSp)
                boomChipPage?.setTextSizeSp(textSizeSp)
                Toast.makeText(this, getString(R.string.bigbang_text_size_changed, textSizeSp), Toast.LENGTH_SHORT).show()
                return
            }
            BigBangMoreAction.PanelHeight -> {
                panelHeightFraction = BigBangSettings.nextPanelHeightFraction(panelHeightFraction)
                BigBangSettings.setPanelHeightFraction(this, panelHeightFraction)
                Toast.makeText(
                    this,
                    getString(R.string.bigbang_panel_height_changed, (panelHeightFraction * 100).toInt()),
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            BigBangMoreAction.PanelOpacity -> {
                panelOpacity = BigBangSettings.nextPanelOpacity(panelOpacity)
                BigBangSettings.setPanelOpacity(this, panelOpacity)
                Toast.makeText(
                    this,
                    getString(R.string.bigbang_panel_opacity_changed, (panelOpacity * 100).toInt()),
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            BigBangMoreAction.PanelAdjustment -> {
                panelAdjustmentEnabled = !panelAdjustmentEnabled
                BigBangSettings.setPanelAdjustmentEnabled(this, panelAdjustmentEnabled)
                Toast.makeText(
                    this,
                    if (panelAdjustmentEnabled) R.string.bigbang_panel_adjustment_on else R.string.bigbang_panel_adjustment_off,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            BigBangMoreAction.SaveToStash -> saveToStash()
            BigBangMoreAction.Translate -> openSearch(selectedTextOrOriginal(), BoomActionHandler.SEARCH_TRANSLATE)
        }
    }

    private fun saveToStash() {
        val originalText = boomChipPage?.originalText.orEmpty()
        val text = selectedTextOrOriginal()
        if (text.isBlank()) {
            Toast.makeText(this, "没有可存入中转站的文字", Toast.LENGTH_SHORT).show()
            return
        }
        val savedSelection = text != originalText

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    StashStore(this@TextSegmentationActivity).use { store ->
                        store.addText(text)
                    }
                }
                Toast.makeText(
                    this@TextSegmentationActivity,
                    if (savedSelection) "所选文字已存入中转站" else "全部文字已存入中转站",
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (error: IOException) {
                DragShareLog.w(TAG, "unable to save text to stash", error)
                Toast.makeText(
                    this@TextSegmentationActivity,
                    "存入中转站失败：${error.message ?: "本地存储不可用"}",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (error: SecurityException) {
                DragShareLog.w(TAG, "permission denied while saving text to stash", error)
                Toast.makeText(
                    this@TextSegmentationActivity,
                    "存入中转站失败：系统拒绝访问本地存储",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun savePanelPosition(xDp: Float, yDp: Float) {
        panelOffsetXdp = xDp
        panelOffsetYdp = yDp
        BigBangSettings.setPanelPositionDp(this, xDp, yDp)
    }

    private fun reopenOcr() {
        val uri = sourceImageUri
        if (uri == null) {
            Toast.makeText(this, R.string.bigbang_ocr_source_missing, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(ImageOcrActivity.createRecheckIntent(this, uri))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        boomChipPage?.captureSelectedState()?.let { outState.putSerializable(SELECTED_STATE, it) }
        boomChipPage?.let { page ->
            page.originalText?.let { text ->
                outState.putString(SAVED_TEXT, text)
                outState.putIntArray(SAVED_SEGMENT, page.currentSegments)
            }
        }
        super.onSaveInstanceState(outState)
    }

    companion object {
        @JvmField
        val DBG = true

        private const val TAG = "DragShare/BigBang"
        private const val EXTRA_TOUCH_X = "dragshare_boom_touch_x"
        private const val EXTRA_TOUCH_Y = "dragshare_boom_touch_y"
        private const val SELECTED_STATE = "selected_state"
        private const val SAVED_TEXT = "saved_text"
        private const val SAVED_SEGMENT = "saved_segment"
        private const val EXTRA_SOURCE_IMAGE_URI = "dragshare_source_image_uri"

        @JvmStatic
        fun createIntent(text: String): Intent = createIntent(text, -1, -1)

        @JvmStatic
        fun createIntent(text: String, touchX: Int, touchY: Int): Intent {
            return createIntent(text, touchX, touchY, null)
        }

        @JvmStatic
        fun createIntent(text: String, touchX: Int, touchY: Int, sourceImage: Uri?): Intent {
            return Intent(Intent.ACTION_SEND).apply {
                component = ComponentName(
                    BuildConfig.APPLICATION_ID,
                    TextSegmentationActivity::class.java.name,
                )
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(EXTRA_TOUCH_X, touchX)
                putExtra(EXTRA_TOUCH_Y, touchY)
                if (sourceImage != null) {
                    putExtra(EXTRA_SOURCE_IMAGE_URI, sourceImage)
                    clipData = ClipData.newRawUri("source image", sourceImage)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        }
    }
}

private enum class BigBangMoreAction {
    Translate,
    SaveToStash,
    TextSize,
    PanelHeight,
    PanelOpacity,
    PanelAdjustment,
}

@Composable
private fun BigBangOverlayContent(
    contentView: View,
    touchX: Int,
    touchY: Int,
    sourceImageAvailable: Boolean,
    textSizeSp: Float,
    panelHeightFraction: Float,
    panelOpacity: Float,
    panelAdjustmentEnabled: Boolean,
    panelOffsetXdp: Float,
    panelOffsetYdp: Float,
    onLegacyThemeChanged: (Int, Int, Int, Int, Int, Int) -> Unit,
    onDismissRequesterChanged: ((() -> Unit)?) -> Unit,
    onDismissRequest: () -> Boolean,
    onDismissFinished: () -> Unit,
    originalText: () -> String,
    onEditText: (String) -> Unit,
    onSelectAll: () -> Unit,
    onMoreAction: (BigBangMoreAction) -> Unit,
    onPanelPositionChanged: (Float, Float) -> Unit,
    onOcr: () -> Unit,
) {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val panelMetrics = rememberOverlayPanelMetrics(panelHeightFraction = panelHeightFraction)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    var dragOffsetXdp by remember(panelOffsetXdp) { mutableFloatStateOf(panelOffsetXdp) }
    var dragOffsetYdp by remember(panelOffsetYdp) { mutableFloatStateOf(panelOffsetYdp) }
    val panelBackground = MiuixTheme.colorScheme.background
    val panelBorder = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.22f)
    val topBarColor = MiuixTheme.colorScheme.surfaceContainer
    val bottomBarColor = MiuixTheme.colorScheme.surfaceContainer
    val legacyBackgroundColor = MiuixTheme.colorScheme.background.copy(alpha = panelOpacity).toArgb()
    val legacyTextColor = MiuixTheme.colorScheme.onSurface.toArgb()
    val legacySummaryColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.toArgb()
    val legacySurfaceColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = panelOpacity).toArgb()
    val legacySelectionColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.16f).toArgb()
    val legacyActionColor = MiuixTheme.colorScheme.onSurfaceVariantActions.toArgb()
    val scrimColor = Color.Black.copy(alpha = if (dark) 0.62f else 0.48f)
    val shadowColor = Color.Black.copy(alpha = 0.5f)
    val panelShape = androidx.compose.foundation.shape.RoundedCornerShape(panelMetrics.cornerRadius)
    var panelVisible by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var editorText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var dismissing by remember { mutableStateOf(false) }
    var panelBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val enterProgress by animateFloatAsState(
        targetValue = if (panelVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "bigbang_panel_enter",
        finishedListener = {
            if (dismissing && it == 0f) {
                onDismissFinished()
            }
        },
    )
    val scrimProgress by animateFloatAsState(
        targetValue = if (panelVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "bigbang_scrim_enter",
    )
    val transformOrigin = remember(panelBounds, touchX, touchY) {
        val bounds = panelBounds
        if (bounds == null || touchX < 0 || touchY < 0) {
            TransformOrigin.Center
        } else {
            TransformOrigin(
                pivotFractionX = ((touchX - bounds.left) / bounds.width).coerceIn(0f, 1f),
                pivotFractionY = ((touchY - bounds.top) / bounds.height).coerceIn(0f, 1f),
            )
        }
    }
    val panelScale = 0.84f + (0.16f * enterProgress)
    val requestDismiss = {
        if (!dismissing && onDismissRequest()) {
            dismissing = true
            panelVisible = false
        }
    }

    DisposableEffect(requestDismiss) {
        onDismissRequesterChanged(requestDismiss)
        onDispose { onDismissRequesterChanged(null) }
    }
    LaunchedEffect(Unit) { panelVisible = true }
    SideEffect {
        onLegacyThemeChanged(
            legacyBackgroundColor,
            legacyTextColor,
            legacySummaryColor,
            legacySurfaceColor,
            legacySelectionColor,
            legacyActionColor,
        )
    }

    BackHandler(onBack = requestDismiss)
    ApplyOverlaySystemBars(
        statusBarColor = if (panelMetrics.fullScreen) topBarColor else Color.Transparent,
        navigationBarColor = if (panelMetrics.fullScreen) bottomBarColor else Color.Transparent,
        darkIcons = !dark,
    )
    OverlayScene(
        scrimColor = scrimColor.copy(alpha = scrimColor.alpha * scrimProgress),
        onDismiss = requestDismiss,
    ) {
        FloatingPanel(
            width = panelMetrics.width,
            height = panelMetrics.height,
            fillMax = panelMetrics.fullScreen,
            modifier = overlayPanelPlacement(panelMetrics, dragOffsetXdp.dp, dragOffsetYdp.dp)
                .onGloballyPositioned { coordinates -> panelBounds = coordinates.boundsInWindow() }
                .graphicsLayer {
                    alpha = enterProgress
                    scaleX = panelScale
                    scaleY = panelScale
                    this.transformOrigin = transformOrigin
                },
            shape = panelShape,
            backgroundColor = panelBackground.copy(alpha = panelOpacity),
            borderColor = panelBorder.copy(alpha = panelOpacity),
            shadowColor = if (panelMetrics.multiWindow) null else shadowColor,
        ) {
            OverlayPanelScaffold(
                topBar = {
                    OverlayHeaderBar(
                        backgroundColor = topBarColor.copy(alpha = panelOpacity),
                        modifier = Modifier.pointerInput(panelAdjustmentEnabled, density) {
                            if (!panelAdjustmentEnabled) return@pointerInput
                            detectDragGestures(
                                onDragEnd = { onPanelPositionChanged(dragOffsetXdp, dragOffsetYdp) },
                            ) { change, dragAmount ->
                                change.consume()
                                dragOffsetXdp = (dragOffsetXdp + dragAmount.x / density)
                                    .coerceIn(-configuration.screenWidthDp * 0.35f, configuration.screenWidthDp * 0.35f)
                                dragOffsetYdp = (dragOffsetYdp + dragAmount.y / density)
                                    .coerceIn(-configuration.screenHeightDp * 0.35f, configuration.screenHeightDp * 0.35f)
                            }
                        },
                        topInset = panelMetrics.topSystemInset,
                        leftInset = panelMetrics.leftSystemInset,
                        rightInset = panelMetrics.rightSystemInset,
                        leading = {
                            OverlayIconAction(
                                imageVector = Icons.Outlined.Edit,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                onClick = {
                                    editorText = originalText()
                                    showEditor = true
                                },
                                contentDescription = stringResource(R.string.bigbang_action_edit),
                            )
                            OverlayIconAction(
                                imageVector = Icons.Outlined.SelectAll,
                                tint = MiuixTheme.colorScheme.onSurface,
                                onClick = onSelectAll,
                                contentDescription = stringResource(R.string.bigbang_action_select_all),
                            )
                        },
                        center = {
                            Text(
                                text = stringResource(R.string.bigbang_overlay_title),
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        trailing = {
                            OverlayIconAction(
                                imageVector = Icons.Outlined.MoreHoriz,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                onClick = { showMoreMenu = true },
                                contentDescription = stringResource(R.string.bigbang_action_more),
                            )
                        },
                    )
                },
                bottomBar = {
                    OverlayBottomBar(
                        backgroundColor = bottomBarColor.copy(alpha = panelOpacity),
                        bottomInset = panelMetrics.bottomSystemInset,
                        leftInset = panelMetrics.leftSystemInset,
                        rightInset = panelMetrics.rightSystemInset,
                        leading = {
                            if (sourceImageAvailable) OverlayIconAction(
                                imageVector = Icons.Outlined.DocumentScanner,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                onClick = onOcr,
                                contentDescription = stringResource(R.string.bigbang_action_ocr),
                            )
                        },
                        center = {
                            OverlayIconAction(
                                iconRes = R.drawable.boom_cancel,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                onClick = requestDismiss,
                                contentDescription = stringResource(R.string.search_overlay_close),
                            )
                        },
                    )
                },
            ) { bodyModifier ->
                Column(modifier = bodyModifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(bottom = 4.dp),
                        factory = { contentView },
                    )
                }
            }
        }
    }

    OverlayDialog(
        show = showEditor,
        title = stringResource(R.string.bigbang_action_edit),
        onDismissRequest = { showEditor = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BasicTextField(
                value = editorText,
                onValueChange = { editorText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 240.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        showEditor = false
                    },
                )
                TextButton(
                    text = stringResource(R.string.bigbang_edit_apply),
                    modifier = Modifier.weight(1f),
                    enabled = editorText.isNotBlank(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        showEditor = false
                        onEditText(editorText)
                    },
                )
            }
        }
    }

    OverlayDialog(
        show = showMoreMenu,
        title = stringResource(R.string.bigbang_action_more),
        summary = stringResource(R.string.bigbang_more_summary),
        onDismissRequest = { showMoreMenu = false },
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = (configuration.screenHeightDp * 0.55f).dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val actions = listOf(
                BigBangMoreAction.Translate to stringResource(R.string.bigbang_more_translate),
                BigBangMoreAction.SaveToStash to "存入中转站",
                BigBangMoreAction.TextSize to stringResource(R.string.bigbang_more_text_size, textSizeSp),
                BigBangMoreAction.PanelHeight to stringResource(
                    R.string.bigbang_more_panel_height,
                    (panelHeightFraction * 100).toInt(),
                ),
                BigBangMoreAction.PanelOpacity to stringResource(
                    R.string.bigbang_more_panel_opacity,
                    (panelOpacity * 100).toInt(),
                ),
                BigBangMoreAction.PanelAdjustment to stringResource(
                    if (panelAdjustmentEnabled) R.string.bigbang_more_panel_adjustment_on
                    else R.string.bigbang_more_panel_adjustment_off,
                ),
            )
            actions.forEach { (action, label) ->
                TextButton(
                    text = label,
                    onClick = {
                        showMoreMenu = false
                        onMoreAction(action)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun applyLegacySegmentationTheme(
    contentView: View,
    backgroundColor: Int,
    textColor: Int,
    summaryColor: Int,
    surfaceColor: Int,
    selectionColor: Int,
    actionColor: Int,
) {
    val chipColors = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_selected), intArrayOf()),
        intArrayOf(selectionColor, surfaceColor),
    )
    contentView.setBackgroundColor(backgroundColor)
    contentView.findViewById<ViewGroup>(R.id.boom_content)?.let { content ->
        fun tintText(view: View) {
            if (view is android.widget.TextView) {
                view.setTextColor(textColor)
                view.backgroundTintList = chipColors
            } else if (view is android.widget.ImageView) {
                view.imageTintList = ColorStateList.valueOf(actionColor)
            } else if (view is ViewGroup) {
                for (index in 0 until view.childCount) tintText(view.getChildAt(index))
            }
        }
        tintText(content)
    }
    contentView.findViewById<View>(R.id.boom_multi_selected_bg)
        ?.backgroundTintList = ColorStateList.valueOf(selectionColor)
    contentView.findViewById<android.widget.TextView>(R.id.boom_adjacent_top_hint)
        ?.apply {
            setTextColor(summaryColor)
            backgroundTintList = ColorStateList.valueOf(surfaceColor)
        }
    contentView.findViewById<android.widget.TextView>(R.id.boom_adjacent_bottom_hint)
        ?.apply {
            setTextColor(summaryColor)
            backgroundTintList = ColorStateList.valueOf(surfaceColor)
        }
    listOf(R.id.multi_selected_bar, R.id.fake_multi_selected_bar).forEach { actionBarId ->
        contentView.findViewById<ViewGroup>(actionBarId)?.let { actions ->
            fun tintActions(view: View) {
                if (view is android.widget.ImageView) {
                    view.imageTintList = ColorStateList.valueOf(actionColor)
                    view.background = RippleDrawable(
                        ColorStateList.valueOf(selectionColor),
                        GradientDrawable().apply {
                            setColor(surfaceColor)
                            cornerRadius = 16f * view.resources.displayMetrics.density
                        },
                        null,
                    )
                } else if (view is ViewGroup) {
                    for (index in 0 until view.childCount) tintActions(view.getChildAt(index))
                }
            }
            tintActions(actions)
        }
    }
}
