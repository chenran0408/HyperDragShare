package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** Only the module UID creates the content; SecurityCenter receives a surface, never stash records. */
@android.annotation.SuppressLint("NewApi") // Provider only admits the measured Android 17 host.
internal class StashSurfaceHost private constructor(
    val context: Context,
    token: IBinder,
    private val owner: ISidebarHost,
    private val uid: Int,
    private var width: Int,
    private var height: Int,
) : LifecycleOwner, SavedStateRegistryOwner, OnBackPressedDispatcherOwner, IBinder.DeathRecipient {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = savedState.savedStateRegistry
    override val onBackPressedDispatcher = OnBackPressedDispatcher(::requestClose)
    private val surface = SurfaceControlViewHost(context, context.display, token)
    @Volatile private var ownerAlive = true
    private var mounted = false
    private var released = false
    var dismiss: () -> Unit = {}
    var onBack: (() -> Unit)? = null

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        try { owner.asBinder().linkToDeath(this, 0) }
        catch (error: RemoteException) {
            registry.currentState = Lifecycle.State.DESTROYED
            surface.release()
            throw IllegalStateException("Sidebar host exited before embedding", error)
        }
    }

    fun prepare(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setViewTreeOnBackPressedDispatcherOwner(this)
        // The native sidebar/search hierarchy owns focus and the IME. This embedded surface
        // is permanently passive for focus; touch input still reaches its child views.
    }

    fun show(view: View): Boolean {
        check(!released)
        check(ownerAlive && owner.asBinder().isBinderAlive) { "Sidebar host exited before mounting" }
        if (!mounted) {
            surface.setView(view, attributes())
            mounted = true
            registry.currentState = Lifecycle.State.RESUMED
        }
        return true
    }

    private fun attributes() = SurfaceControlViewHost.LayoutParams(width, height, false).apply {
        setTitle("HyperDragShare sidebar stash")
    }

    fun requestClose() {
        if (!ownerAlive || released) {
            dismiss()
            return
        }
        try { owner.requestClose() }
        catch (error: RemoteException) {
            DragShareLog.w("DragShare/Sidebar", "Sidebar host disappeared", error)
            dismiss()
        }
    }

    fun release() {
        if (released) return
        released = true
        owner.asBinder().unlinkToDeath(this, 0)
        registry.currentState = Lifecycle.State.DESTROYED
        surface.release()
        dismiss = {}
        onBack = null
        DragShareLog.i("DragShare/Sidebar", "stash surface released")
    }

    override fun binderDied() {
        ownerAlive = false
        main.post { dismiss() }
    }

    private val control = object : ISidebarContent.Stub() {
        private fun enforceOwner() {
            if (Binder.getCallingUid() != uid && Binder.getCallingUid() != android.os.Process.myUid()) throw SecurityException("Sidebar surface belongs to another UID")
        }
        override fun resize(w: Int, h: Int) {
            enforceOwner()
            requireSize(context, w, h)
            main.post {
                if (!released) {
                    width = w; height = h
                    if (mounted) surface.relayout(attributes())
                }
            }
        }
        override fun back() {
            enforceOwner()
            main.post {
                if (!released) {
                    val handler = onBack
                    if (handler != null) handler() else onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        override fun close() {
            enforceOwner()
            main.post { dismiss() }
        }
    }

    companion object {
        const val METHOD_OPEN = "open_sidebar_stash_surface"
        private val main = Handler(Looper.getMainLooper())

        private fun requireSize(context: Context, width: Int, height: Int) {
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            require(width in 1..bounds.width() && height in 1..bounds.height()) { "Invalid sidebar surface size" }
        }

        @JvmStatic fun open(app: Context, input: Bundle, uid: Int): Bundle {
            val token = requireNotNull(input.getBinder("host_token")) { "Missing surface host token" }
            val owner = requireNotNull(ISidebarHost.Stub.asInterface(input.getBinder("owner")))
            val display = requireNotNull(app.getSystemService(DisplayManager::class.java)
                .getDisplay(input.getInt("display_id"))) { "Sidebar display no longer exists" }
            val context = app.createDisplayContext(display)
            val width = input.getInt("width"); val height = input.getInt("height")
            requireSize(context, width, height)
            val created = java.util.concurrent.atomic.AtomicReference<StashSurfaceHost>()
            val abandoned = java.util.concurrent.atomic.AtomicBoolean(false)
            val task = FutureTask {
                check(!app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Unlock before opening stash" }
                val host = StashSurfaceHost(context, token, owner, uid, width, height)
                created.set(host)
                try {
                    check(!abandoned.get()) { "Sidebar caller timed out" }
                    StashOverlayPanel.attachSurface(app, host)
                    Bundle().apply {
                        putParcelable("surface", host.surface.surfacePackage)
                        putBinder("content", host.control)
                        putInt("version", BuildConfig.VERSION_CODE)
                    }
                } catch (error: RuntimeException) {
                    host.release()
                    throw error
                }
            }
            // ContentProvider runs off-main. A bounded handoff prevents a stuck host retaining a view.
            if (Looper.myLooper() == Looper.getMainLooper()) task.run() else main.post(task)
            try { return task.get(3, TimeUnit.SECONDS) }
            catch (error: java.util.concurrent.TimeoutException) {
                abandoned.set(true)
                main.post { created.get()?.dismiss?.invoke() }
                throw IllegalStateException("Sidebar surface creation timed out", error)
            } catch (error: InterruptedException) {
                abandoned.set(true)
                main.post { created.get()?.dismiss?.invoke() }
                Thread.currentThread().interrupt()
                throw IllegalStateException("Sidebar surface creation interrupted", error)
            } catch (error: java.util.concurrent.ExecutionException) {
                throw IllegalStateException("Unable to create stash surface", error.cause)
            }
        }
    }
}
