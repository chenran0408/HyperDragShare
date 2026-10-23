package com.leaf.hyperdragshare.codex

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Cut
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.overlay.OverlayDialog

private val SELECTION_ACTION_TOOLBAR_HEIGHT = 48.dp
private val SELECTION_ACTION_TOOLBAR_GAP = 8.dp
private val SELECTION_ACTION_TOP_RESERVE =
    SELECTION_ACTION_TOOLBAR_HEIGHT + SELECTION_ACTION_TOOLBAR_GAP

internal fun selectionToolbarTopY(
    anchorYpx: Int,
    pageTopInsetPx: Int,
    toolbarHeightPx: Int,
    gapPx: Int,
    viewportHeightPx: Int,
): Int {
    val anchoredTop = anchorYpx + pageTopInsetPx - gapPx - toolbarHeightPx
    return anchoredTop.coerceIn(0, (viewportHeightPx - toolbarHeightPx).coerceAtLeast(0))
}

internal enum class BigBangMoreAction {
    Dictionary,
    Direct,
    Split,
    Merge,
    Translate,
    UndoEdit,
    TextSize,
    PanelHeight,
    PanelOpacity,
    PanelAdjustment,
}

@Composable
internal fun BigBangOverlayContent(
    contentView: View,
    touchX: Int,
    touchY: Int,
    textSizeSp: Float,
    panelHeightFraction: Float,
    panelOpacity: Float,
    panelAdjustmentEnabled: Boolean,
    panelOffsetXdp: Float,
    panelOffsetYdp: Float,
    onLegacyThemeChanged: (Int, Int, Int, Int, Int, Int) -> Unit,
    onDismissRequesterChanged: ((() -> Unit)?) -> Unit,
    onDismissFinished: () -> Unit,
    onCollapseFinished: () -> Unit,
    originalText: () -> String,
    onEditText: (String) -> Unit,
    canUndoEdit: Boolean,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onSearch: () -> Unit,
    onStash: () -> Unit,
    directAction: DirectEntityDestination?,
    copying: Boolean,
    stashing: Boolean,
    canSplit: Boolean,
    canMerge: Boolean,
    totalCharacterCount: Int,
    selectedCharacterCount: Int,
    hasTextSelection: Boolean,
    selectionActionAnchorY: Int,
    allTextSelected: Boolean,
    isSegmenting: Boolean,
    isRecognizing: Boolean,
    copyFeedback: String?,
    onMoreAction: (BigBangMoreAction) -> Unit,
    onPanelPositionChanged: (Float, Float) -> Unit,
    onDismissDirect: () -> Unit = {},
    directVisible: Boolean = false,
    directContent: @Composable () -> Unit = {},
) {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val maximumMetrics = rememberOverlayPanelMetrics(panelHeightFraction = panelHeightFraction)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    val words = remember(contentView) { contentView.findViewById<View>(R.id.boom_content) }
    val table = remember(contentView) { contentView.findViewById<View>(R.id.boom_table) }
    val scroller = remember(contentView) { contentView.findViewById<View>(R.id.boom_scroller) }
    fun naturalBodyHeight() = words.height + table.paddingTop + table.paddingBottom +
        scroller.paddingTop + scroller.paddingBottom
    var bodyHeightPx by remember(contentView) { mutableIntStateOf(naturalBodyHeight()) }
    DisposableEffect(contentView) {
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            bodyHeightPx = naturalBodyHeight()
        }
        words.addOnLayoutChangeListener(listener)
        onDispose { words.removeOnLayoutChangeListener(listener) }
    }
    val actionReady = !isSegmenting && totalCharacterCount > 0
    val hasSelectionToolbar = hasTextSelection && actionReady && selectionActionAnchorY >= 0
    val selectionToolbarTopInset = if (hasSelectionToolbar) SELECTION_ACTION_TOP_RESERVE else 0.dp
    // The full-text toolbar is present only without a selection; selected actions float by the selected words.
    val naturalPanelHeight = (bodyHeightPx / density).dp +
        (if (hasTextSelection) 60.dp + selectionToolbarTopInset else 187.dp) +
        (if (isRecognizing) 88.dp else 0.dp)
    val panelMetrics = if (maximumMetrics.fullScreen) maximumMetrics else maximumMetrics.copy(
        height = minOf(maximumMetrics.height, naturalPanelHeight),
    )
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
    var collapsing by remember { mutableStateOf(false) }
    var panelBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val enterProgress by animateFloatAsState(
        targetValue = if (panelVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "bigbang_panel_enter",
        finishedListener = {
            if (dismissing && it == 0f) {
                if (collapsing) onCollapseFinished() else onDismissFinished()
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
    val closePanel: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            panelVisible = false
        }
    }
    val requestDismiss: () -> Unit = {
        when {
            showEditor -> {
                focusManager.clearFocus()
                keyboard?.hide()
                showEditor = false
            }
            directVisible -> onDismissDirect()
            showMoreMenu -> showMoreMenu = false
            !dismissing -> { collapsing = true; closePanel() }
        }
    }

    DisposableEffect(requestDismiss) {
        onDismissRequesterChanged(requestDismiss)
        onDispose { onDismissRequesterChanged(null) }
    }
    LaunchedEffect(Unit) { panelVisible = true }
    DisposableEffect(legacyBackgroundColor, legacyTextColor, legacySummaryColor,
        legacySurfaceColor, legacySelectionColor, legacyActionColor) {
        onLegacyThemeChanged(
            legacyBackgroundColor,
            legacyTextColor,
            legacySummaryColor,
            legacySurfaceColor,
            legacySelectionColor,
            legacyActionColor,
        )
        onDispose { }
    }

    BackHandler(onBack = requestDismiss)
    ApplyOverlaySystemBars(
        statusBarColor = if (panelMetrics.fullScreen) topBarColor else Color.Transparent,
        navigationBarColor = if (panelMetrics.fullScreen) bottomBarColor else Color.Transparent,
        darkIcons = !dark,
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val availableWidth = maxWidth
      val availableHeight = maxHeight
      val sideMargin = if (panelMetrics.fullScreen) 0.dp else 12.dp
      val placedMetrics = panelMetrics.copy(
          width = minOf(panelMetrics.width, (availableWidth - sideMargin * 2).coerceAtLeast(0.dp)),
          height = minOf(panelMetrics.height, availableHeight),
          offsetY = 0.dp,
      )
      val maxOffsetX = ((availableWidth - placedMetrics.width) / 2 - sideMargin).coerceAtLeast(0.dp)
      val maxOffsetY = ((availableHeight - placedMetrics.height) / 2).coerceAtLeast(0.dp)
      val placedOffsetX = dragOffsetXdp.dp.coerceIn(-maxOffsetX, maxOffsetX)
      val placedOffsetY = dragOffsetYdp.dp.coerceIn(-maxOffsetY, maxOffsetY)
      OverlayScene(
        scrimColor = scrimColor,
        scrimOpacity = { scrimProgress },
        onDismiss = requestDismiss,
      ) {
        FloatingPanel(
            width = placedMetrics.width,
            height = placedMetrics.height,
            fillMax = placedMetrics.fullScreen,
            modifier = overlayPanelPlacement(placedMetrics, placedOffsetX, placedOffsetY)
                .onGloballyPositioned { coordinates -> panelBounds = coordinates.boundsInWindow() }
                .graphicsLayer {
                    alpha = enterProgress
                    scaleX = 0.84f + (0.16f * enterProgress)
                    scaleY = scaleX
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
                        modifier = Modifier.pointerInput(panelAdjustmentEnabled, density, maxOffsetX, maxOffsetY) {
                            if (!panelAdjustmentEnabled) return@pointerInput
                            detectDragGestures(
                                onDragEnd = { onPanelPositionChanged(dragOffsetXdp, dragOffsetYdp) },
                            ) { change, dragAmount ->
                                change.consume()
                                dragOffsetXdp = (dragOffsetXdp + dragAmount.x / density)
                                    .coerceIn(-maxOffsetX.value, maxOffsetX.value)
                                dragOffsetYdp = (dragOffsetYdp + dragAmount.y / density)
                                    .coerceIn(-maxOffsetY.value, maxOffsetY.value)
                            }
                        },
                        topInset = placedMetrics.topSystemInset,
                        leftInset = placedMetrics.leftSystemInset,
                        rightInset = placedMetrics.rightSystemInset,
                        leading = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BigBangIconAction(
                                    icon = MiuixIcons.Edit,
                                    label = "编辑原文",
                                    enabled = actionReady,
                                    onClick = { editorText = originalText(); showEditor = true },
                                )
                                BigBangIconAction(
                                    icon = MiuixIcons.Tasks,
                                    label = if (allTextSelected) "取消全选" else "全选",
                                    enabled = actionReady,
                                    onClick = onSelectAll,
                                    toggled = allTextSelected,
                                )
                            }
                        },
                        center = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.bigbang_overlay_title),
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (isRecognizing) "正在识别…"
                                        else if (isSegmenting) "正在分词…"
                                        else if (copyFeedback != null) copyFeedback.orEmpty()
                                        else if (hasTextSelection) "已选 $selectedCharacterCount / $totalCharacterCount"
                                        else "共 $totalCharacterCount 字符",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BigBangIconAction(
                                    icon = MiuixIcons.Undo,
                                    label = "撤销上次拆分、合并或编辑",
                                    enabled = actionReady && canUndoEdit,
                                    onClick = { onMoreAction(BigBangMoreAction.UndoEdit) },
                                )
                                BigBangIconAction(
                                    icon = MiuixIcons.More,
                                    label = "窗口外观",
                                    enabled = !isSegmenting,
                                    onClick = { showMoreMenu = true },
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!hasTextSelection) Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(bottomBarColor.copy(alpha = panelOpacity))
                            .padding(start = 8.dp + placedMetrics.leftSystemInset,
                                end = 8.dp + placedMetrics.rightSystemInset,
                                top = 6.dp, bottom = 6.dp + placedMetrics.bottomSystemInset),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(1.dp)
                                .background(panelBorder.copy(alpha = panelOpacity)),
                        )
                        Text(
                            text = "全文操作",
                            modifier = Modifier.fillMaxWidth().height(20.dp)
                                .padding(start = 8.dp, top = 2.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            BigBangToolbarAction(Modifier.weight(1f), MiuixIcons.Copy,
                                if (copying) "正在复制" else "复制选中内容或原文", onCopy,
                                actionReady && !copying, primary = true)
                            BigBangToolbarAction(Modifier.weight(1f), MiuixIcons.Search,
                                "搜索选中内容或原文", onSearch, actionReady)
                            BigBangToolbarAction(Modifier.weight(1f), DictionaryIcon,
                                "用词典查询选中内容", { onMoreAction(BigBangMoreAction.Dictionary) }, actionReady)
                            BigBangToolbarAction(Modifier.weight(1f), MiuixIcons.Translate,
                                "翻译选中内容或原文", { onMoreAction(BigBangMoreAction.Translate) }, actionReady)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BigBangToolbarAction(Modifier.width(56.dp), MiuixIcons.Link,
                                "链接、邮箱与电话直达", {
                                    onMoreAction(BigBangMoreAction.Direct)
                                }, actionReady, primary = directAction != null)
                            BigBangToolbarAction(Modifier.width(56.dp), MiuixIcons.Folder,
                                if (stashing) "正在加入中转站" else "加入中转站", onStash,
                                actionReady && !stashing)
                        }
                    }
                },
            ) { bodyModifier ->
                Column(modifier = bodyModifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isRecognizing) {
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("正在识别微信文字…", color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 13.sp)
                        }
                    } else {
                        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                            val reservedTop = minOf(selectionToolbarTopInset, maxHeight)
                            AndroidView(
                                modifier = Modifier.fillMaxSize().padding(
                                    top = reservedTop,
                                    bottom = 4.dp,
                                ),
                                factory = { contentView },
                            )
                            if (hasSelectionToolbar) {
                                val toolbarHeight = SELECTION_ACTION_TOOLBAR_HEIGHT
                                val toolbarY = with(LocalDensity.current) {
                                    selectionToolbarTopY(
                                        anchorYpx = selectionActionAnchorY,
                                        pageTopInsetPx = reservedTop.roundToPx(),
                                        toolbarHeightPx = toolbarHeight.roundToPx(),
                                        gapPx = SELECTION_ACTION_TOOLBAR_GAP.roundToPx(),
                                        viewportHeightPx = maxHeight.roundToPx(),
                                    ).toDp()
                                }
                                Row(
                                    modifier = Modifier.align(Alignment.TopCenter)
                                        .offset(y = toolbarY)
                                        .widthIn(max = maxWidth)
                                        .horizontalScroll(rememberScrollState())
                                        .background(
                                            MiuixTheme.colorScheme.surfaceContainer.copy(alpha = panelOpacity),
                                            RoundedCornerShape(16.dp),
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    BigBangToolbarAction(
                                        Modifier.width(44.dp), MiuixIcons.Cut,
                                        "拆分选中词语", { onMoreAction(BigBangMoreAction.Split) },
                                        canSplit,
                                    )
                                    BigBangToolbarAction(
                                        Modifier.width(44.dp), MiuixIcons.Merge,
                                        "合并选中词语", { onMoreAction(BigBangMoreAction.Merge) },
                                        canMerge,
                                    )
                                    BigBangToolbarAction(
                                        Modifier.width(44.dp), MiuixIcons.Copy,
                                        if (copying) "正在复制" else "复制选中内容", onCopy,
                                        !copying, primary = true,
                                    )
                                    BigBangToolbarAction(
                                        Modifier.width(44.dp), MiuixIcons.Search,
                                        "搜索选中内容", onSearch, true,
                                    )
                                    BigBangToolbarAction(
                                        Modifier.width(44.dp), DictionaryIcon,
                                        "用词典查询选中内容",
                                        { onMoreAction(BigBangMoreAction.Dictionary) }, true,
                                    )
                                    BigBangToolbarAction(
                                        Modifier.width(44.dp), MiuixIcons.Translate,
                                        "翻译选中内容",
                                        { onMoreAction(BigBangMoreAction.Translate) }, true,
                                    )
                                    BigBangToolbarAction(
                                        Modifier.width(44.dp), MiuixIcons.Link,
                                        "链接、邮箱与电话直达",
                                        { onMoreAction(BigBangMoreAction.Direct) },
                                        true, primary = directAction != null,
                                    )
                                    BigBangToolbarAction(
                                        Modifier.width(44.dp), MiuixIcons.Folder,
                                        if (stashing) "正在加入中转站" else "加入中转站", onStash,
                                        !stashing,
                                    )
                                }
                            }
                        }
                        if (hasTextSelection && placedMetrics.bottomSystemInset > 0.dp) {
                            Spacer(Modifier.height(placedMetrics.bottomSystemInset))
                        }
                    }
                }
            }
        }
      }
    }

    OverlayDialog(
        show = directVisible,
        title = "直达",
        onDismissRequest = onDismissDirect,
    ) {
        directContent()
    }

    OverlayDialog(
        show = showEditor,
        title = stringResource(R.string.bigbang_action_edit),
        onDismissRequest = {
            focusManager.clearFocus()
            keyboard?.hide()
            showEditor = false
        },
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
                    enabled = editorText.isNotBlank() && editorText != originalText(),
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
        title = "更多",
        onDismissRequest = { showMoreMenu = false },
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = (configuration.screenHeightDp * 0.55f).dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val ready = !isSegmenting && totalCharacterCount > 0
            SmallTitle("窗口外观", insideMargin = PaddingValues(start = 8.dp))
            Card {
                Column {
                    BigBangMoreRow(
                        icon = MiuixIcons.Tune,
                        title = stringResource(R.string.bigbang_more_text_size, textSizeSp),
                        enabled = ready,
                        onClick = { onMoreAction(BigBangMoreAction.TextSize) },
                    )
                    BigBangMoreRow(
                        icon = MiuixIcons.Tune,
                        title = stringResource(
                            R.string.bigbang_more_panel_height,
                            (panelHeightFraction * 100).toInt(),
                        ),
                        enabled = ready,
                        onClick = { onMoreAction(BigBangMoreAction.PanelHeight) },
                    )
                    BigBangMoreRow(
                        icon = MiuixIcons.Tune,
                        title = stringResource(
                            R.string.bigbang_more_panel_opacity,
                            (panelOpacity * 100).toInt(),
                        ),
                        enabled = ready,
                        onClick = { onMoreAction(BigBangMoreAction.PanelOpacity) },
                    )
                    BigBangMoreRow(
                        icon = MiuixIcons.Tune,
                        title = stringResource(
                            if (panelAdjustmentEnabled) R.string.bigbang_more_panel_adjustment_on
                            else R.string.bigbang_more_panel_adjustment_off,
                        ),
                        enabled = ready,
                        onClick = { onMoreAction(BigBangMoreAction.PanelAdjustment) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BigBangToolbarAction(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    primary: Boolean = false,
    toggled: Boolean = false,
) {
    Box(modifier = modifier.height(44.dp), contentAlignment = Alignment.Center) {
        TooltipBox(text = label, enabled = enabled) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(44.dp).background(
                color = if (enabled && (primary || toggled)) MiuixTheme.colorScheme.primary.copy(alpha = 0.13f)
                    else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                ).semantics { selected = toggled },
                minWidth = 44.dp,
                minHeight = 44.dp,
                cornerRadius = 12.dp,
            ) {
                Icon(icon, label, modifier = Modifier.size(22.dp),
                    tint = (if (primary || toggled) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurface).copy(alpha = if (enabled) 1f else 0.38f))
            }
        }
    }
}

@Composable
private fun BigBangMoreRow(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = title,
        enabled = enabled,
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MiuixTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f),
            )
        },
        onClick = onClick,
    )
}

// Miuix 0.9.3 has no dictionary icon. An outlined book with an A distinguishes lookup from translation.
private val DictionaryIcon = ImageVector.Builder(
    name = "Dictionary", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6f, 3f)
        lineTo(18f, 3f)
        quadTo(20f, 3f, 20f, 5f)
        lineTo(20f, 20.5f)
        lineTo(6f, 20.5f)
        quadTo(3.5f, 20.5f, 3.5f, 18f)
        lineTo(3.5f, 5.5f)
        quadTo(3.5f, 3f, 6f, 3f)
        close()
        moveTo(3.5f, 18f)
        quadTo(3.5f, 16f, 6f, 16f)
        lineTo(20f, 16f)
        moveTo(6.5f, 3f)
        lineTo(6.5f, 16f)
        moveTo(10f, 12.5f)
        lineTo(13f, 6.5f)
        lineTo(16f, 12.5f)
        moveTo(11f, 10.5f)
        lineTo(15f, 10.5f)
    }
}.build()

@Composable
private fun BigBangIconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    primary: Boolean = false,
    toggled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    TooltipBox(text = label, enabled = enabled) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .size(44.dp)
                .background(
                    color = if (enabled && (primary || toggled)) {
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.13f)
                    } else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                ),
            minWidth = 44.dp,
            minHeight = 44.dp,
            cornerRadius = 12.dp,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = (if (primary || toggled) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurface)
                    .copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
    }
}

