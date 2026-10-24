package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val rows = listOf(
        DiagnosticRow("Root 授权", snapshot?.root ?: ActivationEvidence.CHECKING,
            when (snapshot?.root ?: ActivationEvidence.CHECKING) {
                ActivationEvidence.READY -> "Root 命令已成功返回"
                ActivationEvidence.UNAVAILABLE -> "Root 管理器拒绝或未授予本次请求；仅影响依赖 Root 的功能"
                ActivationEvidence.UNKNOWN -> "检查超时或无响应，无法判断授权结果"
                ActivationEvidence.NOT_APPLICABLE -> "当前功能不需要 Root"
                ActivationEvidence.CHECKING -> "正在检查"
            }, "按压取词、部分触控输入和 Portal 运行通道"),
        DiagnosticRow("传送门模块", snapshot?.injection ?: ActivationEvidence.CHECKING,
            evidenceDescription(snapshot?.injection ?: ActivationEvidence.CHECKING,
                "收到当前版本的存活模块会话", "检测到旧版本会话", "未收到当前版本的存活会话"),
            "传送门内容捕获和相关拖拽功能"),
        DiagnosticRow("传送门服务", snapshot?.portalService ?: ActivationEvidence.CHECKING,
            evidenceDescription(snapshot?.portalService ?: ActivationEvidence.CHECKING,
                "服务报告已就绪", "服务报告未就绪", "尚无可确认的服务状态"),
            "系统传送门入口和内容捕获"),
        DiagnosticRow("传送门 Root 通道", snapshot?.portalRoot ?: ActivationEvidence.CHECKING,
            evidenceDescription(snapshot?.portalRoot ?: ActivationEvidence.CHECKING,
                "传送门进程的 Root 检查成功", "传送门进程的 Root 检查被拒绝",
                "尚无结果或检查超时；不会按未授权处理"),
            "依赖传送门进程 Root 能力的触控和识别路径"),
        DiagnosticRow("Root 触控输入", snapshot?.rootInput ?: ActivationEvidence.CHECKING,
            snapshot?.rootInputError?.takeIf { it.isNotBlank() }
                ?: evidenceDescription(snapshot?.rootInput ?: ActivationEvidence.CHECKING,
                    "触控输入已就绪", "触控输入明确报告不可用", "没有可确认的输入状态"),
            "按压取词及读取 Root 触控流的操作"),
        DiagnosticRow("按压取词组件", snapshot?.pressReader ?: ActivationEvidence.CHECKING,
            if (snapshot?.thumbTriggerEnabled == false) "拇指触发未启用，此项不适用"
            else snapshot?.pressReaderError?.takeIf { it.isNotBlank() }
                ?: evidenceDescription(snapshot?.pressReader ?: ActivationEvidence.CHECKING,
                    "按压取词已就绪", "组件明确报告不可用", "未收到就绪或错误报告"),
            "拇指触发开启时的按压识别"),
        DiagnosticRow("应用悬浮窗权限", snapshot?.overlayPermission ?: ActivationEvidence.CHECKING,
            evidenceDescription(snapshot?.overlayPermission ?: ActivationEvidence.CHECKING,
                "已允许应用显示悬浮窗", "尚未允许显示悬浮窗", "权限状态暂时无法读取"),
            "大爆炸、点按识屏和部分应用侧悬浮工具"),
        DiagnosticRow("小米澎湃 AI 引擎", ActivationEvidence.UNKNOWN,
            "按需调用 OCR、实体识别或条码服务；当前没有通用心跳检查，需实际使用对应识别功能确认",
            "仅使用 AI 识别的功能；不会影响普通拖拽或手势"),
        DiagnosticRow("系统手势作用域", ActivationEvidence.UNKNOWN,
            "当前应用无法无打扰地验证系统进程中的模块钩子，请通过实际手势或模块管理器确认",
            "底角、蜂巢和小窗手势"),
        DiagnosticRow("复制直达", if (snapshot?.copyDirectEnabled == true)
            ActivationEvidence.UNKNOWN else ActivationEvidence.NOT_APPLICABLE,
            if (snapshot?.copyDirectEnabled == true)
                "功能已开启，但 AICR 卡片服务按需调用，没有持续运行状态报告"
            else "功能未启用",
            "仅复制直达卡片；其他复制与分享功能独立运行"),
    )
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
                        title = when (snapshot?.overallState ?: ActivationState.CHECKING) {
                            ActivationState.CHECKING -> "正在检查"
                            ActivationState.NORMAL -> "已检查项目正常"
                            ActivationState.NEEDS_ACTION -> "部分项目需要处理"
                            ActivationState.UNKNOWN -> "暂时无法确认部分项目"
                            ActivationState.NOT_APPLICABLE -> "当前没有适用项目"
                        },
                        summary = if (monitor.checking) "正在更新状态，当前结果保留显示" else
                            "状态依据运行时报告和实际权限查询；未收到响应会标为待确认。",
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
            item("diagnostics-components-title") {
                SmallTitle("运行环节")
            }
            items(rows, key = { it.title }) { row -> DiagnosticEvidenceCard(row) }
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
                    SettingsNavigationPreference(
                        title = "打开悬浮窗权限",
                        summary = "仅适用于依赖应用悬浮窗的工具",
                        icon = MiuixIcons.Info,
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                .setData(android.net.Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                    )
                }
            }
        }
    }
}

private data class DiagnosticRow(
    val title: String,
    val evidence: ActivationEvidence,
    val detail: String,
    val impact: String,
)

private fun evidenceDescription(
    evidence: ActivationEvidence,
    ready: String,
    unavailable: String,
    unknown: String,
): String = when (evidence) {
    ActivationEvidence.CHECKING -> "正在检查"
    ActivationEvidence.READY -> ready
    ActivationEvidence.UNAVAILABLE -> unavailable
    ActivationEvidence.UNKNOWN -> unknown
    ActivationEvidence.NOT_APPLICABLE -> "当前功能不适用"
}

@Composable
private fun DiagnosticEvidenceCard(row: DiagnosticRow) {
    val status = when (row.evidence) {
        ActivationEvidence.CHECKING -> "检查中"
        ActivationEvidence.READY -> "正常"
        ActivationEvidence.UNAVAILABLE -> "需要处理"
        ActivationEvidence.UNKNOWN -> "待确认"
        ActivationEvidence.NOT_APPLICABLE -> "不适用"
    }
    Card(Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
        BasicComponent(
            title = "${row.title} · $status",
            summary = "${row.detail}\n影响功能：${row.impact}",
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
