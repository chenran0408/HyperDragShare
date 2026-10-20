package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.basic.Scaffold

/** A module-UID panel session shared by Activity and interactive overlay hosts. */
internal class BigBangPanelSession(private val context: Context, private val actions: Actions) {
    interface Actions {
        fun dismiss()
        fun search(text: String, type: Int)
        fun share(text: String)
        fun saveToStash(text: String, selectedOnly: Boolean)
        fun reopenOcr(uri: Uri)
    }
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private val dark = DragShareSettings.readLocal(context).colorMode == DragShareSettings.COLOR_DARK
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


    init {
        legacyContentView = LayoutInflater.from(context).inflate(R.layout.boom_activity_layout, null, false)
        legacyContentView.findViewById<View>(R.id.boom_page).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            BoomAnimator.makeFadeIn(this, BoomAnimator.BOOM_DURATION)
        }
        boomChipPage = BoomChipPage(context, legacyContentView, false, object : BoomChipPage.Actions {
            override fun dismiss() = requestAnimatedDismissFromLegacy()
            override fun afterCopy() = closeAfterCopyIfEnabled()
            override fun search(text: String, type: Int) = openSearch(text, type)
            override fun share(text: String) = actions.share(text)
        }).also { page ->
            page.setOnChipsChangedListener(::refreshLegacyTheme)
            page.setOnSelectionChangedListener(::updateSelectionSummary)
        }
        textSizeSp = BigBangSettings.getTextSizeSp(context)
        panelHeightFraction = BigBangSettings.getPanelHeightFraction(context)
        panelOpacity = BigBangSettings.getPanelOpacity(context)
        panelAdjustmentEnabled = BigBangSettings.isPanelAdjustmentEnabled(context)
        panelOffsetXdp = BigBangSettings.getPanelOffsetXDp(context)
        panelOffsetYdp = BigBangSettings.getPanelOffsetYDp(context)
        boomChipPage?.setTextSizeSp(textSizeSp)

    }

    @Composable
    fun Content() {

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
                        onDismissFinished = { actions.dismiss() },
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

    fun open(text: String, touchX: Int, touchY: Int, sourceImage: Uri?, savedState: Bundle? = null) {
        check(!closed) { "Create a new panel session after close" }
        launchTouchX = touchX
        launchTouchY = touchY
        sourceImageUri = sourceImage
        boomChipPage?.prepareForReinit()
        boomChipPage?.restoreSelectedState(savedState?.getSerializable(SELECTED_STATE))
        val savedText = savedState?.getString(SAVED_TEXT)
        val savedSegments = savedState?.getIntArray(SAVED_SEGMENT)
        if (savedText != null && savedSegments != null) handleSegmentResult(savedText, savedSegments)
        else segmentLocally(text)
    }

    fun close() {
        closed = true
        requests.invalidate()
        animatedDismissRequester = null
    }

    private fun segmentLocally(text: String) {
        val request = requests.begin()
        Thread({
            try {
                val result = TextSegmenter.get(context).segment(text)
                main.post {
                    if (!closed && requests.accepts(request)) handleSegmentResult(text, result)
                }
            } catch (error: IllegalStateException) {
                segmentationFailed(request, error)
            } catch (error: UnsatisfiedLinkError) {
                segmentationFailed(request, error)
            } catch (error: SecurityException) {
                segmentationFailed(request, error)
            }
        }, "drag-share-segmenter").start()
    }

    private fun segmentationFailed(request: Long, error: Throwable) {
        DragShareLog.w(TAG, "local text segmentation failed", error)
        main.post {
            if (!closed && requests.accepts(request)) {
                Toast.makeText(context, "分词失败：${error.message ?: "词典或分词库不可用"}", Toast.LENGTH_LONG).show()
                actions.dismiss()
            }
        }
    }

    private fun openSearch(text: String, type: Int) {
        if (text.isNotBlank()) actions.search(text.trim(), type)
    }

    private fun shouldDismissPage(): Boolean = boomChipPage?.handleClick() != true
    private fun selectAll() { boomChipPage?.selectAll() }
    private fun selectedTextOrOriginal(): String = boomChipPage?.selectedTextOrOriginal().orEmpty()
    private fun saveToStash() {
        val text = selectedTextOrOriginal()
        if (text.isBlank()) Toast.makeText(context, "没有可存入中转站的文字", Toast.LENGTH_SHORT).show()
        else actions.saveToStash(text, text != boomChipPage?.originalText.orEmpty())
    }
    fun requestAnimatedDismissFromLegacy() {
        animatedDismissRequester?.invoke() ?: actions.dismiss()
    }

    fun closeAfterCopyIfEnabled() {
        if (BigBangSettings.isExitAfterCopyEnabled(context)) {
            requestAnimatedDismissFromLegacy()
        }
    }

    private fun handleSegmentResult(
        text: String,
        result: IntArray?,
    ) {
        if (result == null || result.isEmpty()) {
            Toast.makeText(context, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
            actions.dismiss()
            return
        }
        if (boomChipPage?.initWords(result, text, -1, launchTouchX, launchTouchY) != true) {
            Toast.makeText(context, R.string.a_msg_no_words, Toast.LENGTH_SHORT).show()
            actions.dismiss()
            return
        }
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

    private fun handleMoreAction(action: BigBangMoreAction) {
        when (action) {
            BigBangMoreAction.TextSize -> {
                textSizeSp = BigBangSettings.nextTextSizeSp(textSizeSp)
                BigBangSettings.setTextSizeSp(context, textSizeSp)
                boomChipPage?.setTextSizeSp(textSizeSp)
                Toast.makeText(context, context.getString(R.string.bigbang_text_size_changed, textSizeSp), Toast.LENGTH_SHORT).show()
                return
            }
            BigBangMoreAction.PanelHeight -> {
                panelHeightFraction = BigBangSettings.nextPanelHeightFraction(panelHeightFraction)
                BigBangSettings.setPanelHeightFraction(context, panelHeightFraction)
                Toast.makeText(
                    context,
                    context.getString(R.string.bigbang_panel_height_changed, (panelHeightFraction * 100).toInt()),
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            BigBangMoreAction.PanelOpacity -> {
                panelOpacity = BigBangSettings.nextPanelOpacity(panelOpacity)
                BigBangSettings.setPanelOpacity(context, panelOpacity)
                Toast.makeText(
                    context,
                    context.getString(R.string.bigbang_panel_opacity_changed, (panelOpacity * 100).toInt()),
                    Toast.LENGTH_SHORT,
                ).show()
                return
            }
            BigBangMoreAction.PanelAdjustment -> {
                panelAdjustmentEnabled = !panelAdjustmentEnabled
                BigBangSettings.setPanelAdjustmentEnabled(context, panelAdjustmentEnabled)
                Toast.makeText(
                    context,
                    if (panelAdjustmentEnabled) R.string.bigbang_panel_adjustment_on else R.string.bigbang_panel_adjustment_off,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            BigBangMoreAction.SaveToStash -> saveToStash()
            BigBangMoreAction.Translate -> openSearch(selectedTextOrOriginal(), BoomActionHandler.SEARCH_TRANSLATE)
        }
    }

    private fun savePanelPosition(xDp: Float, yDp: Float) {
        panelOffsetXdp = xDp
        panelOffsetYdp = yDp
        BigBangSettings.setPanelPositionDp(context, xDp, yDp)
    }

    private fun reopenOcr() {
        val uri = sourceImageUri
        if (uri == null) {
            Toast.makeText(context, R.string.bigbang_ocr_source_missing, Toast.LENGTH_SHORT).show()
            return
        }
        actions.reopenOcr(uri)
    }

    fun saveState(outState: Bundle) {
        boomChipPage?.captureSelectedState()?.let { outState.putSerializable(SELECTED_STATE, it) }
        boomChipPage?.let { page ->
            page.originalText?.let { text ->
                outState.putString(SAVED_TEXT, text)
                outState.putIntArray(SAVED_SEGMENT, page.currentSegments)
            }
        }
    }
    companion object {
        private const val TAG = "DragShare/BigBang"
        private const val SELECTED_STATE = "selected_state"
        private const val SAVED_TEXT = "saved_text"
        private const val SAVED_SEGMENT = "saved_segment"
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
