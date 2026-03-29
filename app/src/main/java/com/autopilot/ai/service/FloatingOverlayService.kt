package com.autopilot.ai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
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

class FloatingOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "autopilot_overlay"
        const val NOTIFICATION_ID = 1002

        @Volatile
        var instance: FloatingOverlayService? = null
            private set

        /** Auto-start the overlay if permission is granted and not already running. */
        fun ensureRunning(context: Context) {
            if (instance != null) return
            if (!Settings.canDrawOverlays(context)) return
            context.startForegroundService(
                Intent(context, FloatingOverlayService::class.java)
            )
        }

        /** Hide bubble + panel — called when AutoPilot app is in foreground. */
        fun hide() {
            instance?.let { svc ->
                svc.bubbleView?.visibility = View.GONE
                if (svc.isPanelShowing) svc.removePanel()
            }
        }

        /** Show bubble — called when AutoPilot app goes to background. */
        fun show() {
            instance?.let { svc ->
                if (!svc.isPanelShowing) {
                    svc.bubbleView?.visibility = View.VISIBLE
                }
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var isPanelShowing = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var panelJobs: Job? = null
    private var messagesContainer: LinearLayout? = null
    private var lastMessageCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoPilot AI")
            .setContentText("Bubble active — tap to chat from anywhere")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        startForeground(
            NOTIFICATION_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        createBubble()

        // If the app is currently in foreground, hide the bubble immediately
        val app = application as? App
        if (app?.isAppInForeground == true) {
            bubbleView?.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        instance = null
        panelJobs?.cancel()
        serviceScope.cancel()
        removePanel()
        removeBubble()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "AutoPilot Overlay", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active when the floating bubble is running" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /* ───── helpers ───── */

    private fun dp(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()

    private fun roundRect(
        color: String, radius: Int, strokeColor: String? = null
    ): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(Color.parseColor(color))
            if (strokeColor != null) setStroke(dp(1), Color.parseColor(strokeColor))
        }

    /* ════════════════════  BUBBLE  ════════════════════ */

    private fun createBubble() {
        val size = dp(56)

        val bubble = FrameLayout(this).apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(2), Color.parseColor("#1A7A6D"))
            }
            elevation = dp(8).toFloat()
        }

        val imageView = ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        bubble.addView(
            imageView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - dp(16)
            y = dp(200)
        }

        /* drag + tap */
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f; var drag = false
        bubble.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params.x; iy = params.y; tx = ev.rawX; ty = ev.rawY; drag = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - tx; val dy = ev.rawY - ty
                    if (dx * dx + dy * dy > 100) drag = true
                    params.x = ix + dx.toInt(); params.y = iy + dy.toInt()
                    try { windowManager.updateViewLayout(bubble, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!drag) togglePanel(); true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        bubbleView = bubble
    }

    private fun removeBubble() {
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null
    }

    /* ════════════════════  CHAT PANEL  ════════════════════ */

    private fun togglePanel() {
        if (isPanelShowing) removePanel() else showPanel()
    }

    @Suppress("SetTextI18n")
    private fun showPanel() {
        if (isPanelShowing) return
        isPanelShowing = true
        bubbleView?.visibility = View.GONE

        val app = application as App
        val orchestrator = app.orchestrator
        val screenWidth = resources.displayMetrics.widthPixels
        val panelWidth = screenWidth - dp(24)

        /* ── card container ── */
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect("#FFFBFE", 20, "#C0C0C0")
            elevation = dp(16).toFloat()
        }

        /* ── title bar ── */
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(8), dp(10))
            setBackgroundColor(Color.parseColor("#F5F0EB"))
        }

        val logoImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        titleBar.addView(
            logoImg,
            LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) }
        )

        titleBar.addView(
            TextView(this).apply {
                text = "AutoPilot AI"
                setTextColor(Color.parseColor("#1C1B1F"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val clearBtn = TextView(this).apply {
            text = "🗑"
            textSize = 18f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { orchestrator.clearMessages() }
        }
        titleBar.addView(clearBtn)

        titleBar.addView(TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.parseColor("#666666"))
            setPadding(dp(8), dp(4), dp(4), dp(4))
            setOnClickListener { removePanel() }
        })
        card.addView(titleBar)

        /* ── divider ── */
        card.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))

        /* ── messages area ── */
        val messagesScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(360)
            )
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isVerticalScrollBarEnabled = true
            setBackgroundColor(Color.parseColor("#FFFAF5"))
        }
        val messagesLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        messagesScroll.addView(messagesLayout)
        card.addView(messagesScroll)
        messagesContainer = messagesLayout

        /* ── status bar ── */
        val statusTv = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#1A7A6D"))
            textSize = 11f
            setPadding(dp(12), dp(4), dp(12), dp(4))
            visibility = View.GONE
        }
        card.addView(statusTv)

        /* ── divider ── */
        card.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))

        /* ── input row ── */
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            setBackgroundColor(Color.parseColor("#FFFBFE"))
        }
        val editText = EditText(this).apply {
            hint = "Tell me what to do..."
            setHintTextColor(Color.parseColor("#999999"))
            setTextColor(Color.parseColor("#1C1B1F"))
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 3
            background = roundRect("#F0F0F0", 24, "#CCCCCC")
            setPadding(dp(16), dp(10), dp(16), dp(10))
            imeOptions = EditorInfo.IME_ACTION_SEND
        }
        inputRow.addView(
            editText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val sendBtn = TextView(this).apply {
            text = "➤"
            textSize = 24f
            setTextColor(Color.parseColor("#1A7A6D"))
            setPadding(dp(12), dp(6), dp(4), dp(6))
        }
        inputRow.addView(sendBtn)
        card.addView(inputRow)

        /* ── observe messages & running state ── */
        lastMessageCount = 0
        panelJobs?.cancel()
        panelJobs = serviceScope.launch {
            launch {
                orchestrator.messages.collectLatest { msgs ->
                    updateMessages(messagesLayout, messagesScroll, msgs)
                }
            }
            launch {
                orchestrator.isRunning.collectLatest { running ->
                    if (running) {
                        statusTv.text = "⏳ Working..."
                        statusTv.visibility = View.VISIBLE
                        editText.isEnabled = false
                        sendBtn.isEnabled = false
                    } else {
                        statusTv.visibility = View.GONE
                        editText.isEnabled = true
                        sendBtn.isEnabled = true
                    }
                }
            }
        }

        /* ── send logic ── */
        val doSend: () -> Unit = send@{
            val text = editText.text.toString().trim()
            if (text.isEmpty()) return@send
            if (orchestrator.isRunning.value) return@send

            editText.setText("")
            serviceScope.launch {
                orchestrator.processCommand(text)
            }
        }

        sendBtn.setOnClickListener { doSend() }
        editText.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) {
                doSend(); true
            } else false
        }

        /* ── add to window ── */
        val wParams = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(60)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        windowManager.addView(card, wParams)
        panelView = card

        editText.requestFocus()
        editText.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }

    /* ── message list management ── */

    private fun updateMessages(
        container: LinearLayout, scroll: ScrollView, messages: List<ConversationMessage>
    ) {
        if (messages.size < lastMessageCount) {
            container.removeAllViews()
            lastMessageCount = 0
        }

        for (i in lastMessageCount until messages.size) {
            container.addView(createMessageView(messages[i]))
        }
        lastMessageCount = messages.size

        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun createMessageView(msg: ConversationMessage): View {
        val isUser = msg.role == "user"
        val isSystem = msg.role == "system"

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, dp(3))
            gravity = if (isUser) Gravity.END else Gravity.START
        }

        if (msg.isSubAgent) {
            wrapper.addView(TextView(this).apply {
                text = "🤖 SubAgent #${msg.subAgentId}"
                textSize = 10f
                setTextColor(Color.parseColor("#FF9800"))
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(4), 0, dp(4), dp(2))
            })
        }

        val bubble = TextView(this).apply {
            text = msg.content
            textSize = 13f
            lineHeight = dp(18)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            maxWidth = (resources.displayMetrics.widthPixels * 0.65).toInt()

            when {
                isUser -> {
                    background = roundRect("#6B4C3B", 16)
                    setTextColor(Color.WHITE)
                }
                isSystem -> {
                    background = roundRect("#F0F0F0", 16)
                    setTextColor(Color.parseColor("#666666"))
                    textSize = 11f
                }
                msg.isSubAgent -> {
                    background = roundRect("#FFF3E0", 16, "#FFE0B2")
                    setTextColor(Color.parseColor("#1C1B1F"))
                }
                else -> {
                    background = roundRect("#F5E6DC", 16)
                    setTextColor(Color.parseColor("#1C1B1F"))
                }
            }
        }

        val bubbleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (isUser) Gravity.END else Gravity.START
        }

        wrapper.addView(bubble, bubbleParams)
        return wrapper
    }

    private fun removePanel() {
        panelJobs?.cancel()
        panelJobs = null
        messagesContainer = null
        lastMessageCount = 0
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
        isPanelShowing = false
        bubbleView?.visibility = View.VISIBLE
    }
}
