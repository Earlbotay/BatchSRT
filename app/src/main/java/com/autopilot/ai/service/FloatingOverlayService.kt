package com.autopilot.ai.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.autopilot.ai.App
import com.autopilot.ai.R
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
    }

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var promptView: View? = null
    private var isPromptShowing = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoPilot AI")
            .setContentText("Floating overlay active — tap bubble to prompt")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
        startForeground(
            NOTIFICATION_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        showBubble()
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        removePrompt()
        removeBubble()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "AutoPilot Overlay", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active when the floating overlay is running" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /* ───── helpers ───── */

    private fun dp(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()

    private fun roundRect(color: String, radius: Int, strokeColor: String? = null): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(Color.parseColor(color))
            if (strokeColor != null) setStroke(dp(1), Color.parseColor(strokeColor))
        }

    /* ════════════════════  BUBBLE  ════════════════════ */

    private fun showBubble() {
        val size = dp(56)

        val bubble = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#6200EE"))
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(8).toFloat()
        }
        bubble.addView(
            TextView(this).apply {
                text = "🤖"
                textSize = 24f
                gravity = Gravity.CENTER
            },
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
                MotionEvent.ACTION_UP -> { if (!drag) togglePrompt(); true }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        bubbleView = bubble
    }

    private fun removeBubble() {
        bubbleView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        bubbleView = null
    }

    /* ════════════════════  PROMPT  ════════════════════ */

    private fun togglePrompt() {
        if (isPromptShowing) removePrompt() else showPrompt()
    }

    @Suppress("SetTextI18n")
    private fun showPrompt() {
        if (isPromptShowing) return
        isPromptShowing = true
        bubbleView?.visibility = View.GONE

        val app = application as App
        val orchestrator = app.orchestrator

        /* ── card container ── */
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundRect("#FFFBFE", 20, "#D0D0D0")
            elevation = dp(16).toFloat()
        }

        /* title row */
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            TextView(this).apply {
                text = "🤖 AutoPilot AI"
                setTextColor(Color.parseColor("#1C1B1F"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        titleRow.addView(TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.parseColor("#666666"))
            setPadding(dp(12), dp(4), dp(4), dp(4))
            setOnClickListener { removePrompt() }
        })
        card.addView(titleRow)

        /* status */
        val statusTv = TextView(this).apply {
            text = "Ready"
            setTextColor(Color.parseColor("#49454F"))
            textSize = 12f
            setPadding(0, dp(2), 0, dp(8))
        }
        card.addView(statusTv)

        /* response area (hidden until first result) */
        val responseScroll = ScrollView(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(160)
            ).apply { bottomMargin = dp(8) }
        }
        val responseTv = TextView(this).apply {
            setTextColor(Color.parseColor("#1C1B1F"))
            textSize = 13f
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundRect("#F3F3F3", 12)
        }
        responseScroll.addView(responseTv)
        card.addView(responseScroll)

        /* input row */
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
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
            setTextColor(Color.parseColor("#6200EE"))
            setPadding(dp(12), dp(6), dp(4), dp(6))
        }
        inputRow.addView(sendBtn)
        card.addView(inputRow)

        /* ── send logic ── */
        val doSend: () -> Unit = send@{
            val text = editText.text.toString().trim()
            if (text.isEmpty()) return@send
            if (orchestrator.isRunning.value) {
                statusTv.text = "⏳ Still running previous task…"
                statusTv.setTextColor(Color.parseColor("#FF9800"))
                return@send
            }

            editText.setText("")
            editText.isEnabled = false
            sendBtn.isEnabled = false
            statusTv.text = "⏳ Working..."
            statusTv.setTextColor(Color.parseColor("#6200EE"))
            responseScroll.visibility = View.VISIBLE
            responseTv.text = ""

            serviceScope.launch {
                var observeJob: Job? = null
                try {
                    observeJob = launch {
                        orchestrator.messages.collectLatest { msgs ->
                            val last = msgs.lastOrNull { it.role == "assistant" }
                            if (last != null) {
                                responseTv.text = last.content
                                responseScroll.post {
                                    responseScroll.fullScroll(View.FOCUS_DOWN)
                                }
                            }
                        }
                    }

                    orchestrator.processCommand(text)
                    observeJob.cancel()

                    val finalMsg =
                        orchestrator.messages.value.lastOrNull { it.role == "assistant" }
                    responseTv.text = finalMsg?.content ?: "Done"
                    statusTv.text = "✅ Done"
                    statusTv.setTextColor(Color.parseColor("#4CAF50"))
                } catch (e: Exception) {
                    observeJob?.cancel()
                    responseTv.text = "Error: ${e.message}"
                    statusTv.text = "❌ Error"
                    statusTv.setTextColor(Color.parseColor("#F44336"))
                } finally {
                    editText.isEnabled = true
                    sendBtn.isEnabled = true
                }
            }
        }

        sendBtn.setOnClickListener { doSend() }
        editText.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) { doSend(); true } else false
        }

        /* ── add to window ── */
        val wParams = WindowManager.LayoutParams(
            dp(320),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0, // focusable for keyboard
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(100)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }

        windowManager.addView(card, wParams)
        promptView = card

        editText.requestFocus()
        editText.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }, 250)
    }

    private fun removePrompt() {
        promptView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        promptView = null
        isPromptShowing = false
        bubbleView?.visibility = View.VISIBLE
    }
}
