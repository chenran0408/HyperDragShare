package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.Report

@Composable
internal fun ActivationStatusCard(
    context: Context,
    monitor: ActivationMonitorState,
    onOpenDiagnostics: () -> Unit,
) {
    val snapshot = monitor.snapshot
    val state = snapshot?.overallState ?: ActivationState.CHECKING
    val summary = when (state) {
        ActivationState.CHECKING -> "正在确认运行状态${if (monitor.checking) " · 检查中" else ""}"
        ActivationState.NORMAL -> "已检查的功能运行正常，点击查看各组件状态"
        ActivationState.NEEDS_ACTION -> buildList {
            if (snapshot?.root == ActivationEvidence.UNAVAILABLE) add("Root 输入功能")
            if (snapshot?.portalFeature == ActivationState.NEEDS_ACTION) add("传送门取词与拖拽")
            if (snapshot?.overlayPermission == ActivationEvidence.UNAVAILABLE) add("部分悬浮工具")
            if (snapshot?.pressFeature == ActivationState.NEEDS_ACTION) add("按压取词")
        }.distinct().joinToString("、").let { affected ->
            if (affected.isBlank()) "有运行环节需要处理，查看具体影响和处理方法"
            else "可能影响：$affected。点击查看处理方法"
        }
        ActivationState.UNKNOWN -> "部分状态暂时无法确认；这不代表权限已被拒绝"
        ActivationState.NOT_APPLICABLE -> "当前没有需要检查的运行组件"
    }
    val title = when (state) {
        ActivationState.CHECKING -> "运行状态检查中"
        ActivationState.NORMAL -> "运行状态正常"
        ActivationState.NEEDS_ACTION -> "有功能需要处理"
        ActivationState.UNKNOWN -> "部分状态待确认"
        ActivationState.NOT_APPLICABLE -> "运行状态"
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        SettingsNavigationPreference(
            title = title,
            summary = summary,
            icon = if (state == ActivationState.NORMAL) MiuixIcons.Ok else MiuixIcons.Info,
            onClick = onOpenDiagnostics,
        )
    }
}

@Composable
internal fun ActivationDiagnosticsPage(
    context: Context,
    monitor: ActivationMonitorState,
    onBack: () -> Unit,
    onOpenGeneralSettings: () -> Unit,
    onOpenProblemDiagnostics: () -> Unit,
) {
    val snapshot = monitor.snapshot
    val rootNeeds = buildList {
        if (snapshot?.root == ActivationEvidence.UNAVAILABLE) add("MMGG")
        if (snapshot?.portalRoot == ActivationEvidence.UNAVAILABLE) add("小米传送门")
    }
    val missingPermissions = buildList {
        if (snapshot?.overlayPermission == ActivationEvidence.UNAVAILABLE) add("悬浮窗")
    }
    val portalInstalled = packageInstalled(context, PORTAL_PACKAGE)
    val aicrInstalled = packageInstalled(context, AICR_PACKAGE)
    val missingSoftware = buildList {
        if (portalInstalled == false) add("小米传送门" to "取词和相关拖拽功能需要此组件。")
        if (aicrInstalled == false) add("小米澎湃 AI 引擎" to "系统识字和 AI 识别功能需要此组件。")
    }
    val portalNeedsReload = snapshot?.injection == ActivationEvidence.UNAVAILABLE
        || snapshot?.portalService == ActivationEvidence.UNAVAILABLE
        || snapshot?.rootInput == ActivationEvidence.UNAVAILABLE
        || (snapshot?.thumbTriggerEnabled == true && snapshot.pressReader == ActivationEvidence.UNAVAILABLE)
    val hasUnknown = (snapshot?.let {
        listOf(it.root, it.injection, it.portalService, it.portalRoot, it.rootInput,
            if (it.thumbTriggerEnabled) it.pressReader else ActivationEvidence.NOT_APPLICABLE,
            it.overlayPermission).any { evidence -> evidence == ActivationEvidence.UNKNOWN }
    } ?: false) || (snapshot != null && (portalInstalled == null || aicrInstalled == null))
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                title = "运行诊断",
                largeTitle = "运行诊断",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 20.dp,
            ),
        ) {
            item("diagnostics-summary") {
                SmallTitle("功能状态")
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                    BasicComponent(
                        title = when {
                            monitor.checking || snapshot == null -> "正在检查"
                            rootNeeds.isNotEmpty() || missingPermissions.isNotEmpty()
                                    || missingSoftware.isNotEmpty() || portalNeedsReload -> "有项目需要处理"
                            hasUnknown -> "部分状态暂时无法确认"
                            else -> "已检查项目正常"
                        },
                        summary = if (monitor.checking) "正在更新状态" else
                            "这里只显示需要处理的项目；未能确认的状态不会当作权限缺失。",
                        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    )
                    SettingsNavigationPreference(
                        title = "重新检查",
                        summary = "重新读取权限和组件状态",
                        icon = MiuixIcons.Update,
                        onClick = monitor.refresh,
                    )
                }
            }
            item("diagnostics-root") {
                if (rootNeeds.isNotEmpty()) {
                    SimpleDiagnosticSection("需要 Root", rootNeeds.map { it to "请在 Root 管理器中为${it}授权后重新检查" }) {
                        monitor.refresh()
                    }
                }
            }
            item("diagnostics-permissions") {
                if (missingPermissions.isNotEmpty()) {
                    SimpleDiagnosticSection("缺少权限", missingPermissions.map { it to "允许 MMGG 显示悬浮窗" }) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            .setData(android.net.Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }
            }
            item("diagnostics-software") {
                if (missingSoftware.isNotEmpty()) {
                    SimpleDiagnosticSection("缺少软件", missingSoftware.map { (name, impact) ->
                        name to "${impact}请从系统组件更新或设备厂商提供的渠道安装。"
                    })
                }
            }
            item("diagnostics-reload") {
                if (portalNeedsReload && missingSoftware.none { it.first == "小米传送门" }) {
                    SimpleDiagnosticSection("需要检查或重载", listOf("小米传送门" to "检查 LSPosed 作用域后，在通用设置中热重载传送门")) {
                        onOpenGeneralSettings()
                    }
                }
            }
            item("diagnostics-unknown") {
                if (hasUnknown) {
                    Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                        BasicComponent(
                            title = "有状态暂时无法确认",
                            summary = "这不代表权限未授予或软件缺失。需要继续排查时，可导出诊断包查看技术信息。",
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            item("diagnostics-clear") {
                if (snapshot != null && !monitor.checking && rootNeeds.isEmpty()
                    && missingPermissions.isEmpty() && missingSoftware.isEmpty() && !portalNeedsReload && !hasUnknown
                ) {
                    Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                        BasicComponent(
                            title = "没有发现需要处理的项目",
                            summary = "已检查的权限和运行状态正常。",
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            item("diagnostics-actions") {
                SmallTitle("处理入口")
                Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                    SettingsNavigationPreference(
                        title = "问题反馈与诊断",
                        summary = "导出诊断 ZIP、运行触控专项检查或提交问题",
                        icon = MiuixIcons.Report,
                        onClick = onOpenProblemDiagnostics,
                    )
                    SettingsNavigationPreference(
                        title = "打开通用设置",
                        summary = "检查模块作用域、Root 授权或重载运行服务",
                        icon = SettingModule.GENERAL.icon,
                        onClick = onOpenGeneralSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleDiagnosticSection(
    title: String,
    rows: List<Pair<String, String>>,
    onClick: (() -> Unit)? = null,
) {
    if (rows.isEmpty()) return
    SmallTitle(title)
    Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
        rows.forEach { (name, detail) ->
            if (onClick == null) {
                BasicComponent(
                    title = name,
                    summary = detail,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                SettingsNavigationPreference(
                    title = name,
                    summary = detail,
                    icon = MiuixIcons.Info,
                    onClick = onClick,
                )
            }
        }
    }
}

private const val PORTAL_PACKAGE = "com.miui.contentextension"
private const val AICR_PACKAGE = "com.xiaomi.aicr"

private fun packageInstalled(context: Context, packageName: String): Boolean? = try {
    context.packageManager.getApplicationInfo(packageName, 0)
    true
} catch (_: android.content.pm.PackageManager.NameNotFoundException) {
    false
} catch (_: RuntimeException) {
    null
}
