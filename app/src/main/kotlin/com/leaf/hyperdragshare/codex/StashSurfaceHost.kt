package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.ClipData
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.net.Uri
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

internal fun sidebarHostSupportsShadowV1(openRequest: Bundle): Boolean =
    openRequest.getBoolean(StashSurfaceHost.SIDEBAR_SHADOW_V1_CAPABILITY, false)

/** Only the module UID creates the content; SecurityCenter receives a surface, never stash records. */
@android.annotation.SuppressLint("NewApi") // Provider only admits the measured Android 17 host.
internal class StashSurfaceHost private constructor(
    val context: Context,
    token: IBinder,
    private val owner: ISidebarHost,
    private val uid: Int,
    private val ownerSupportsShadowV1: Boolean,
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
    private var closeRequestInFlight = false
    private val dragGrantedUris = linkedSetOf<Uri>()
    private var dragGrantPackage: String? = null
    private val dragBridge = EmbeddedStashDragBridge(main,
        startNativeDrag = { clip, flags, shadow, result ->
            if (!surface.transferTouchGestureToHost()) {
                DragShareLog.w("DragShare/Sidebar", "Unable to transfer embedded drag gesture to host window")
                result.onStarted(false)
            } else {
                val callback = object : ISidebarDragCallback.Stub() {
                    override fun onStarted(started: Boolean) = result.onStarted(started)
                    override fun onEnded(accepted: Boolean) = result.onEnded(accepted)
                }
                startSidebarDrag(owner, clip, flags, shadow, callback)
            }
        },
        grant = ::grantDragUris,
        revoke = ::revokeDragUris,
    )
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
        setTitle("MMGG sidebar stash")
    }

    fun requestClose() {
        if (released || closeRequestInFlight) return
        if (dragBridge.deferClose(::requestClose)) return
        if (!ownerAlive || released) {
            dismiss()
            return
        }
        try {
            closeRequestInFlight = true
            owner.requestClose()
        }
        catch (error: RemoteException) {
            closeRequestInFlight = false
            DragShareLog.w("DragShare/Sidebar", "Sidebar host disappeared", error)
            dismiss()
        }
    }

    /** The windowless SCVH session cannot start system drags; ask its verified real-window host. */
    fun startDrag(clip: ClipData, shadow: View.DragShadowBuilder, flags: Int,
                  onStarted: (Boolean) -> Unit, onEnded: (Boolean) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (released || !ownerAlive || !owner.asBinder().isBinderAlive) {
            onStarted(false)
            return
        }
        val shadowSnapshot = if (ownerSupportsShadowV1) {
            runCatching { StashDragShadowSnapshot.capture(shadow) }.getOrNull()
        } else null
        if (ownerSupportsShadowV1 && shadowSnapshot == null) {
            onStarted(false)
            return
        }
        dragBridge.start(clip, flags, shadowSnapshot, onStarted, onEnded)
    }

    private fun grantDragUris(clip: ClipData, flags: Int) {
        val allowedFlags = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
        require(flags and allowedFlags.inv() == 0 && flags and View.DRAG_FLAG_GLOBAL != 0) {
            "Invalid embedded stash drag flags"
        }
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.distinct()
        if (uris.isEmpty()) return
        require(flags and View.DRAG_FLAG_GLOBAL_URI_READ != 0) { "Image drag is missing read permission" }
        val hostPackage = context.packageManager.getPackagesForUid(uid)
            ?.firstOrNull { it == SIDEBAR_PACKAGE }
            ?: throw SecurityException("Sidebar caller UID no longer owns $SIDEBAR_PACKAGE")
        dragGrantPackage = hostPackage
        uris.forEach { uri ->
            require(uri.scheme == "content") { "Only content URIs can be dragged from the stash" }
            context.grantUriPermission(hostPackage, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            dragGrantedUris += uri
        }
    }

    private fun revokeDragUris() {
        val hostPackage = dragGrantPackage
        dragGrantedUris.forEach { uri ->
            try {
                if (hostPackage != null) context.revokeUriPermission(
                    hostPackage, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            catch (error: RuntimeException) {
                DragShareLog.w("DragShare/Sidebar", "Unable to revoke embedded drag grant", error)
            }
        }
        dragGrantedUris.clear()
        dragGrantPackage = null
    }

    fun release() {
        if (released) return
        dragBridge.abort()
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
        main.post {
            dragBridge.abort()
            dismiss()
        }
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
        const val SIDEBAR_SHADOW_V1_CAPABILITY = "supports_shadow_drag_v1"
        private const val SIDEBAR_PACKAGE = "com.miui.securitycenter"
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
                val host = StashSurfaceHost(context, token, owner, uid,
                    sidebarHostSupportsShadowV1(input), width, height)
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

/** Selects an AIDL method only when the host advertised the appended shadow method. */
internal fun startSidebarDrag(
    owner: ISidebarHost,
    clip: ClipData,
    flags: Int,
    shadow: StashDragShadowPayload?,
    callback: ISidebarDragCallback,
) {
    if (shadow == null) owner.startDrag(clip, flags, callback)
    else owner.startDragWithShadow(clip, flags, shadow.png, shadow.bitmapWidthPx, shadow.bitmapHeightPx,
        shadow.shadowWidthPx, shadow.shadowHeightPx, shadow.scaledHotspotX, shadow.scaledHotspotY, callback)
}
