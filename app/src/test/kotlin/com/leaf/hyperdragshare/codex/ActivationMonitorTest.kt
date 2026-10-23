package com.leaf.hyperdragshare.codex

import android.content.Context
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.ComposeView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(
    manifest = Config.NONE,
    sdk = [33],
    shadows = [ShadowModuleActivation::class],
)
class ActivationMonitorTest {
    private lateinit var activityController: org.robolectric.android.controller.ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var composeView: ComposeView
    private val monitor = AtomicReference<ActivationMonitorState?>()
    private val compositions = AtomicInteger()
    private val probes = ShadowModuleActivation.probes

    @Before
    fun setUp() {
        ShadowModuleActivation.reset()
        activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = activityController.get()
        composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            val state = rememberActivationMonitor(activity)
            SideEffect {
                monitor.set(state)
                compositions.incrementAndGet()
            }
        }
        settleCompose()
    }

    @After
    fun tearDown() {
        ShadowModuleActivation.releaseAll()
        if (::composeView.isInitialized) {
            runOnUi { composeView.disposeComposition() }
        }
        if (::activityController.isInitialized) {
            activityController.pause().stop().destroy()
        }
        ShadowLooper.shadowMainLooper().idle()
        ShadowModuleActivation.reset()
    }

    @Test
    fun staleRootCheckCannotPublishAndPreferenceChangeQueuesAnotherRefresh() {
        val first = awaitProbe()
        awaitState { it.checking }
        val beforeRefresh = compositions.get()

        runOnUi { (monitor.get() ?: error("monitor was not composed")).refresh() }
        first.complete(ModuleActivation.RootAccessOutcome.UNKNOWN)

        val second = awaitProbe()
        awaitState { it.checking }
        assertTrue("stale first result must not publish a snapshot", monitor.get()?.snapshot == null)
        assertTrue("checking stays true until the newest request completes", monitor.get()?.checking == true)
        assertTrue(compositions.get() >= beforeRefresh)

        second.complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { it.snapshot?.root == ActivationEvidence.READY && !it.checking }

        val prefs = activity.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
        runOnUi { prefs.edit().putBoolean("test_refresh_signal", true).commit() }
        val preferenceRefresh = awaitProbe()
        preferenceRefresh.complete(ModuleActivation.RootAccessOutcome.UNKNOWN)
        awaitState { it.snapshot?.root == ActivationEvidence.UNKNOWN && !it.checking }
    }

    @Test
    fun resumeAfterPauseRequestsFreshSnapshot() {
        val initial = awaitProbe()
        initial.complete(ModuleActivation.RootAccessOutcome.UNKNOWN)
        awaitState { it.snapshot != null && !it.checking }

        activityController.pause()
        ShadowLooper.shadowMainLooper().idle()
        activityController.resume()

        val resumed = awaitProbe()
        resumed.complete(ModuleActivation.RootAccessOutcome.GRANTED)
        awaitState { it.snapshot?.root == ActivationEvidence.READY && !it.checking }
    }

    @Test
    fun disposingCompositionUnregistersRefreshSourcesAndIgnoresPendingResult() {
        val inFlight = awaitProbe()
        awaitState { it.checking }
        val compositionCount = compositions.get()

        runOnUi {
            (composeView.parent as ViewGroup).removeView(composeView)
            composeView.disposeComposition()
        }
        inFlight.complete(ModuleActivation.RootAccessOutcome.UNKNOWN)
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(100))
        assertEquals("disposed composition receives no state publication", compositionCount, compositions.get())

        val prefs = activity.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
        runOnUi {
            prefs.edit().putBoolean("test_after_dispose", true).commit()
            activityController.pause()
            activityController.resume()
        }
        assertNull("disposed listeners must not schedule a Root check", probes.poll(250, TimeUnit.MILLISECONDS))
    }

    private fun awaitProbe(): RootProbe {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(16))
            probes.poll(20, TimeUnit.MILLISECONDS)?.let { return it }
        }
        throw AssertionError("ModuleActivation.checkRootAccess was not called")
    }

    private fun awaitState(predicate: (ActivationMonitorState) -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(16))
            val latest = monitor.get()
            if (latest != null && predicate(latest)) return
        }
        throw AssertionError("activation monitor state did not reach the expected value: ${monitor.get()}")
    }

    private fun settleCompose() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline && monitor.get() == null) {
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(16))
        }
        assertNotNull("ComposeView should compose the monitor", monitor.get())
    }

    private fun runOnUi(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            activity.runOnUiThread(action)
            ShadowLooper.shadowMainLooper().idle()
        }
    }
}

internal class RootProbe {
    private val completed = java.util.concurrent.CountDownLatch(1)
    private val outcome = AtomicReference<ModuleActivation.RootAccessOutcome?>()

    internal fun complete(value: ModuleActivation.RootAccessOutcome) {
        outcome.set(value)
        completed.countDown()
    }

    internal fun awaitOutcome(): ModuleActivation.RootAccessOutcome =
        if (completed.await(5, TimeUnit.SECONDS)) {
            outcome.get() ?: ModuleActivation.RootAccessOutcome.UNKNOWN
        } else {
            ModuleActivation.RootAccessOutcome.UNKNOWN
        }
}

@Implements(value = ModuleActivation::class, isInAndroidSdk = false)
internal class ShadowModuleActivation {
    companion object {
        @JvmField
        val probes = LinkedBlockingQueue<RootProbe>()
        private val activeProbes = java.util.concurrent.ConcurrentHashMap.newKeySet<RootProbe>()

        @JvmStatic
        @Implementation
        fun checkRootAccess(): ModuleActivation.RootAccessOutcome {
            val probe = RootProbe()
            activeProbes.add(probe)
            probes.put(probe)
            return try {
                probe.awaitOutcome()
            } finally {
                activeProbes.remove(probe)
            }
        }

        @JvmStatic
        @Implementation
        fun requestPortalRuntimeStart(context: Context): Boolean = false

        @JvmStatic
        @Resetter
        fun reset() {
            releaseAll()
            probes.clear()
        }

        fun releaseAll() {
            activeProbes.forEach { it.complete(ModuleActivation.RootAccessOutcome.UNKNOWN) }
            while (true) {
                val probe = probes.poll() ?: break
                probe.complete(ModuleActivation.RootAccessOutcome.UNKNOWN)
            }
        }
    }
}
