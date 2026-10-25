package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.leaf.hyperdragshare.codex.touchprobe.TouchCompatibilityValidator
import com.leaf.hyperdragshare.codex.touchprobe.TouchProbeCollector
import com.leaf.hyperdragshare.codex.touchprobe.TouchProbeExporter
import com.leaf.hyperdragshare.codex.touchprobe.TouchProfileRegistry
import com.leaf.hyperdragshare.codex.touchprobe.TouchReportReader
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.concurrent.thread

internal data class TouchProbeUiState(
    val running: Boolean = false,
    val status: String = "尚未开始",
    val connection: TouchReportReader.ConnectionInfo? = null,
    val snapshot: TouchProbeCollector.Snapshot = TouchProbeCollector().snapshot(),
    val armedGroup: TouchProbeCollector.Group? = null,
    val captureActive: Boolean = false,
    val exportInFlight: Boolean = false,
)

internal fun canExportTouchProbeReport(state: TouchProbeUiState): Boolean =
    !state.captureActive && !state.exportInFlight

internal fun interface TouchProbeReportRunner {
    fun export(
        context: Context,
        connection: TouchReportReader.ConnectionInfo?,
        snapshot: TouchProbeCollector.Snapshot,
        status: String,
        options: TouchProbeExporter.Options,
    ): TouchProbeExporter.Result
}

internal interface TouchProbeReaderHandle {
    fun start()
    fun close()
}

/**
 * Beta-only compatibility probe. It observes the selected fields and never feeds them into
 * the production press/drag pipeline.
 */
class TouchProbeActivity : ComponentActivity() {
    internal var updateChannelForGate: String = BuildConfig.UPDATE_CHANNEL
    private val mainHandler = Handler(Looper.getMainLooper())
    private val collector = TouchProbeCollector()
    private var reader: TouchProbeReaderHandle? = null
    private var readerGeneration = 0L
    private var connection: TouchReportReader.ConnectionInfo? = null
    private var lastUiAt = 0L
    private var exportInFlight = false
    private var uiState by mutableStateOf(TouchProbeUiState())
    private val reportExportSession = DiagnosticExportSession()
    private var pendingReportToken: Long? = null
    private var pendingReport: TouchProbeExporter.Result? = null

    private val textReportSaveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { destination -> onTouchReportSaveResult(destination) }
    private val adapterPackageSaveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { destination -> onTouchReportSaveResult(destination) }

    internal var reportRunner = TouchProbeReportRunner { context, connection, snapshot, status, options ->
        TouchProbeExporter.export(context, connection, snapshot, status, options)
    }
    internal var exportTaskLauncher: (() -> Unit) -> Unit = { task ->
        thread(name = "touch-probe-export", block = task)
    }
    internal var touchReportSavePicker: (TouchProbeExporter.Result) -> Unit = { exported ->
        when (exported.kind) {
            TouchProbeExporter.PackageKind.TEXT_REPORT ->
                textReportSaveLauncher.launch(exported.file.name)
            TouchProbeExporter.PackageKind.ADAPTER_ZIP ->
                adapterPackageSaveLauncher.launch(exported.file.name)
        }
    }

    internal var readerFactory: (TouchReportReader.Listener) -> TouchProbeReaderHandle = { listener ->
        val activeReader = TouchReportReader(listener)
        object : TouchProbeReaderHandle {
            override fun start() = activeReader.start()
            override fun close() = activeReader.close()
        }
    }

    internal val currentUiState: TouchProbeUiState get() = uiState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (updateChannelForGate != "beta") {
            Toast.makeText(this, "触控报告测试仅在内测版开放", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            val dark = isSystemInDarkTheme()
            MiuixTheme(
                controller = remember(dark) {
                    ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
                },
            ) {
                TouchProbeScreen(
                    state = uiState,
                    onBack = { finish() },
                    onStart = ::startProbe,
                    onStop = { stopProbe("已停止采样") },
                    onArm = ::armSample,
                    onDisarm = ::disarmSample,
                    onBeginTouch = ::beginSampleTouch,
                    onEndTouch = ::endSampleTouch,
                    onUndo = ::undoLastSample,
                    onClear = ::clearSamples,
                    onExport = ::exportReport,
                )
            }
        }
    }

    override fun onStop() {
        stopProbe("页面离开，采样已停止")
        super.onStop()
    }

    override fun onDestroy() {
        reportExportSession.cancelActive()
        pendingReportToken = null
        pendingReport = null
        exportInFlight = false
        uiState = uiState.copy(exportInFlight = false)
        stopProbe("页面关闭")
        super.onDestroy()
    }

    internal fun startProbe() {
        stopProbe("重新连接")
        collector.reset()
        connection = null
        openReader()
    }

    private fun openReader() {
        val session = ++readerGeneration
        lastUiAt = 0L
        publish(TouchProbeUiState(running = true, status = "正在查找触控服务与算法库…"))
        lateinit var activeReader: TouchProbeReaderHandle
        val listener = object : TouchReportReader.Listener {
            override fun onReady(value: TouchReportReader.ConnectionInfo) {
                mainHandler.post {
                    if (!isCurrentReader(session, activeReader)) return@post
                    connection = value
                    publish(
                        TouchProbeUiState(
                            running = true,
                            status = when {
                                value.selection.isExactLibrary && value.pointerMatchesProfile ->
                                    "连接成功：算法库精确匹配，等待真实按压验证"
                                value.selection.isCandidate ->
                                    "连接成功：算法库代码特征匹配，布局待验证"
                                !value.selection.isExactLibrary ->
                                    "已连接：未知算法库，仅收集诊断数据"
                                else ->
                                    "连接成功：正在验证动态报告位置"
                            },
                            connection = value,
                            snapshot = collector.snapshot(),
                            armedGroup = uiState.armedGroup,
                            captureActive = uiState.captureActive,
                        ),
                    )
                }
            }

            override fun onFrame(frame: com.leaf.hyperdragshare.codex.touchprobe.TouchReportLayout.Frame) {
                mainHandler.post {
                    if (!isCurrentReader(session, activeReader)) return@post
                    collector.accept(frame)
                    val now = SystemClock.elapsedRealtime()
                    if (!uiState.captureActive && now - lastUiAt >= 80L) {
                        lastUiAt = now
                        publish(
                            TouchProbeUiState(
                                running = true,
                                status = uiState.status,
                                connection = connection,
                                snapshot = collector.snapshot(),
                                armedGroup = uiState.armedGroup,
                                captureActive = uiState.captureActive,
                            ),
                        )
                    }
                }
            }

            override fun onFailure(message: String?, error: Throwable?) {
                mainHandler.post {
                    if (!isCurrentReader(session, activeReader)) return@post
                    reader = null
                    readerGeneration++
                    val detail = message?.takeIf { it.isNotBlank() }
                        ?: error?.message?.takeIf { it.isNotBlank() }
                        ?: "未知错误"
                    collector.cancelActiveGroup("触控读取中断，本次按压未计入")
                    publish(
                        TouchProbeUiState(
                            running = false,
                            status = "触控数据流已中断，采样已停止：$detail",
                            connection = connection,
                            snapshot = collector.snapshot(),
                            armedGroup = null,
                            captureActive = false,
                        ),
                    )
                }
            }
        }
        activeReader = readerFactory(listener)
        reader = activeReader
        activeReader.start()
    }

    internal fun stopProbe(reason: String) {
        readerGeneration++
        val active = reader
        reader = null
        active?.close()
        collector.cancelActiveGroup("采样已停止，本次按压未计入")
        val nextRunning = false
        val current = uiState
        if (current.running || current.snapshot.frames > 0L) {
            publish(
                TouchProbeUiState(
                    running = nextRunning,
                    status = reason,
                    connection = connection,
                    snapshot = collector.snapshot(),
                    armedGroup = null,
                    captureActive = false,
                ),
            )
        }
    }

    private fun isCurrentReader(session: Long, candidate: TouchProbeReaderHandle): Boolean =
        session == readerGeneration && reader === candidate

    private fun armSample(group: TouchProbeCollector.Group) {
        if (!uiState.running || uiState.captureActive) return
        collector.addEvent("continuous capture start group=" + group.name)
        publish(
            uiState.copy(
                status = "正在采集${group.label}：单指按住约 1 秒，抬起后会自动准备下一次",
                armedGroup = group,
            ),
        )
    }

    private fun disarmSample(reason: String) {
        if (uiState.captureActive) return
        if (uiState.armedGroup != null) {
            collector.addEvent("continuous capture stopped: " + reason)
            publish(uiState.copy(status = reason, armedGroup = null))
        }
    }

    private fun beginSampleTouch(group: TouchProbeCollector.Group) {
        if (!uiState.running || uiState.captureActive) return
        val armed = uiState.armedGroup
        if (armed == null) {
            publish(uiState.copy(status = "请先点击“开始采集${group.label}”"))
            return
        }
        if (armed != group) {
            publish(uiState.copy(
                status = "按压方式与当前采集类型不一致，本次已忽略",
                armedGroup = null,
            ))
            collector.addEvent("sample rejected group_mismatch expected=" + armed.name
                    + " actual=" + group.name)
            return
        }
        collector.beginGroup(group)
        publish(uiState.copy(
            status = "正在记录${group.label}，请保持单指按住",
            captureActive = true,
        ))
    }

    private fun endSampleTouch() {
        if (!uiState.captureActive) return
        val sample = collector.endGroup()
        val nextGroup = sample?.group ?: uiState.armedGroup
        val message = when {
            sample == null -> "采样已结束，可以继续按压"
            sample.valid -> "已记录有效${sample.group.label}样本，已自动准备下一次"
            else -> "本次样本无效：${sample.invalidReason}。已自动准备下一次"
        }
        publish(uiState.copy(
            status = message,
            snapshot = collector.snapshot(),
            armedGroup = nextGroup,
            captureActive = false,
        ))
    }

    private fun undoLastSample() {
        if (uiState.captureActive) return
        val removed = collector.undoLastSample()
        publish(uiState.copy(
            status = if (removed == null) "没有可撤销的样本" else "已撤销最近一次${removed.group.label}样本",
            snapshot = collector.snapshot(),
        ))
    }

    private fun clearSamples() {
        if (uiState.captureActive) return
        collector.clearSamples()
        publish(uiState.copy(
            status = if (uiState.running) "样本已清空，仍在读取触控报告" else "样本已清空",
            snapshot = collector.snapshot(),
            armedGroup = null,
            captureActive = false,
        ))
    }

    private fun publish(next: TouchProbeUiState) {
        val apply = { uiState = next.copy(exportInFlight = exportInFlight) }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            apply()
        } else {
            mainHandler.post { apply() }
        }
    }

    internal fun exportReport(options: TouchProbeExporter.Options) {
        if (uiState.captureActive) {
            Toast.makeText(this, "请先抬起手指，再导出触控报告", Toast.LENGTH_SHORT).show()
            return
        }
        if (exportInFlight) return
        val exportToken = reportExportSession.begin() ?: return
        exportInFlight = true
        publish(uiState.copy(exportInFlight = true))
        val appContext = applicationContext
        val connectionSnapshot = connection
        val sampleSnapshot = collector.snapshot()
        val currentStatus = uiState.status
        val launchResult = runCatching {
            exportTaskLauncher {
            val result = runCatching {
                reportRunner.export(
                    appContext,
                    connectionSnapshot,
                    sampleSnapshot,
                    currentStatus,
                    options,
                )
            }
            mainHandler.post {
                if (!reportExportSession.isActive(exportToken) || isDestroyed) return@post
                if (isFinishing || isDestroyed) {
                    finishTouchReportExport(exportToken, uiState.status)
                    result.onSuccess {
                        Toast.makeText(
                            applicationContext,
                            "触控报告已生成，但页面已关闭，未打开保存或分享面板",
                            Toast.LENGTH_LONG,
                        ).show()
                    }.onFailure {
                        Toast.makeText(
                            applicationContext,
                            "触控报告生成失败，请重试",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    return@post
                }
                result.onSuccess { exported ->
                    pendingReportToken = exportToken
                    pendingReport = exported
                    uiState = uiState.copy(status = "请选择保存位置；保存成功后可继续分享")
                    runCatching { touchReportSavePicker(exported) }.onFailure {
                        finishTouchReportExport(exportToken, "无法打开触控报告保存窗口")
                        Toast.makeText(this, "无法打开触控报告保存窗口，请重试", Toast.LENGTH_LONG).show()
                    }
                }.onFailure {
                    finishTouchReportExport(exportToken, "触控报告生成失败")
                    Toast.makeText(
                        this,
                        "触控报告生成失败，请重试",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            }
        }
        launchResult.onFailure {
            finishTouchReportExport(exportToken, "触控报告生成失败")
            Toast.makeText(this, "触控报告生成失败，请重试", Toast.LENGTH_LONG).show()
        }
    }

    internal fun onTouchReportSaveResult(destination: Uri?) {
        val token = pendingReportToken ?: return
        val exported = pendingReport ?: return
        if (!reportExportSession.consumeSavePickerResult(token)) return
        pendingReportToken = null
        if (destination == null) {
            if (reportExportSession.cancelBeforeSavePicker(token)) {
                pendingReport = null
                exportInFlight = false
                uiState = uiState.copy(
                    status = "已取消保存，未分享触控报告",
                    exportInFlight = false,
                )
            }
            return
        }

        uiState = uiState.copy(status = "正在保存触控报告…", exportInFlight = true)
        val appContext = applicationContext
        val appContentResolver = appContext.contentResolver
        lifecycleScope.launch {
            val outcome = reportExportSession.saveAndShare(
                token = token,
                save = {
                    withContext(Dispatchers.IO) {
                        val output = appContentResolver.openOutputStream(destination, "w")
                            ?: throw IOException("无法打开所选位置")
                        output.use { stream ->
                            exported.file.inputStream().use { it.copyTo(stream) }
                        }
                    }
                },
                share = {
                    val uri = FileProvider.getUriForFile(
                        appContext,
                        "${BuildConfig.APPLICATION_ID}.updates",
                        exported.file,
                    )
                    val mimeType = if (exported.kind == TouchProbeExporter.PackageKind.ADAPTER_ZIP) {
                        "application/zip"
                    } else {
                        "text/plain"
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "MMGG 触控测试结果 ${BuildConfig.VERSION_NAME}")
                        clipData = ClipData.newRawUri("MMGG touch probe", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(send, "分享触控测试结果"))
                },
            )
            if (outcome == DiagnosticExportOutcome.STALE) return@launch
            pendingReport = null
            exportInFlight = false
            val attachmentNote = when {
                exported.kind == TouchProbeExporter.PackageKind.TEXT_REPORT ->
                    "精简报告，不含算法库"
                exported.libraryAttached -> "完整适配包，算法库校验通过"
                else -> "适配包未附算法库，报告已记录原因"
            }
            val status = when (outcome) {
                DiagnosticExportOutcome.SAVED_CHOOSER_OPENED ->
                    "触控报告已保存（$attachmentNote），并已打开分享面板"
                DiagnosticExportOutcome.SAVE_FAILED -> "保存触控报告失败，请重试"
                DiagnosticExportOutcome.SHARE_FAILED ->
                    "触控报告已保存（$attachmentNote），但无法打开分享面板"
                DiagnosticExportOutcome.STALE -> return@launch
            }
            uiState = uiState.copy(status = status, exportInFlight = false)
            if (outcome == DiagnosticExportOutcome.SAVE_FAILED ||
                outcome == DiagnosticExportOutcome.SHARE_FAILED) {
                Toast.makeText(this@TouchProbeActivity, status, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun finishTouchReportExport(token: Long, status: String) {
        if (!reportExportSession.finish(token)) return
        pendingReportToken = null
        pendingReport = null
        exportInFlight = false
        uiState = uiState.copy(status = status, exportInFlight = false)
    }
}

@Composable
private fun TouchProbeScreen(
    state: TouchProbeUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onArm: (TouchProbeCollector.Group) -> Unit,
    onDisarm: (String) -> Unit,
    onBeginTouch: (TouchProbeCollector.Group) -> Unit,
    onEndTouch: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onExport: (TouchProbeExporter.Options) -> Unit,
) {
    var group by rememberSaveable { mutableStateOf(TouchProbeCollector.Group.FINGERTIP) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    val compatibility = TouchCompatibilityValidator.evaluate(
        state.connection,
        state.snapshot,
        state.status,
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = "触控报告内测",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = when {
                        state.captureActive -> "请先抬起手指，再导出报告。"
                        state.status.startsWith("正在保存") -> "正在保存触控报告…"
                        state.status.startsWith("请选择保存位置") -> "选择保存位置后，可以继续分享报告。"
                        state.exportInFlight -> "正在生成触控报告…"
                        state.status.startsWith("触控数据流已中断") ->
                            "采样已停止；可以导出当前诊断报告。"
                        else -> "报告含设备信息和触控样本，不含页面文字、截图、剪贴板或应用内容；" +
                            "先保存，之后可分享。"
                    },
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                TextButton(
                    text = "导出触控报告",
                    enabled = canExportTouchProbeReport(state),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { onExport(TouchProbeExporter.Options.smartDefault()) },
                )

            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "测试说明",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "这个测试会检查手机能否分辨“指尖”和“拇指指腹”按压。"
                                + "请按下面的四步操作，两类样本各做 3 次。",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "只读测试：不改系统，不启用新功能，也不影响取词、长按和拖拽。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (state.connection != null && state.running
                                && state.status.startsWith("连接成功")) {
                                "连接成功，可以开始测试"
                            } else {
                                state.status
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        if (state.connection != null) {
                            Text(
                                text = if (compatibility.classification ==
                                    TouchProfileRegistry.LayoutClassification.REFERENCE_STATIC) {
                                    "两类真实单指样本通过后，手机返回的数据可用于后续触发适配。"
                                } else {
                                    "继续完成两类真实单指样本，页面会自动判断是否可用。"
                                },
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        val map = state.connection?.map
                        if (map != null) {
                            Text(
                                text = "${map.properties["ro.product.model"].orEmpty()} · "
                                    + "触控服务 ${map.pid}",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (state.running) {
                                TextButton(
                                    text = "停止检测",
                                    enabled = !state.captureActive,
                                    onClick = onStop,
                                )
                            } else {
                                TextButton(
                                    text = if (state.snapshot.frames == 0L) "开始检测" else "重新检测",
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                    onClick = onStart,
                                )
                            }
                        }
                    }
                }
            }

            if (!state.running || state.connection == null) {
                item {
                    SmallTitle("怎么测试 · 每类各 3 次")
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            TestInstructionStep(
                                number = "1",
                                title = "开始检测",
                                body = "点“开始检测”，等待页面显示“连接成功，可以开始测试”。",
                            )
                            TestInstructionStep(
                                number = "2",
                                title = "采集指尖",
                                body = "选择“指尖”，点“开始采集指尖”，再单指按住采样区约 1 秒后抬起。"
                                    + "抬起后会自动准备下一次，做 3 次即可。",
                            )
                            TestInstructionStep(
                                number = "3",
                                title = "采集拇指指腹",
                                body = "切到“拇指指腹”，点一次“开始采集拇指指腹”，"
                                    + "再单指按住采样区约 1 秒后抬起；同样做 3 次。",
                            )
                            TestInstructionStep(
                                number = "4",
                                title = "检查并导出结果",
                                body = "检查指尖和拇指指腹各 3 个样本后，点“导出触控报告”并选择保存位置。"
                                    + "保存后可继续分享到其他应用；需要适配的机型可选择附带算法库。",
                            )
                        }
                    }
                }
            }

            if (state.running && state.connection != null) {
                item {
                    SmallTitle("选择本次按压方式")
                    TabRow(
                        tabs = TouchProbeCollector.Group.entries.map { it.label },
                        selectedTabIndex = TouchProbeCollector.Group.entries.indexOf(group),
                        onTabSelected = {
                            if (!state.captureActive) {
                                group = TouchProbeCollector.Group.entries[it]
                                if (state.armedGroup != null) {
                                    onDisarm("已暂停采集，请选择新的按压方式")
                                }
                            }
                        },
                        height = 42.dp,
                    )
                    val collectingGroup = state.armedGroup == group
                    TextButton(
                        text = when {
                            state.captureActive -> "正在采集…"
                            collectingGroup -> "暂停采集${group.label}"
                            else -> "开始采集${group.label}"
                        },
                        enabled = state.running && !state.captureActive,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            if (collectingGroup) {
                                onDisarm("已暂停采集${group.label}")
                            } else {
                                onArm(group)
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    val validSampleCount = state.snapshot.gestureSamples.count {
                        it.group == group && it.valid
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(
                                color = if (state.running) {
                                    MiuixTheme.colorScheme.primary.copy(alpha = .22f)
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainer
                                },
                                shape = RoundedCornerShape(28.dp),
                            )
                            .pointerInput(state.running, state.armedGroup, group) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (!state.running || state.armedGroup != group) {
                                        return@awaitEachGesture
                                    }
                                    onBeginTouch(group)
                                    down.consume()
                                    var pressed = true
                                    while (pressed) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null) break
                                        pressed = change.pressed
                                        change.consume()
                                    }
                                    onEndTouch()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = group.label,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "有效样本 $validSampleCount / 3",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (validSampleCount >= 3) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                text = when {
                                    state.captureActive -> "正在记录，请保持单指按住"
                                    collectingGroup && state.snapshot.gestureSamples.none {
                                        it.group == group
                                    } -> "可以开始按压，抬起后会自动准备下一次"
                                    collectingGroup -> "已自动准备下一次，请继续按压"
                                    else -> "点上方“开始采集${group.label}”"
                                },
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }

            if (state.snapshot.gestureSamples.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "本次判断",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            val fingertip = state.snapshot.fingertipPeak()
                            val thumb = state.snapshot.minimumThumbPeak()
                            Text(
                                text = if (fingertip > 0 && thumb > 0) {
                                    if (state.snapshot.hasSeparation()) {
                                        "已观察到可区分数据"
                                    } else {
                                        "本次暂未观察到可区分数据"
                                    }
                                } else {
                                    "两类有效样本不足，还需要继续采集"
                                },
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            if (!state.running || state.connection == null) {
                item {
                SmallTitle("测试结果能说明什么")
                Card(
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "能区分：手机是否返回了不同的指尖、指腹数值。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "能复用：这个规律能否用于后续小米机型的拇指触发适配。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "哪里失败：是没连上、按得太短、多指，还是系统返回的数据不同。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "本次只收集数据，不会启用拇指触发。具体字段和地址会在导出报告中一并提供。",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                }
            }

            if (state.snapshot.gestureSamples.isNotEmpty()) {
                item {
                    SmallTitle("本次样本记录")
                    Card {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.snapshot.gestureSamples.takeLast(5).reversed().forEachIndexed { index, sample ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "${sample.group.label} · ${if (sample.valid) "有效" else "无效"}",
                                            fontWeight = FontWeight.Medium,
                                            color = if (sample.valid) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.onSurface
                                            },
                                        )
                                        Text(
                                            text = if (sample.valid) {
                                                "已获得有效单指读数，已计入本组统计"
                                            } else {
                                                sample.invalidReason
                                            },
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                                if (index != state.snapshot.gestureSamples.takeLast(5).lastIndex) {
                                    Spacer(Modifier.height(2.dp))
                                }
                            }
                            BasicComponent(
                                title = "撤销上次",
                                summary = "移除最近一次样本，并重新计算分组结果",
                                enabled = !state.captureActive,
                                onClick = onUndo,
                            )
                            BasicComponent(
                                title = "清空全部记录",
                                summary = "删除当前读数、样本和判断结果",
                                enabled = !state.captureActive,
                                onClick = { showClearConfirm = true },
                            )
                        }
                    }
                }
            }

            state.connection?.let {
                item {
                    Card(colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                    )) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = "兼容信息",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "参考缓冲区：${referenceBufferLabel(compatibility)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "算法库：${if (compatibility.matchKind ==
                                    TouchProfileRegistry.MatchKind.EXACT_LIBRARY) {
                                    "已识别，可继续验证"
                                } else if (compatibility.matchKind ==
                                    TouchProfileRegistry.MatchKind.CODE_SIGNATURE_CANDIDATE) {
                                    "版本接近，需要真实按压确认"
                                } else {
                                    "尚未适配，仅安全收集诊断信息"
                                }}",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Text(
                                text = "地址变化不代表设备不支持；完成两类样本后会自动复核。"
                                    + "完整地址和哈希只写入导出报告。",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        TouchProbeDialog(
            title = "清空全部记录？",
            summary = "会删除本页累计的触控读数、有效和无效样本，以及当前的指腹判断结果。"
                + "连接状态会保留。已经导出或分享的文件不会删除。",
            onDismissRequest = { showClearConfirm = false },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    modifier = Modifier.weight(1f),
                    onClick = { showClearConfirm = false },
                )
                TextButton(
                    text = "确认清空",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showClearConfirm = false
                        onClear()
                    },
                )
            }
        }
    }


}

@Composable
private fun TouchProbeDialog(
    title: String,
    summary: String,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background.copy(alpha = .45f))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = summary,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    content()
                }
            }
        }
    }
}

@Composable
private fun TestInstructionStep(number: String, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = MiuixTheme.colorScheme.primary.copy(alpha = .16f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

private fun referenceBufferLabel(
    compatibility: TouchCompatibilityValidator.Result,
): String {
    return when (compatibility.classification) {
        TouchProfileRegistry.LayoutClassification.REFERENCE_STATIC -> "静态匹配"
        TouchProfileRegistry.LayoutClassification.DYNAMIC_VALID -> "动态地址"
        TouchProfileRegistry.LayoutClassification.SAME_LIB_UNVERIFIED -> "待验证"
        TouchProfileRegistry.LayoutClassification.RVA_MISMATCH -> "待验证"
        TouchProfileRegistry.LayoutClassification.UNKNOWN_LAYOUT -> "无法验证"
        TouchProfileRegistry.LayoutClassification.UNREADABLE -> "无法验证"
    }
}

