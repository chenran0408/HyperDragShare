package com.leaf.hyperdragshare.codex

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsHomePage(
    context: Context,
    settings: DragShareSettings,
    directory: List<SettingEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    homeListState: LazyListState,
    searchListState: LazyListState,
    topAppBarState: TopAppBarState,
    onOpenModule: (SettingModule) -> Unit,
    onOpenResult: (SettingEntry) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val focus = LocalFocusManager.current
    val searching = query.isNotBlank()
    val results = if (searching) directory.filter { it.matches(query) } else emptyList()
    Scaffold(topBar = {
        SmallTopAppBar(title = "HyperDragShare", scrollBehavior = scrollBehavior)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchBar(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                inputField = {
                    InputField(query = query, onQueryChange = onQueryChange,
                        onSearch = { focus.clearFocus() }, expanded = false,
                        onExpandedChange = {}, label = "搜索全部设置",
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                                Icon(MiuixIcons.Close, contentDescription = "清除搜索", modifier = Modifier.size(22.dp))
                            }
                        })
                }, expanded = false, onExpandedChange = {},
            ) {}
            LazyColumn(
                state = if (searching) searchListState else homeListState,
                modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (searching) {
                    item("search-count") {
                        SmallTitle(if (results.isEmpty()) "没有找到相关设置" else "找到 ${results.size} 项设置")
                    }
                    items(results, key = { it.id }) { entry ->
                        Card(Modifier.padding(horizontal = 12.dp)) {
                            SettingsNavigationPreference(entry.title,
                                entry.path,
                                entry.icon,
                                onClick = { focus.clearFocus(); onOpenResult(entry) })
                            if (!entry.enabled && entry.prerequisite != null) Text(entry.prerequisite,
                                modifier = Modifier.padding(start = 54.dp, end = 16.dp, bottom = 12.dp),
                                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                } else {
                    item("status") {
                        ActivationStatusCard(context)
                    }
                    items(SettingModule.entries.filter { it != SettingModule.GENERAL }, key = { it.name }) { module ->
                        Card(Modifier.padding(horizontal = 12.dp)) {
                            SettingsNavigationPreference(module.title,
                                when (module) {
                                    SettingModule.PORTAL -> "取词识字、分词、拖拽分享与中转站"
                                    SettingModule.GESTURES -> "底角、侧滑、蜂巢与任务切换"
                                    SettingModule.FREEFORM -> "启动、窗体尺寸、位置与窗外操作"
                                    SettingModule.COPY_DIRECT -> "复制卡片、识别动作与目标应用"
                                    else -> error("Unexpected home module")
                                }, module.icon, onClick = { focus.clearFocus(); onOpenModule(module) })
                        }
                    }
                    item("general") {
                        Card(Modifier.padding(horizontal = 12.dp)) {
                            SettingsNavigationPreference(SettingModule.GENERAL.title,
                                "主题、权限、重载、日志与关于", SettingModule.GENERAL.icon,
                                onClick = { focus.clearFocus(); onOpenModule(SettingModule.GENERAL) })
                        }
                    }
                }
            }
        }
        FirstRunGuide(context)
    }
}

@Composable
internal fun SettingsModulePage(
    module: SettingModule,
    entries: List<SettingEntry>,
    focusId: String?,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    // Keep inactive parameters out of the normal page; search can still reveal one
    // with its prerequisite instead of enabling the feature on the user's behalf.
    val groups = entries.filter { it.enabled || it.prerequisite == null || it.id == focusId }.groupBy { it.group }
    // Headers and rows share the same flattened order used by LazyColumn.
    val rowKeys = groups.flatMap { (group, rows) -> listOf("group:$group") + rows.map { it.id } }
    var highlighted by remember(focusId) { mutableStateOf(focusId) }
    LaunchedEffect(focusId) {
        if (focusId != null) {
            val index = rowKeys.indexOf(focusId)
            check(index >= 0) { "Setting is no longer registered: $focusId" }
            listState.scrollToItem(index)
            delay(1800)
            highlighted = null
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = module.title, largeTitle = module.title, scrollBehavior = scrollBehavior,
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(MiuixIcons.Back, contentDescription = "返回", modifier = Modifier.size(24.dp))
            } })
    }) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 20.dp),
        ) {
            groups.forEach { (group, rows) ->
                item("group:$group") {
                    Row(Modifier.padding(start = 28.dp, top = 12.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(settingGroupIcon(module, group), contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackgroundVariant, modifier = Modifier.size(18.dp))
                        SmallTitle(group, insideMargin = PaddingValues(start = 8.dp))
                    }
                }
                items(rows, key = { it.id }) { entry ->
                    Card(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                        Column(Modifier.background(if (highlighted == entry.id)
                            MiuixTheme.colorScheme.primary.copy(alpha = .14f)
                            else MiuixTheme.colorScheme.surfaceContainer)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(entry.icon, contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = if (entry.enabled) 1f else .4f),
                                    modifier = Modifier.padding(start = 16.dp).size(22.dp))
                                Box(Modifier.weight(1f)) { entry.content(entry.enabled) }
                            }
                            if (!entry.enabled && entry.prerequisite != null) Text(entry.prerequisite,
                                modifier = Modifier.padding(start = 54.dp, end = 16.dp, bottom = 12.dp),
                                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                }
            }
        }
    }
}
