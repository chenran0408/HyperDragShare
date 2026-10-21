package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
        fun collapse()
        fun searchDirect(text: String)
        fun search(text: String, type: Int)
        fun share(text: String)
        fun dragText(source: View, text: String): Boolean
        fun openExternal(intent: android.content.Intent)
        fun readQr(uri: Uri)
    }
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var selectionSummaryFrameScheduled = false
    private val selectionSummaryFrameCallback = Choreographer.FrameCallback {
        selectionSummaryFrameScheduled = false
        if (!closed) updateSelectionSummary()
    }
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
    private var lastCountedOriginal: String? = null
    private var selectedCharacterCount by mutableIntStateOf(0)
    private var hasTextSelection by mutableStateOf(false)
    private var isSegmenting by mutableStateOf(true)
    private var copying by mutableStateOf(false)
    private var copyFeedback by mutableStateOf<String?>(null)
    private var undoEditState by mutableStateOf<Bundle?>(null)
    private var beforeText: String? = null
    private var afterText: String? = null
    private var directText by mutableStateOf<String?>(null)

    private fun updateSelectionSummary() {
        if (!copying) copyFeedback = null
        val page = boomChipPage ?: return
        val original = page.originalText
        if (lastCountedOriginal !== original) {
            lastCountedOriginal = original
            totalCharacterCount = original?.codePointCount(0, original.length) ?: 0
        }
        hasTextSelection = page.hasSelection()
        selectedCharacterCount = page.selectedCharacterCount()
    }

    private fun scheduleSelectionSummary() {
        if (closed || selectionSummaryFrameScheduled) return
        selectionSummaryFrameScheduled = true
        Choreographer.getInstance().postFrameCallback(selectionSummaryFrameCallback)
    }


    init {
        legacyContentView = LayoutInflater.from(context).inflate(R.layout.boom_activity_layout, null, false)
        boomChipPage = BoomChipPage(context, legacyContentView, false, object : BoomChipPage.Actions {
            override fun dismiss() = requestAnimatedDismissFromLegacy()
            override fun copy(text: String) = copyText(text)
            override fun search(text: String, type: Int) = openSearch(text, type)
            override fun share(text: String) = actions.share(text)
            override fun dragText(source: View, text: String) = actions.dragText(source, text)
        }).also { page ->
            page.setOnChipsChangedListener(::refreshLegacyTheme)
            page.setOnSelectionChangedListener(::scheduleSelectionSummary)
            page.setOnAdjacentRequestListener(object : BoomChipPage.OnAdjacentRequestListener {
                override fun peekAdjacentText(direction: String): String? =
                    if (direction == "before") beforeText else afterText
                override fun onAdjacentRequest(direction: String) = expandAdjacent(direction)
            })
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
        val dark = DragShareSettings.readLocal(context).isDarkThemeInComposition()

        MiuixTheme(
            controller = remember(dark) { ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light) },
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
                    onDismissFinished = { actions.dismiss() },
                    onCollapseFinished = { actions.collapse() },
                    originalText = { boomChipPage?.originalText.orEmpty() },
                    onEditText = { text ->
                        if (text != boomChipPage?.originalText) {
                            directText = null
                            undoEditState = Bundle().also {
                                saveState(it)
                                it.remove(UNDO_EDIT)
                            }
                            boomChipPage?.prepareForReinit()
                            segmentLocally(text)
                        }
                    },
                    canUndoEdit = undoEditState != null,
                    onSelectAll = { selectAll() },
                    onCopy = { copyText(selectedTextOrOriginal()) },
                    onSearch = { openSearch(selectedTextOrOriginal(), BoomActionHandler.SEARCH_WEB) },
                    onShare = { actions.share(selectedTextOrOriginal()) },
                    copying = copying,
                    canSplit = hasTextSelection && boomChipPage?.let {
                        it.mLayout.canSplitSelectedWords(it.mBoomActionHandler.mSelectedId) } == true,
                    canMerge = hasTextSelection && boomChipPage?.let {
                        it.mLayout.canMergeSelectedWords(it.mBoomActionHandler.mSelectedId) } == true,
                    totalCharacterCount = totalCharacterCount,
                    selectedCharacterCount = selectedCharacterCount,
                    hasTextSelection = hasTextSelection,
                    allTextSelected = boomChipPage?.mBoomActionHandler?.isAllSelected() == true,
                    isSegmenting = isSegmenting,
                    copyFeedback = copyFeedback,
                    onClearSelection = { boomChipPage?.handleClick() },
                    onMoreAction = ::handleMoreAction,
                    onPanelPositionChanged = ::savePanelPosition,
                    directVisible = directText != null,
                    directContent = {
                        directText?.let { text ->
                            CopyDirectSuggestions(context, text, onDismiss = { directText = null },
                                onOpenExternal = actions::openExternal,
                                onSearch = actions::searchDirect)
                        }
                    },
                    )
            }
        }

    }

    fun open(text: String, touchX: Int, touchY: Int, sourceImage: Uri?, savedState: Bundle? = null,
        before: String? = null, after: String? = null) {
        check(!closed) { "Create a new panel session after close" }
        directText = null
        requests.invalidate()
        undoEditState = savedState?.getBundle(UNDO_EDIT)
        beforeText = (if (savedState != null) savedState.getString("before_text") else before)?.takeIf { it.isNotBlank() }
        afterText = (if (savedState != null) savedState.getString("after_text") else after)?.takeIf { it.isNotBlank() }
        launchTouchX = touchX
        launchTouchY = touchY
        sourceImageUri = sourceImage
        boomChipPage?.prepareForReinit()
        boomChipPage?.restoreSelectedState(savedState?.getSerializable(SELECTED_STATE))
        val savedText = savedState?.getString(SAVED_TEXT)
        val savedSegments = savedState?.getIntArray(SAVED_SEGMENT)
        if (savedText != null && savedSegments != null) handleSegmentResult(savedText, savedSegments)
        else segmentLocally(text)
        boomChipPage?.finishAdjacentPull()
    }

    private fun expandAdjacent(direction: String) {
        val page = boomChipPage ?: return
        val prepend = direction == "before"
        val adjacent = (if (prepend) beforeText else afterText) ?: return
        val original = page.originalText.orEmpty()
        val prefix = if (prepend) "$adjacent\n" else ""
        val combined = if (prepend) prefix + original else "$original\n$adjacent"
        val existingSegments = page.currentSegments
        val request = requests.begin()
        Thread({
            try {
                val neighborSegments = TextSegmenter.get(context).segment(adjacent)
                val segments = if (prepend) BoomWordsLayout.concatenateSegments(
                    neighborSegments, existingSegments, prefix.length)
                else BoomWordsLayout.concatenateSegments(existingSegments, neighborSegments, original.length + 1)
                main.post {
                    if (!closed && requests.accepts(request)) {
                        if (page.replaceWords(segments, combined, 0, prefix.length)) {
                            if (prepend) beforeText = null else afterText = null
                            scheduleSelectionSummary()
                        } else {
                            Toast.makeText(context, "无法展开附近文字，请重试", Toast.LENGTH_SHORT).show()
                        }
                        page.finishAdjacentPull()
                    }
                }
            } catch (error: IllegalStateException) {
                adjacentFailed(request, error)
            } catch (error: UnsatisfiedLinkError) {
                adjacentFailed(request, error)
            } catch (error: SecurityException) {
                adjacentFailed(request, error)
            }
        }, "bigbang-adjacent").start()
    }

    private fun adjacentFailed(request: Long, error: Throwable) {
        DragShareLog.w(TAG, "adjacent text segmentation failed", error)
        main.post {
            if (!closed && requests.accepts(request)) {
                boomChipPage?.finishAdjacentPull()
                Toast.makeText(context, "扩展文字失败：${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun showDirectActions(text: String) {
        directText = text
    }

    fun close() {
        closed = true
        if (selectionSummaryFrameScheduled) {
            Choreographer.getInstance().removeFrameCallback(selectionSummaryFrameCallback)
            selectionSummaryFrameScheduled = false
        }
        requests.invalidate()
        animatedDismissRequester = null
    }

    private fun segmentLocally(text: String) {
        isSegmenting = true
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

    private fun selectAll() { boomChipPage?.selectAll() }
    private fun selectedTextOrOriginal(): String = boomChipPage?.selectedTextOrOriginal().orEmpty()
    fun requestAnimatedDismissFromLegacy() {
        animatedDismissRequester?.invoke() ?: actions.dismiss()
    }

    fun closeAfterCopyIfEnabled() {
        if (BigBangSettings.isExitAfterCopyEnabled(context)) {
            actions.dismiss()
        }
    }

    private fun copyText(text: String) {
        if (closed || copying) return
        copying = true
        copyFeedback = "正在复制…"
        ClipboardWriter.copy(context, text) { error ->
            if (!closed) {
                copying = false
                copyFeedback = if (error == null) "已复制" else "复制失败：${error.message}"
                if (error == null) closeAfterCopyIfEnabled()
            }
        }
    }

    private fun handleSegmentResult(
        text: String,
        result: IntArray?,
    ) {
        isSegmenting = false
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
            BigBangMoreAction.Direct -> showDirectActions(selectedTextOrOriginal())
            BigBangMoreAction.Dictionary -> openSearch(selectedTextOrOriginal(), BoomActionHandler.SEARCH_DICTIONARY)
            BigBangMoreAction.Split, BigBangMoreAction.Merge -> {
                val page = boomChipPage ?: return
                val previous = Bundle().also {
                    saveState(it)
                    it.remove(UNDO_EDIT)
                }
                val changed = if (action == BigBangMoreAction.Split) page.splitSelectedWordsToChars()
                    else page.mergeSelectedWords()
                if (changed) undoEditState = previous
            }
            BigBangMoreAction.ReadQr -> sourceImageUri?.let(actions::readQr)
            BigBangMoreAction.UndoEdit -> {
                val state = undoEditState ?: return
                open(state.getString(SAVED_TEXT).orEmpty(), launchTouchX, launchTouchY, sourceImageUri, state)
            }
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
            BigBangMoreAction.Translate -> openSearch(selectedTextOrOriginal(), BoomActionHandler.SEARCH_TRANSLATE)
        }
    }

    private fun savePanelPosition(xDp: Float, yDp: Float) {
        panelOffsetXdp = xDp
        panelOffsetYdp = yDp
        BigBangSettings.setPanelPositionDp(context, xDp, yDp)
    }

    fun saveState(outState: Bundle) {
        outState.putString("before_text", beforeText)
        outState.putString("after_text", afterText)
        undoEditState?.let { outState.putBundle(UNDO_EDIT, it) }
        boomChipPage?.captureSelectedState()?.let { outState.putSerializable(SELECTED_STATE, it) }
        boomChipPage?.let { page ->
            page.originalText?.let { text ->
                outState.putString(SAVED_TEXT, text)
                outState.putIntArray(SAVED_SEGMENT, page.currentSegments)
            }
        }
    }
    companion object {
        const val UNDO_EDIT = "undo_edit"
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
}
