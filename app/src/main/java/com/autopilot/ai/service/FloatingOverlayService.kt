package com.autopilot.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.autopilot.ai.App
import com.autopilot.ai.MainActivity
import com.autopilot.ai.R
import com.autopilot.ai.data.model.ConversationMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Floating overlay widget service modeled after ZeroTap's FloatingWidgetService.
 *
 * Communication via intent actions:
 * - ACTION_SHOW:  Show collapsed bubble
 * - ACTION_HIDE:  Hide bubble + panel
 * - ACTION_STOP:  Stop service entirely
 *
 * The bubble auto-appears when the user leaves the app (triggered by App lifecycle).
 * The bubble auto-hides when the user enters the app.
 */
class FloatingOverlayService : Service() {

    companion object {
        private const val TAG = "FloatingOverlay"
        const val CHANNEL_ID = "autopilot_overlay"
        const val NOTIFICATION_ID = 1002

        const val ACTION_SHOW = "com.autopilot.ai.widget.SHOW"
        const val ACTION_HIDE = "com.autopilot.ai.widget.HIDE"
        const val ACTION_STOP = "com.autopilot.ai.widget.STOP"

        @Volatile
        private var serviceInstance: FloatingOverlayService? = null

        /** Show the floating bubble. Starts the service if not running. */
        fun show(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_SHOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Hide the bubble (but keep service alive). */
        fun hide(context: Context) {
            val svc = serviceInstance ?: return
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }

        /** Convenience: hide using application context from App instance. */
        fun hide() {
            val svc = serviceInstance ?: return
            svc.hideBubbleAndPanel()
        }

        /** Convenience: show using application context from App instance. */
        fun show() {
            val svc = serviceInstance
            if (svc != null) {
                svc.showBubble()
            }
            // If service isn't running, App will call show(context) instead
        }

        /** Stop the entire service. */
        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var messagesJob: Job? = null

    // Views
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    // State
    private var isPanelOpen = false
    private var lastMessageCount = 0

    // Panel children (to update chat)
    private var chatContainer: LinearLayout? = null
    private var chatScrollView: ScrollView? = null
    private var inputField: EditText? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showBubble()
            ACTION_HIDE -> hideBubbleAndPanel()
            ACTION_STOP -> {
                hideBubbleAndPanel()
                stopSelf()
            }
            else -> showBubble()  // Default: show
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesJob?.cancel()
        scope.cancel()
        removeBubble()
        removePanel()
        serviceInstance = null
        Log.i(TAG, "Service destroyed")
    }

    /* ════════════════════════════════════════════════════════
     *  NOTIFICATION
     * ════════════════════════════════════════════════════════ */

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "AutoPilot Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Floating AI assistant overlay"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("AutoPilot AI")
            .setContentText("Floating assistant active")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    /* ════════════════════════════════════════════════════════
     *  BUBBLE VIEW
     * ════════════════════════════════════════════════════════ */

    private fun showBubble() {
        if (bubbleView != null) {
            bubbleView?.visibility = View.VISIBLE
            return
        }
        createBubble()
        startMessageObserver()
    }

    private fun hideBubbleAndPanel() {
        bubbleView?.visibility = View.GONE
        closePanel()
    }

    private fun createBubble() {
        val size = dpToPx(56)

        // Outer circle with border
        val container = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dpToPx(2), Color.parseColor("#00897B"))
            }
            background = bg
            elevation = dpToPx(6).toFloat()
        }

        // Logo icon
        val icon = ImageView(this).apply {
            try {
                val bmp = assets.open("logo.png").use { BitmapFactory.decodeStream(it) }
                setImageBitmap(bmp)
            } catch (_: Exception) {
                // Fallback: colored circle with text
                setImageDrawable(GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#00897B"))
                })
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            val pad = dpToPx(8)
            setPadding(pad, pad, pad, pad)
        }
        container.addView(icon, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(8)
            y = dpToPx(200)
        }

        // Touch: drag + click
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) isDragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    try { windowManager.updateViewLayout(container, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) togglePanel()
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(container, params)
            bubbleView = container
            bubbleParams = params
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add bubble", e)
        }
    }

    private fun removeBubble() {
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null
        bubbleParams = null
    }

    /* ════════════════════════════════════════════════════════
     *  CHAT PANEL
     * ════════════════════════════════════════════════════════ */

    private fun togglePanel() {
        if (isPanelOpen) closePanel() else openPanel()
    }

    private fun openPanel() {
        if (panelView != null) {
            panelView?.visibility = View.VISIBLE
            isPanelOpen = true
            refreshChat()
            return
        }
        createPanel()
        isPanelOpen = true
        refreshChat()
    }

    private fun closePanel() {
        panelView?.visibility = View.GONE
        isPanelOpen = false
    }

    private fun removePanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
        chatContainer = null
        chatScrollView = null
        inputField = null
    }

    private fun createPanel() {
        val dm = resources.displayMetrics
        val panelWidth = (dm.widthPixels * 0.88).toInt()
        val panelHeight = (dm.heightPixels * 0.65).toInt()

        // Main container
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(16).toFloat()
                setColor(Color.parseColor("#1E1E2E"))
                setStroke(dpToPx(1), Color.parseColor("#333355"))
            }
            background = bg
            elevation = dpToPx(10).toFloat()
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            setBackgroundColor(Color.parseColor("#2A2A3E"))
        }

        val title = TextView(this).apply {
            text = "AutoPilot AI"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val clearBtn = TextView(this).apply {
            text = "🗑"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            setOnClickListener {
                val app = applicationContext as App
                app.orchestrator.clearMessages()
                chatContainer?.removeAllViews()
            }
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FF6B6B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(8), 0, dpToPx(4), 0)
            setOnClickListener { closePanel() }
        }

        header.addView(title)
        header.addView(clearBtn)
        header.addView(closeBtn)
        main.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Chat scroll area
        val scroll = ScrollView(this).apply {
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            isFillViewport = true
        }
        val chatLL = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(chatLL, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        main.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        chatContainer = chatLL
        chatScrollView = scroll

        // Input bar
        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            setBackgroundColor(Color.parseColor("#2A2A3E"))
        }

        val input = EditText(this).apply {
            hint = "Tell me what to do..."
            setHintTextColor(Color.parseColor("#666688"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            imeOptions = EditorInfo.IME_ACTION_SEND
            maxLines = 3
            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(20).toFloat()
                setColor(Color.parseColor("#333355"))
            }
            background = bg
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val sendBtn = TextView(this).apply {
            text = "▶"
            setTextColor(Color.parseColor("#00897B"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(dpToPx(10), 0, dpToPx(4), 0)
        }

        val sendAction = {
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                input.setText("")
                hideKeyboard(input)
                val app = applicationContext as App
                scope.launch {
                    app.orchestrator.processCommand(text)
                }
            }
        }

        sendBtn.setOnClickListener { sendAction() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendAction(); true } else false
        }

        inputBar.addView(input)
        inputBar.addView(sendBtn)
        main.addView(inputBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        inputField = input

        // Window params — focusable so keyboard works
        val params = WindowManager.LayoutParams(
            panelWidth, panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(main, params)
            panelView = main
            panelParams = params
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add panel", e)
        }
    }

    /* ════════════════════════════════════════════════════════
     *  CHAT RENDERING
     * ════════════════════════════════════════════════════════ */

    private fun startMessageObserver() {
        messagesJob?.cancel()
        val app = applicationContext as App
        messagesJob = scope.launch {
            app.orchestrator.messages.collectLatest { msgs ->
                if (isPanelOpen && msgs.size != lastMessageCount) {
                    lastMessageCount = msgs.size
                    refreshChat()
                }
            }
        }
    }

    private fun refreshChat() {
        val container = chatContainer ?: return
        val app = applicationContext as App
        val msgs = app.orchestrator.messages.value

        container.removeAllViews()
        for (msg in msgs) {
            container.addView(createMessageBubble(msg))
        }

        chatScrollView?.post {
            chatScrollView?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun createMessageBubble(msg: ConversationMessage): View {
        val isUser = msg.role == "user"
        val isSystem = msg.role == "system"

        val bubble = TextView(this).apply {
            text = if (msg.isSubAgent) "🤖 SubAgent #${msg.subAgentId}: ${msg.content}" else msg.content
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
            maxLines = 50

            val bgColor = when {
                isUser -> Color.parseColor("#00897B")
                isSystem -> Color.parseColor("#333355")
                msg.isSubAgent -> Color.parseColor("#4A3500")
                else -> Color.parseColor("#3A3A5E")
            }
            setTextColor(if (isUser) Color.WHITE else Color.parseColor("#E0E0F0"))

            val bg = GradientDrawable().apply {
                cornerRadius = dpToPx(12).toFloat()
                setColor(bgColor)
            }
            background = bg
        }

        val wrapper = FrameLayout(this).apply {
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isUser) Gravity.END else Gravity.START
                setMargins(dpToPx(4), dpToPx(3), dpToPx(4), dpToPx(3))
                width = dpToPx(260).coerceAtMost(
                    (resources.displayMetrics.widthPixels * 0.7).toInt()
                )
            }
            addView(bubble, lp)
        }
        return wrapper
    }

    /* ════════════════════════════════════════════════════════
     *  UTIL
     * ════════════════════════════════════════════════════════ */

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
