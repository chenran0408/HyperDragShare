package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.appiconloader.AppIconLoader
import java.util.LinkedHashSet
import kotlin.math.roundToInt

private const val ORDER_LIST_INDEX_OFFSET = 1

@Composable
internal fun VisibilityPage(
    context: Context,
    settings: DragShareSettings,
    onBack: () -> Unit,
    persist: (DragShareSettingsMutation) -> DragShareSettings,
) {
    var pageData by remember(context) { mutableStateOf<VisibilityPageData?>(null) }
    var expandedGroupKeys by remember { mutableStateOf(emptySet<String>()) }
    val scrollBehavior = MiuixScrollBehavior()
    val groups = pageData?.groups.orEmpty()
    val allKeys = pageData?.targets?.mapNotNull { it.key() }.orEmpty()
    val bulkEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = "全选",
                onClick = {
                    persist { _ ->
                        setHiddenTargetKeys(emptySet())
                    }
                },
            ),
            DropdownItem(
                text = "全不选",
                onClick = {
                    persist { _ ->
                        setHiddenTargetKeys(LinkedHashSet(allKeys))
                    }
                },
            ),
            DropdownItem(
                text = "全展开",
                onClick = { expandedGroupKeys = groups.map { it.key }.toSet() },
            ),
            DropdownItem(
                text = "全折叠",
                onClick = { expandedGroupKeys = emptySet() },
            ),
        ),
    )

    LaunchedEffect(context) {
        pageData = null
        pageData = withContext(Dispatchers.IO) {
            val targets = querySettingsTargets(context)
            VisibilityPageData(
                targets = targets,
                groups = buildGroups(context, targets),
                iconBitmaps = loadSettingsIcons(context, targets),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "拖拽分享目标",
                largeTitle = "拖拽分享目标",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    if (pageData != null) {
                        OverlayIconDropdownMenu(entry = bulkEntry) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = "批量操作",
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        if (pageData == null) {
            LoadingContent(paddingValues)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues,
            ) {
                if (groups.isEmpty()) {
                    item(key = "visibility-empty") {
                        Text(
                            text = "没有找到可用的分享应用",
                            modifier = Modifier.padding(28.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    groups.forEach { group ->
                        item(key = "visibility-group:${group.key}") {
                            val expanded = expandedGroupKeys.contains(group.key)
                            VisibilityGroup(
                                group = group,
                                settings = settings,
                                expanded = expanded,
                                appIconBitmap = pageData?.iconBitmaps?.get(group.key),
                                onToggleExpanded = {
                                    expandedGroupKeys = if (expanded) {
                                        expandedGroupKeys - group.key
                                    } else {
                                        expandedGroupKeys + group.key
                                    }
                                },
                                onGroupCheckedChange = { checked ->
                                    persist { current ->
                                        val hidden = LinkedHashSet(current.hiddenTargetKeys)
                                        group.targets.forEach { target ->
                                            if (checked) hidden.remove(target.key())
                                            else hidden.add(target.key())
                                        }
                                        setHiddenTargetKeys(hidden)
                                    }
                                },
                                onTargetCheckedChange = { target, checked ->
                                    persist { current ->
                                        val hidden = LinkedHashSet(current.hiddenTargetKeys)
                                        if (checked) hidden.remove(target.key())
                                        else hidden.add(target.key())
                                        setHiddenTargetKeys(hidden)
                                    }
                                },
                            )
                        }
                    }
                }
                item(key = "visibility-spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun VisibilityGroup(
    group: TargetGroup,
    settings: DragShareSettings,
    expanded: Boolean,
    appIconBitmap: Bitmap?,
    onToggleExpanded: () -> Unit,
    onGroupCheckedChange: (Boolean) -> Unit,
    onTargetCheckedChange: (ShareTarget, Boolean) -> Unit,
) {
    val visibleCount = group.targets.count { settings.isTargetVisible(it.key()) }
    val groupState = when (visibleCount) {
        0 -> ToggleableState.Off
        group.targets.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Column {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            onClick = onToggleExpanded,
            showIndication = true,
            insideMargin = PaddingValues(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TargetIcon(
                    target = group.targets.first(),
                    modifier = Modifier.padding(end = 10.dp),
                    size = 48.dp,
                    normalizedBitmap = appIconBitmap,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        modifier = Modifier.basicMarquee(),
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = "已显示${visibleCount}个",
                        modifier = Modifier.basicMarquee(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Checkbox(
                    state = groupState,
                    onClick = {
                        onGroupCheckedChange(groupState != ToggleableState.On)
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                group.targets.forEach { target ->
                    val checked = settings.isTargetVisible(target.key())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .width(6.dp)
                                .height(24.dp)
                                .squircleBackground(
                                    color = if (checked) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.primaryContainer
                                    },
                                    cornerRadius = 16.dp,
                                ),
                        )
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp, end = 12.dp, bottom = 6.dp),
                        ) {
                            BasicComponent(
                                startAction = {
                                    TargetIcon(
                                        target = target,
                                        modifier = Modifier.padding(end = 2.dp),
                                        size = 40.dp,
                                        normalizedBitmap = appIconBitmap,
                                    )
                                },
                                endActions = {
                                    Checkbox(
                                        state = ToggleableState(checked),
                                        onClick = { onTargetCheckedChange(target, !checked) },
                                    )
                                },
                                insideMargin = PaddingValues(horizontal = 9.dp),
                                onClick = { onTargetCheckedChange(target, !checked) },
                            ) {
                                Text(
                                    text = target.label?.toString() ?: target.key(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = targetSummary(target),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

@Composable
internal fun OrderPage(
    context: Context,
    settings: DragShareSettings,
    onBack: () -> Unit,
    persist: (DragShareSettingsMutation) -> DragShareSettings,
) {
    val items = remember { mutableStateListOf<ShareTarget>() }
    var loading by remember { mutableStateOf(true) }
    var iconBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    val listState = rememberLazyListState()
    val currentPersist = rememberUpdatedState(persist)
    val dragDropState = rememberDragDropState(
        lazyListState = listState,
        onMove = { fromIndex, toIndex ->
            val from = fromIndex - ORDER_LIST_INDEX_OFFSET
            val to = (toIndex - ORDER_LIST_INDEX_OFFSET).coerceAtLeast(0)
            if (from in items.indices && to in items.indices && from != to) {
                items.add(to, items.removeAt(from))
            }
        },
        onDragFinished = {
            currentPersist.value { _ ->
                setTargetOrder(items.map { it.key() })
            }
        },
    )
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(context, settings.hiddenTargetKeys) {
        loading = true
        val loadedSettings = settings
        val (queried, loadedIcons) = withContext(Dispatchers.IO) {
            val targets = querySettingsTargets(context)
                .filterNot { it.isBuiltIn() }
                .filter { loadedSettings.isTargetVisible(it.key()) }
            targets to loadSettingsIcons(context, targets)
        }
        items.clear()
        items.addAll(ShareTargetRepository.orderForSettings(queried, loadedSettings))
        iconBitmaps = loadedIcons
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "拖拽分享目标排序",
                largeTitle = "拖拽分享目标排序",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        if (loading) {
            LoadingContent(paddingValues)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .dragContainer(dragDropState),
                contentPadding = paddingValues,
            ) {
                item(key = "order-title") {
                    SmallTitle(text = "分享应用顺序")
                }
                if (items.isEmpty()) {
                    item(key = "order-empty") {
                        Text(
                            text = "没有找到可排序的分享应用",
                            modifier = Modifier.padding(28.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    itemsIndexed(
                        items = items,
                        key = { _, target -> target.key() ?: target.hashCode().toString() },
                    ) { index, target ->
                        DraggableItem(
                            dragDropState = dragDropState,
                            index = index + ORDER_LIST_INDEX_OFFSET,
                        ) { isDragging ->
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 3.dp),
                                cornerRadius = if (isDragging) 16.dp else 12.dp,
                                holdDownState = isDragging,
                            ) {
                                BasicComponent(
                                    title = target.label?.toString() ?: target.key(),
                                    summary = target.packageName(),
                                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                    startAction = {
                                        TargetIcon(
                                            target = target,
                                            normalizedBitmap = iconBitmaps[target.packageName()],
                                        )
                                    },
                                    endActions = {
                                        DropdownArrowEndAction(
                                            actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "order-spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private data class TargetGroup(
    val key: String,
    val title: String,
    val targets: List<ShareTarget>,
)

private data class VisibilityPageData(
    val targets: List<ShareTarget>,
    val groups: List<TargetGroup>,
    val iconBitmaps: Map<String, Bitmap>,
)

private fun querySettingsTargets(context: Context): List<ShareTarget> {
    val targets = try {
        ShareTargetRepository.queryAll(context)
    } catch (_: Throwable) {
        emptyList()
    }
    return targets + listOf(
        ShareTarget.stash(context.getDrawable(R.drawable.ic_stash)),
        ShareTarget.copyTextToClipboard(ShareTargetRepository.loadCopyIcon(context)),
        ShareTarget.copyImageToClipboard(ShareTargetRepository.loadCopyIcon(context)),
        ShareTarget.saveToLocal(ShareTargetRepository.loadSaveIcon(context)),
        ShareTarget.textSegmentation(ShareTargetRepository.loadTextSegmentationIcon(context)),
        ShareTarget.imageOcr(ShareTargetRepository.loadTextSegmentationIcon(context)),
        ShareTarget.qrScan(ShareTargetRepository.loadQrScanIcon(context)),
    )
}

private fun buildGroups(context: Context, targets: List<ShareTarget>): List<TargetGroup> {
    return targets
        .groupBy { it.packageName() }
        .map { (packageName, values) ->
            TargetGroup(
                key = packageName,
                title = if (values.firstOrNull()?.isBuiltIn() == true) {
                    "内置功能"
                } else {
                    applicationLabel(context, packageName, packageName)
                },
                targets = values.sortedWith(
                    compareBy<ShareTarget>({ !it.isBuiltIn() }, { it.label?.toString() ?: it.key() }),
                ),
            )
        }
        .sortedWith(compareBy<TargetGroup>({ it.key != "builtin" }, { it.title }))
}

private fun applicationLabel(context: Context, packageName: String, fallback: String): String {
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        info.loadLabel(context.packageManager).toString()
    } catch (_: Throwable) {
        fallback
    }
}

private fun loadSettingsIcons(
    context: Context,
    targets: List<ShareTarget>,
): Map<String, Bitmap> {
    val packageNames = targets
        .asSequence()
        .filterNot { it.isBuiltIn() }
        .map { it.packageName() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
    if (packageNames.isEmpty()) return emptyMap()

    val targetSizePx = (48f * context.resources.displayMetrics.density)
        .roundToInt()
        .coerceAtLeast(1)
    val loader = AppIconLoader(targetSizePx, false, context.applicationContext)
    val packageManager = context.packageManager
    val result = LinkedHashMap<String, Bitmap>()
    packageNames.forEach { packageName ->
        try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            result[packageName] = loader.loadIcon(applicationInfo).also { it.prepareToDraw() }
        } catch (_: Throwable) {
            // Keep the ResolveInfo drawable as a per-item fallback.
        }
    }
    return result
}

private fun targetSummary(target: ShareTarget): String {
    return when {
        target.isCopyTextToClipboard() -> "将文字复制到剪贴板"
        target.isCopyImageToClipboard() -> "将图片复制到剪贴板"
        target.isSaveToLocal() -> "仅在图片分享菜单中显示"
        target.isTextSegmentation() -> "仅在文字分享菜单中显示"
        target.isImageOcr() -> "识别图片文字后进入分词，仅在图片菜单中显示"
        target.isQrScan() -> "直接从图片解出二维码内容，仅在图片菜单中显示"
        else -> {
        target.component?.let { component ->
            component.className
                .removePrefix("${component.packageName}.")
        } ?: target.key()
        }
    }
}

@Composable
private fun TargetIcon(
    target: ShareTarget,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    normalizedBitmap: Bitmap? = null,
) {
    val builtInAction = target.isCopyToClipboard()
        || target.isSaveToLocal()
        || target.isTextSegmentation()
        || target.isImageOcr()
        || target.isQrScan()
    val targetSizePx = with(LocalDensity.current) {
        size.roundToPx().coerceAtLeast(1)
    }
    val bitmap = remember(target, normalizedBitmap, targetSizePx, builtInAction) {
        if (builtInAction) {
            drawableBitmap(ShareTargetRepository.iconForDisplay(target), targetSizePx)
        } else {
            normalizedBitmap ?: drawableBitmap(target.icon)
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size),
        )
    } else {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = target.label?.firstOrNull()?.toString() ?: "?")
        }
    }
}

private fun drawableBitmap(drawable: Drawable?, squareSizePx: Int? = null): Bitmap? {
    if (drawable == null) return null
    val snapshot = try {
        drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
    } catch (_: Throwable) {
        drawable
    }
    val width = squareSizePx ?: snapshot.intrinsicWidth.coerceAtLeast(1)
    val height = squareSizePx ?: snapshot.intrinsicHeight.coerceAtLeast(1)
    return try {
        createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            snapshot.setBounds(0, 0, width, height)
            snapshot.draw(canvas)
        }
    } catch (_: Throwable) {
        null
    }
}
