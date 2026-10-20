package com.leaf.hyperdragshare.codex

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Collects contact-size samples in a dedicated region; it does not enable global triggering. */
class ThumbCalibrationActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var touchSource: RootTouchSource
    private var contactRegion = floatArrayOf(0f, 0f, 0f, 0f)
    private var sourceReady by mutableStateOf(false)
    private var sourceHasContactSize by mutableStateOf(false)
    private var sourceFailure by mutableStateOf<String?>(null)
    private var sourceStartedAtMillis = 0L
    private var sampleMessage by mutableStateOf("点“开始校准”，按提示采集指尖和拇指。")
    private var liveSize by mutableStateOf("—")
    private var validSizeSeen by mutableStateOf(false)
    private var frameworkSize by mutableStateOf("尚未触摸")
    private var frameworkPeakMajor = 0f
    private var frameworkPeakMinor = 0f
    private var frameworkPeakToolMajor = 0f
    private var frameworkPeakSize = 0f
    private var calibrationStarted by mutableStateOf(false)
    private val fingertipSamples = mutableStateListOf<Double>()
    private val thumbSamples = mutableStateListOf<Double>()
    private var isCollectingThumb by mutableStateOf(false)
    private var pressInRegion = false
    private var pressLeftRegion = false
    private var peakSize = 0.0
    private var samplingEnabled = false
    private var screenOrigin = intArrayOf(0, 0)

    private val updateSourceStatus = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            sourceReady = touchSource.isReady()
            sourceHasContactSize = touchSource.hasContactSize()
            sourceFailure = touchSource.failureReason() ?: if (
                !sourceReady && SystemClock.elapsedRealtime() - sourceStartedAtMillis >= SOURCE_TIMEOUT_MILLIS
            ) {
                "等待触摸设备读取超时，请检查 Root 授权和模块日志。"
            } else {
                null
            }
            mainHandler.postDelayed(this, STATUS_POLL_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDarkTheme = DragShareSettings.readLocal(this).isDarkTheme(this)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
        window.decorView.getLocationOnScreen(screenOrigin)
        if (ThumbTriggerSettings.read(this).requiresRecalibration) {
            sampleMessage = "接触尺寸采集方式已更新，旧阈值已停用，请重新校准指尖与拇指。"
        }
        touchSource = RootTouchSource(this) { action, x, y, _, major, minor, widthMajor ->
            runOnUiThread { onRawContact(action, x, y, major, minor, widthMajor) }
        }
        setContent {
            val dark = DragShareSettings.readLocal(this).isDarkThemeInComposition()
            MiuixTheme(
                controller = remember(dark) {
                    ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
                },
            ) {
                CalibrationPage(
                    sourceReady = sourceReady,
                    hasContactSize = sourceHasContactSize,
                    validSizeSeen = validSizeSeen,
                    sourceFailure = sourceFailure,
                    statusMessage = sampleMessage,
                    liveSize = liveSize,
                    frameworkSize = frameworkSize,
                    calibrationStarted = calibrationStarted,
                    fingertipCount = fingertipSamples.size,
                    thumbCount = thumbSamples.size,
                    collectingThumb = isCollectingThumb,
                    onRegionBounds = { bounds ->
                        contactRegion = floatArrayOf(
                            bounds.left + screenOrigin[0], bounds.top + screenOrigin[1],
                            bounds.right + screenOrigin[0], bounds.bottom + screenOrigin[1],
                        )
                    },
                    onStart = { startCalibration() },
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        sourceStartedAtMillis = SystemClock.elapsedRealtime()
        touchSource.start()
        mainHandler.post(updateSourceStatus)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(updateSourceStatus)
        discardPress()
        touchSource.stop()
        super.onStop()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            frameworkPeakMajor = 0f
            frameworkPeakMinor = 0f
            frameworkPeakToolMajor = 0f
            frameworkPeakSize = 0f
        }
        frameworkPeakMajor = maxOf(frameworkPeakMajor, event.touchMajor)
        frameworkPeakMinor = maxOf(frameworkPeakMinor, event.touchMinor)
        frameworkPeakToolMajor = maxOf(frameworkPeakToolMajor, event.toolMajor)
        frameworkPeakSize = maxOf(frameworkPeakSize, event.size)
        frameworkSize = "接触长轴 %.1f，短轴 %.1f，工具宽度 %.1f，size %.3f".format(
            frameworkPeakMajor, frameworkPeakMinor, frameworkPeakToolMajor, frameworkPeakSize,
        )
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            DragShareLog.i("DragShare/Thumb", "app MotionEvent contact $frameworkSize")
        }
        return super.dispatchTouchEvent(event)
    }

    private fun startCalibration() {
        resetCalibration()
        if (!sourceHasContactSize) {
            sampleMessage = sourceFailure ?: "当前触摸设备未提供接触面积数据，无法校准。"
            return
        }
        sampleMessage = "请在下方区域用指尖按压并松开，共 3 次。"
        calibrationStarted = true
        samplingEnabled = true
    }

    private fun resetCalibration() {
        fingertipSamples.clear()
        thumbSamples.clear()
        isCollectingThumb = false
        samplingEnabled = false
        sampleMessage = "点“开始校准”，按提示采集指尖和拇指。"
        liveSize = "—"
        discardPress()
    }

    private fun onRawContact(action: Int, x: Float, y: Float, major: Int, minor: Int, widthMajor: Int) {
        val inside = x >= contactRegion[0] && x <= contactRegion[2]
                && y >= contactRegion[1] && y <= contactRegion[3]
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                pressInRegion = inside && samplingEnabled
                pressLeftRegion = false
                peakSize = 0.0
                updatePeak(major, minor, widthMajor)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pressInRegion && !inside) pressLeftRegion = true
                if (pressInRegion && !pressLeftRegion) updatePeak(major, minor, widthMajor)
            }
            MotionEvent.ACTION_UP -> {
                if (pressInRegion && !inside) pressLeftRegion = true
                if (pressInRegion && !pressLeftRegion) {
                    updatePeak(major, minor, widthMajor)
                    commitSample()
                } else if (pressInRegion) {
                    sampleMessage = "按压移出了采样区域，本次未计入；请在区域内完成按压。"
                }
                discardPress()
            }
            MotionEvent.ACTION_CANCEL -> {
                discardPress()
                sampleMessage = "检测到多指或触摸取消，本次未计入；请单指重试。"
            }
        }
    }

    private fun updatePeak(major: Int, minor: Int, widthMajor: Int) {
        val size = touchSource.normalizedContactSize(major, widthMajor)
        if (size > 0.0) validSizeSeen = true
        if (size > peakSize) peakSize = size
        liveSize = "接触长轴 $major，短轴 $minor，工具宽度 $widthMajor；" +
                if (size > 0.0) "归一化尺寸 %.3f".format(size) else "尚无有效尺寸值"
    }

    private fun commitSample() {
        if (peakSize <= 0.0) {
            sampleMessage = "本次没有收到有效接触尺寸，未计入样本。请查看下方 Android 触摸数据。"
            return
        }
        val samples = if (isCollectingThumb) thumbSamples else fingertipSamples
        samples.add(peakSize)
        if (!isCollectingThumb && fingertipSamples.size == SAMPLE_COUNT) {
            isCollectingThumb = true
            sampleMessage = "指尖样本已完成。现在用拇指按压并松开，共 3 次。"
        } else if (isCollectingThumb && thumbSamples.size == SAMPLE_COUNT) {
            val result = ThumbCalibration.calculate(
                fingertipSamples.toDoubleArray(),
                thumbSamples.toDoubleArray(),
            )
            if (!result.calibrated) {
                sampleMessage = "两组接触面积没有可分开的间隔。请重试校准，按压时保持指尖与拇指姿势稳定。"
                samplingEnabled = false
                return
            }
            ThumbTriggerSettings.saveCalibration(this, result.threshold.toFloat())
            samplingEnabled = false
            sampleMessage = "校准成功：归一化阈值 %.3f（指尖最大 %.3f，拇指最小 %.3f）。".format(
                result.threshold, result.fingertipMaximum, result.thumbMinimum,
            )
        } else {
            val kind = if (isCollectingThumb) "拇指" else "指尖"
            sampleMessage = "${kind}样本 ${samples.size}/$SAMPLE_COUNT 已记录，请继续按压采集。"
        }
    }

    private fun discardPress() {
        pressInRegion = false
        pressLeftRegion = false
        peakSize = 0.0
    }

    companion object {
        private const val SAMPLE_COUNT = 3
        private const val STATUS_POLL_MILLIS = 300L
        private const val SOURCE_TIMEOUT_MILLIS = 8_000L
    }
}

@androidx.compose.runtime.Composable
private fun CalibrationPage(
    sourceReady: Boolean,
    hasContactSize: Boolean,
    validSizeSeen: Boolean,
    sourceFailure: String?,
    statusMessage: String,
    liveSize: String,
    frameworkSize: String,
    calibrationStarted: Boolean,
    fingertipCount: Int,
    thumbCount: Int,
    collectingThumb: Boolean,
    onRegionBounds: (androidx.compose.ui.geometry.Rect) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = "拇指触发校准",
                navigationIcon = { TextButton(text = "返回", onClick = onBack) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("用指尖和拇指分别按压三次，帮助区分日常触摸和大面积按压。完成后可在设置中开启触发。")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (sourceReady) "触摸读取：已连接" else "触摸读取：等待 Root 授权")
                    Text(when {
                        validSizeSeen -> "已收到有效接触尺寸"
                        hasContactSize -> "设备已声明尺寸轴，尚未收到有效值"
                        else -> sourceFailure ?: "正在检查接触尺寸"
                    })
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().height(190.dp).onGloballyPositioned {
                    onRegionBounds(it.boundsInWindow())
                },
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("单指采样区域")
                        Spacer(Modifier.height(8.dp))
                        Text("实时接触尺寸：$liveSize")
                        Text("仅在此区域按下并松开才会计入")
                    }
                }
            }
            Text(statusMessage)
            TextButton(
                text = if (calibrationStarted) "重新校准" else "开始校准",
                onClick = onStart,
                enabled = sourceReady && hasContactSize,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("指尖 $fingertipCount/3    拇指 $thumbCount/3${if (collectingThumb) "（采集中）" else ""}")
            Text("Android 触摸数据：$frameworkSize")
        }
    }
}
