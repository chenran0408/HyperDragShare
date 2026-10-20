package com.leaf.hyperdragshare.codex

import android.content.ComponentName
import android.content.ClipData
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
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Scaffold
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
    private var totalCharacterCount by mutableIntStateOf(0)
    private var selectedCharacterCount by mutableIntStateOf(0)
    private var hasTextSelection by mutableStateOf(false)

    private fun updateSelectionSummary() {
        val page = boomChipPage ?: return
        val original = page.originalText.orEmpty()
        hasTextSelection = page.hasSelection()
        totalCharacterCount = original.codePointCount(0, original.length)
        val selected = if (hasTextSelection) page.selectedTextOrOriginal() else ""
        selectedCharacterCount = selected.codePointCount(0, selected.length)
    }

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
        boomChipPage = BoomChipPage(this, legacyContentView, false, object : BoomChipPage.Actions {
            override fun dismiss() = requestAnimatedDismissFromLegacy()
            override fun afterCopy() = closeAfterCopyIfEnabled()
            override fun search(text: String, type: Int) = openSearch(text, type)
            override fun share(text: String) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    this.type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(send, null).apply {
                    flags = Intent.FLAG_ACTIVITY_NO_HISTORY
                })
            }
        }).also { page ->
            page.setOnChipsChangedListener(::refreshLegacyTheme)
            page.setOnSelectionChangedListener(::updateSelectionSummary)
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
                        totalCharacterCount = totalCharacterCount,
                        selectedCharacterCount = selectedCharacterCount,
                        hasTextSelection = hasTextSelection,
                        onClearSelection = { boomChipPage?.handleClick() },
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
