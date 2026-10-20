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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog

internal enum class BigBangMoreAction {
    Translate,
    SaveToStash,
    UndoEdit,
    CaptureMore,
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
    onDismissRequest: () -> Boolean,
    onDismissFinished: () -> Unit,
    originalText: () -> String,
    onEditText: (String) -> Unit,
    canUndoEdit: Boolean,
    canCaptureMoreText: Boolean,
    onSelectAll: () -> Unit,
    totalCharacterCount: Int,
    selectedCharacterCount: Int,
    hasTextSelection: Boolean,
    isSegmenting: Boolean,
    onClearSelection: () -> Unit,
    onMoreAction: (BigBangMoreAction) -> Unit,
    onPanelPositionChanged: (Float, Float) -> Unit,
    onOcr: () -> Unit,
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
    // Header/footer are 52dp each; the body adds 4dp above and below the native view.
    val naturalPanelHeight = (bodyHeightPx / density).dp + 112.dp
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
    val closePanel: () -> Unit = {
        if (!dismissing) {
            dismissing = true
            panelVisible = false
        }
    }
    val requestDismiss: () -> Unit = {
        if (!dismissing && onDismissRequest()) closePanel()
    }

    DisposableEffect(closePanel) {
        onDismissRequesterChanged(closePanel)
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
                                enabled = !isSegmenting,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                onClick = {
                                    editorText = originalText()
                                    showEditor = true
                                },
                                contentDescription = stringResource(R.string.bigbang_action_edit),
                            )
                            OverlayIconAction(
                                imageVector = Icons.Outlined.SelectAll,
                                enabled = !isSegmenting,
                                tint = MiuixTheme.colorScheme.onSurface,
                                onClick = onSelectAll,
                                contentDescription = stringResource(R.string.bigbang_action_select_all),
                            )
                        },
                        center = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.bigbang_overlay_title),
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
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
                            OverlayIconAction(
                                imageVector = Icons.Outlined.MoreHoriz,
                                enabled = !isSegmenting,
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
                                imageVector = Icons.Outlined.Close,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                onClick = closePanel,
                                contentDescription = stringResource(R.string.search_overlay_close),
                            )
                        },
                        trailing = {
                            if (hasTextSelection) {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                        .clickable(role = Role.Button, onClick = onClearSelection),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "取消选择",
                                        color = MiuixTheme.colorScheme.primary,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
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
            val availableActions = buildList {
                if (canUndoEdit) add(BigBangMoreAction.UndoEdit to "撤销编辑")
                if (canCaptureMoreText) add(BigBangMoreAction.CaptureMore to "追加屏幕文字")
                if (sourceImageAvailable) add(BigBangMoreAction.ReadQr to "识别原图二维码")
                addAll(actions)
            }
            availableActions.forEach { (action, label) ->
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

