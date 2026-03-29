package com.autopilot.ai.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "Accessibility service destroyed")
    }

    fun getScreenContent(): String {
        val root = rootInActiveWindow ?: return "No window available"
        val builder = StringBuilder()
        extractText(root, builder, 0)
        root.recycle()
        return builder.toString().take(3000)
    }

    private fun extractText(node: AccessibilityNodeInfo, builder: StringBuilder, depth: Int) {
        if (depth > 15) return
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val className = node.className?.toString()?.substringAfterLast('.') ?: ""

        if (text.isNotEmpty() || desc.isNotEmpty()) {
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            val label = text.ifEmpty { desc }
            builder.append("[$className] \"$label\" (${bounds.centerX()},${bounds.centerY()})\n")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            extractText(child, builder, depth + 1)
            child.recycle()
        }
    }

    fun performTap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
        Log.d(TAG, "Tap at ($x, $y)")
    }

    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            .build()
        dispatchGesture(gesture, null, null)
        Log.d(TAG, "Swipe ($x1,$y1) -> ($x2,$y2)")
    }

    fun setTextInField(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = findFocusedEditText(root)
        if (focused != null) {
            val arguments = android.os.Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text
            )
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focused.recycle()
        }
        root.recycle()
    }

    private fun findFocusedEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.className?.toString()?.contains("EditText") == true) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFocusedEditText(child)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    fun findAndClickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val found = findNodeByText(root, text)
        val success = found?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        found?.recycle()
        root.recycle()
        return success
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString() ?: ""
        val nodeDesc = node.contentDescription?.toString() ?: ""
        if (nodeText.contains(text, ignoreCase = true) || nodeDesc.contains(text, ignoreCase = true)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, text)
            child.recycle()
            if (result != null) return result
        }
        return null
    }

    fun performLongPress(x: Float, y: Float, durationMs: Long = 1500) {
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
        Log.d(TAG, "Long press at ($x, $y) for ${durationMs}ms")
    }

    fun performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    suspend fun takeScreenshotAsync(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        val deferred = CompletableDeferred<Bitmap?>()

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
                    Log.e(TAG, "Screenshot failed: $errorCode")
                    deferred.complete(null)
                }
            }
        )

        return withTimeoutOrNull(5000) { deferred.await() }
    }
}
