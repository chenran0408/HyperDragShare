package com.leaf.hyperdragshare.codex

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

private const val ACTIVATION_SETTLE_CHECKS = 20

private const val ACTIVATION_SETTLE_INTERVAL_MS = 100L

private const val PORTAL_START_TIMEOUT_MS = 10_000L

internal data class ActivationSnapshot(
    val root: ActivationEvidence,
    val injection: ActivationEvidence,
    val portalService: ActivationEvidence,
    val portalRoot: ActivationEvidence,
    val rootInput: ActivationEvidence,
    val pressReader: ActivationEvidence,
    val overlayPermission: ActivationEvidence,
    val thumbTriggerEnabled: Boolean,
    val copyDirectEnabled: Boolean,
    val rootInputError: String,
    val pressReaderError: String,
) {
    val portalFeature: ActivationState
        get() = activationFeatureState(
            enabled = true,
            dependencies = listOf(injection, portalService, portalRoot, rootInput),
        )

    val pressFeature: ActivationState
        get() = activationFeatureState(
            enabled = true,
            dependencies = listOf(
                injection,
                portalService,
                portalRoot,
                rootInput,
                if (thumbTriggerEnabled) pressReader else ActivationEvidence.NOT_APPLICABLE,
            ),
        )

    val overallState: ActivationState
        get() = activationState(
            listOf(
                root,
                injection,
                portalService,
                portalRoot,
                rootInput,
                if (thumbTriggerEnabled) pressReader else ActivationEvidence.NOT_APPLICABLE,
                overlayPermission,
            ),
        )
}

internal data class ActivationMonitorState(
    val snapshot: ActivationSnapshot?,
    val checking: Boolean,
    val refresh: () -> Unit,
)

private fun activationSnapshot(
    context: Context,
    rootOutcome: ModuleActivation.RootAccessOutcome?,
): ActivationSnapshot = ActivationSnapshot(
    root = rootOutcome?.evidence() ?: ActivationEvidence.CHECKING,
    injection = ModuleActivation.injectionStatus(context).evidence(),
    portalService = ModuleActivation.portalServiceStatus(context).evidence(),
    portalRoot = ModuleActivation.portalRootStatus(context).evidence(),
    rootInput = ModuleActivation.rootInputStatus(context).evidence(),
    pressReader = if (ThumbTriggerSettings.read(context).enabled) {
        ModuleActivation.pressReaderStatus(context).evidence()
    } else {
        ActivationEvidence.NOT_APPLICABLE
    },
    overlayPermission = if (Settings.canDrawOverlays(context)) {
        ActivationEvidence.READY
    } else {
        ActivationEvidence.UNAVAILABLE
    },
    thumbTriggerEnabled = ThumbTriggerSettings.read(context).enabled,
    copyDirectEnabled = IntegrationSettings.isCopyDirectEnabled(context),
    rootInputError = ModuleActivation.rootInputError(context),
    pressReaderError = ModuleActivation.pressReaderError(context),
)

@Composable
internal fun rememberActivationMonitor(context: Context): ActivationMonitorState {
    var snapshot by remember(context) { mutableStateOf<ActivationSnapshot?>(null) }
    var checking by remember(context) { mutableStateOf(false) }
    var requestGeneration by remember(context) { mutableIntStateOf(0) }
    val requests = remember(context) { Channel<Int>(Channel.CONFLATED) }
    val requestRefresh: () -> Unit = remember(requests) {
        {
            requestGeneration += 1
            requests.trySend(requestGeneration)
            Unit
        }
    }
    val lifecycleOwner = LocalView.current.context as? LifecycleOwner

    LaunchedEffect(context, requests) {
        var startupAttempted = false
        requestRefresh()
        for (generation in requests) {
            checking = true
            val rootOutcome = withContext(Dispatchers.IO) { ModuleActivation.checkRootAccess() }
            if (!startupAttempted) {
                startupAttempted = true
                val beforeStart = activationSnapshot(context, rootOutcome)
                if (rootOutcome == ModuleActivation.RootAccessOutcome.GRANTED
                    && (beforeStart.injection != ActivationEvidence.READY
                        || beforeStart.portalService != ActivationEvidence.READY)
                ) {
                    withTimeoutOrNull(PORTAL_START_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) {
                            ModuleActivation.requestPortalRuntimeStart(context)
                        }
                    }
                    for (attempt in 0 until ACTIVATION_SETTLE_CHECKS) {
                        val current = activationSnapshot(context, rootOutcome)
                        if (current.injection == ActivationEvidence.READY
                            && current.portalService == ActivationEvidence.READY
                        ) break
                        delay(ACTIVATION_SETTLE_INTERVAL_MS)
                    }
                }
            }
            if (generation == requestGeneration) {
                snapshot = activationSnapshot(context, rootOutcome)
                checking = false
            }
        }
    }

    DisposableEffect(context, requests) {
        val preferences = context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            requestRefresh()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DisposableEffect(lifecycleOwner, requests) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) requestRefresh()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    return ActivationMonitorState(snapshot, checking, requestRefresh)
}
