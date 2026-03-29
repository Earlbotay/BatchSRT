package com.autopilot.ai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Core accessibility service modeled after ZeroTap's AccessibilityActionExecutor.
 *
 * Provides:
 * - Gesture dispatch with completion callbacks (tap, long-press, swipe)
 * - Screen content reading with coordinates
 * - Node-based click/long-click by text
 * - Text input into focused fields
 * - Submit/IME action (like pressing Enter/Go)
 * - Global actions (back, home, recents, show-all-apps)
 * - Screenshot capture
 */
class AutoPilotAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoPilotA11y"

        @Volatile
        var instance: AutoPilotAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to events; we drive the screen ourselves.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "Accessibility service destroyed")
    }

    /* ════════════════════════════════════════════════════════
     *  SCREEN READING
     * ════════════════════════════════════════════════════════ */

    /**
     * Walk the accessibility tree and return a text representation including
     * element type, visible text, content description, and center coordinates.
     * Truncated to 4000 chars to stay within prompt limits.
     */
    fun getScreenContent(): String {
        val root = try { rootInActiveWindow } catch (_: Exception) { null }
            ?: return "No window available"
        val sb = StringBuilder()
        try {
            collectNodes(root, sb, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading screen", e)
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
        return sb.toString().take(4000)
    }

    private fun collectNodes(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 20 || sb.length > 4000) return

        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val cls = node.className?.toString()?.substringAfterLast('.').orEmpty()
        val viewId = node.viewIdResourceName?.substringAfterLast('/').orEmpty()

        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val isChecked = node.isChecked
        val isSelected = node.isSelected

        if (text.isNotEmpty() || desc.isNotEmpty() || isClickable || isEditable) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val cx = bounds.centerX()
            val cy = bounds.centerY()
            val label = text.ifEmpty { desc }
            val flags = buildList {
                if (isClickable) add("clickable")
                if (isEditable) add("editable")
                if (isChecked) add("checked")
                if (isSelected) add("selected")
            }.joinToString(",")

            sb.append("[$cls")
            if (viewId.isNotEmpty()) sb.append("/$viewId")
            sb.append("] ")
            if (label.isNotEmpty()) sb.append("\"$label\" ")
            if (flags.isNotEmpty()) sb.append("{$flags} ")
            sb.append("($cx,$cy)")
            sb.append('\n')
        }

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            try {
                collectNodes(child, sb, depth + 1)
            } finally {
                try { child.recycle() } catch (_: Exception) {}
            }
        }
    }

    /* ════════════════════════════════════════════════════════
     *  GESTURES — with completion callbacks (ZeroTap pattern)
     * ════════════════════════════════════════════════════════ */

    /**
     * Dispatch a gesture and suspend until it completes or fails.
     * Returns true if the gesture was dispatched successfully.
     */
    private suspend fun dispatchGestureAndWait(gesture: GestureDescription): Boolean {
        return suspendCancellableCoroutine { cont ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val dispatched = dispatchGesture(gesture, callback, null)
            if (!dispatched && cont.isActive) {
                cont.resume(false)
            }
        }
    }

    /** Tap at (x,y) and wait for the gesture to complete. */
    suspend fun performTap(x: Float, y: Float): Boolean {
        Log.d(TAG, "Tap at ($x, $y)")
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAndWait(gesture)
    }

    /** Long press at (x,y) for [durationMs] and wait for completion. */
    suspend fun performLongPress(x: Float, y: Float, durationMs: Long = 1500): Boolean {
        Log.d(TAG, "Long press at ($x, $y) for ${durationMs}ms")
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAndWait(gesture)
    }

    /** Swipe from (x1,y1) to (x2,y2) over [durationMs] and wait. */
    suspend fun performSwipe(
        x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 500
    ): Boolean {
        Log.d(TAG, "Swipe ($x1,$y1) -> ($x2,$y2)")
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAndWait(gesture)
    }

    /* ════════════════════════════════════════════════════════
     *  TEXT INPUT — like ZeroTap TypeText + SubmitText
     * ════════════════════════════════════════════════════════ */

    /** Set text in the currently focused EditText field. */
    fun setTextInField(text: String): Boolean {
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return false
        try {
            val focused = findFocused(root)
            if (focused != null) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                focused.recycle()
                return ok
            }
            // Fallback: find first editable node
            val editable = findFirstEditable(root)
            if (editable != null) {
                editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val ok = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                editable.recycle()
                return ok
            }
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
        return false
    }

    /** Submit / press Enter / IME action on the focused field (like ZeroTap SubmitText). */
    fun submitText(): Boolean {
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return false
        try {
            val focused = findFocused(root)
            if (focused != null) {
                // Try IME action first, then click
                val ok = focused.performAction(AccessibilityNodeInfo.ACTION_NEXT_HTML_ELEMENT) ||
                    focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                focused.recycle()
                return ok
            }
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
        return false
    }

    private fun findFocused(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            val result = findFocused(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            val result = findFirstEditable(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    /* ════════════════════════════════════════════════════════
     *  NODE-BASED CLICK — like ZeroTap ClickNode / LongClickNode
     * ════════════════════════════════════════════════════════ */

    /** Find a node by text and perform ACTION_CLICK on it. */
    fun findAndClickText(text: String): Boolean {
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return false
        try {
            // Try exact match first, then partial
            val found = findNodeByText(root, text)
            if (found != null) {
                val clickable = findClickableParent(found) ?: found
                val ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clickable !== found) clickable.recycle()
                found.recycle()
                return ok
            }
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
        return false
    }

    /** Find a node by text and perform long click. */
    fun findAndLongClickText(text: String): Boolean {
        val root = try { rootInActiveWindow } catch (_: Exception) { null } ?: return false
        try {
            val found = findNodeByText(root, text)
            if (found != null) {
                val clickable = findClickableParent(found) ?: found
                val ok = clickable.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                if (clickable !== found) clickable.recycle()
                found.recycle()
                return ok
            }
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
        return false
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString().orEmpty()
        val nodeDesc = node.contentDescription?.toString().orEmpty()
        if (nodeText.contains(text, ignoreCase = true) ||
            nodeDesc.contains(text, ignoreCase = true)
        ) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            val result = findNodeByText(child, text)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    /** Walk up the tree to find the nearest clickable ancestor. */
    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node.parent ?: return null
        repeat(10) {
            if (current.isClickable) return current
            val parent = current.parent
            current.recycle()
            current = parent ?: return null
        }
        current.recycle()
        return null
    }

    /* ════════════════════════════════════════════════════════
     *  GLOBAL ACTIONS — like ZeroTap NavigateBack/Home/Recents/ShowAllApps
     * ════════════════════════════════════════════════════════ */

    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /** Show all apps / app drawer (API 31+, fallback to recents). */
    fun showAllApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)
        } else {
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    /* ════════════════════════════════════════════════════════
     *  SCREENSHOT — same approach, with proper error handling
     * ════════════════════════════════════════════════════════ */

    suspend fun takeScreenshotAsync(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        val deferred = CompletableDeferred<Bitmap?>()

        try {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        try {
                            val hwBitmap = Bitmap.wrapHardwareBuffer(
                                result.hardwareBuffer, result.colorSpace
                            )
                            val bitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hwBitmap?.recycle()
                            result.hardwareBuffer.close()
                            deferred.complete(bitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Screenshot processing error", e)
                            deferred.complete(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed: errorCode=$errorCode")
                        deferred.complete(null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot threw", e)
            return null
        }

        return withTimeoutOrNull(5000) { deferred.await() }
    }
}
