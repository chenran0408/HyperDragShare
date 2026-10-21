package com.leaf.hyperdragshare.codex

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.overlay.OverlayDialog

internal enum class BigBangMoreAction {
    Dictionary,
    Split,
    Merge,
    ReopenOcr,
    Translate,
    UndoEdit,
    ReadQr,
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
    sourceImageAvailable: Boolean,
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
    onShare: () -> Unit,
    copying: Boolean,
    canSplit: Boolean,
    canMerge: Boolean,
    totalCharacterCount: Int,
    selectedCharacterCount: Int,
    hasTextSelection: Boolean,
    allTextSelected: Boolean,
    isSegmenting: Boolean,
    copyFeedback: String?,
    onClearSelection: () -> Unit,
    onMoreAction: (BigBangMoreAction) -> Unit,
    onPanelPositionChanged: (Float, Float) -> Unit,
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
    // 52dp header + two 44dp action rows, their gap/padding, and the native body's 8dp.
    val naturalPanelHeight = (bodyHeightPx / density).dp + 168.dp +
        if (copyFeedback == null) 0.dp else 24.dp
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
    val panelScale = 0.84f + (0.16f * enterProgress)
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
            showMoreMenu -> showMoreMenu = false
            !dismissing -> { collapsing = true; closePanel() }
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
                            Text(
                                text = stringResource(R.string.bigbang_overlay_title),
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        },
                        center = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isSegmenting) "正在分词…"
                                        else if (hasTextSelection) "已选 $selectedCharacterCount / $totalCharacterCount"
                                        else "共 $totalCharacterCount 字符",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        },
                        trailing = {
                            BigBangIconAction(
                                icon = MiuixIcons.More,
                                label = "更多操作与窗口外观",
                                enabled = !isSegmenting,
                                onClick = { showMoreMenu = true },
                            )
                            BigBangIconAction(MiuixIcons.Close,
                                label = "关闭分词窗口",
                                onClick = closePanel,
                                enabled = true)
                        },
                    )
                },
                bottomBar = {
                    val ready = !isSegmenting && totalCharacterCount > 0
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(bottomBarColor.copy(alpha = panelOpacity))
                            .padding(start = 12.dp + panelMetrics.leftSystemInset,
                                end = 12.dp + panelMetrics.rightSystemInset,
                                top = 8.dp, bottom = 8.dp + panelMetrics.bottomSystemInset),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            BigBangIconAction(MiuixIcons.SelectAll,
                                if (allTextSelected) "取消全选" else "全选", onSelectAll, ready,
                                primary = allTextSelected,
                                modifier = Modifier.semantics { selected = allTextSelected })
                            BigBangToolAction("拆词", { onMoreAction(BigBangMoreAction.Split) }, ready && canSplit)
                            BigBangIconAction(MiuixIcons.Copy, if (copying) "正在复制" else "复制", onCopy,
                                ready && !copying, primary = true)
                            BigBangIconAction(MiuixIcons.Search, "搜索", onSearch, ready)
                            BigBangIconAction(MiuixIcons.Share, "分享", onShare, ready)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            BigBangIconAction(MiuixIcons.Edit, "编辑原文", {
                                editorText = originalText()
                                showEditor = true
                            }, ready)
                            BigBangIconAction(MiuixIcons.Merge, "合并选词",
                                { onMoreAction(BigBangMoreAction.Merge) }, ready && canMerge)
                            BigBangIconAction(MiuixIcons.Undo, "撤销", { onMoreAction(BigBangMoreAction.UndoEdit) }, ready && canUndoEdit)
                            BigBangIconAction(MiuixIcons.Translate, "翻译", { onMoreAction(BigBangMoreAction.Translate) }, ready)
                            BigBangIconAction(DictionaryIcon, "词典查词", { onMoreAction(BigBangMoreAction.Dictionary) }, ready)
                            if (sourceImageAvailable) {
                                BigBangToolAction("重新识字", { onMoreAction(BigBangMoreAction.ReopenOcr) }, ready)
                                BigBangToolAction("二维码", { onMoreAction(BigBangMoreAction.ReadQr) }, ready)
                            }
                            if (hasTextSelection && !allTextSelected)
                                BigBangToolAction("清选", onClearSelection, ready)
                        }
                    }
                },
            ) { bodyModifier ->
                Column(modifier = bodyModifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    copyFeedback?.let {
                        Text(it, color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
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
            val actions = listOf(
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
    modifier: Modifier = Modifier,
) {
    TooltipBox(text = label, enabled = enabled) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(44.dp),
            minWidth = 44.dp,
            minHeight = 44.dp,
            cornerRadius = 12.dp,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = (if (primary) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                    .copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
    }
}

@Composable
private fun BigBangToolAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    TextButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        minWidth = 56.dp,
        minHeight = 44.dp,
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 10.dp),
        textStyle = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
        colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
    )
}

