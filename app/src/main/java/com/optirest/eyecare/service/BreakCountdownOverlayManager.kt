package com.optirest.eyecare.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.optirest.eyecare.data.EyeGuardDefaults

/**
 * Displays a live, interactive 20-20-20 countdown overlay directly over all other apps
 * using WindowManager TYPE_APPLICATION_OVERLAY. Automatically counts down every second
 * on screen without requiring the user to open OptiRest or manually press Start.
 */
class BreakCountdownOverlayManager(
    private val context: Context,
    private val onFinishEarly: () -> Unit,
    private val onSnooze: () -> Unit,
    private val onSkip: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootView: View? = null
    private var ringView: CountdownRingView? = null
    private var secondsTextView: TextView? = null
    private var subtitleTextView: TextView? = null
    private var expandedCard: LinearLayout? = null
    private var minimizedPill: LinearLayout? = null
    private var pillTimerText: TextView? = null

    private var isMinimized: Boolean = false
    private var lastRemainingSec: Int = EyeGuardDefaults.DEFAULT_BREAK_SECONDS
    private var lastTotalSec: Int = EyeGuardDefaults.DEFAULT_BREAK_SECONDS

    val isShowing: Boolean
        get() = rootView != null

    fun show(remainingSec: Int, totalSec: Int, workMinutes: Int): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            return false
        }
        lastRemainingSec = remainingSec.coerceAtLeast(0)
        lastTotalSec = totalSec.coerceAtLeast(1)

        return if (Looper.myLooper() == Looper.getMainLooper()) {
            attachOrUpdateInternal(lastRemainingSec, lastTotalSec, workMinutes)
            rootView != null
        } else {
            mainHandler.post {
                attachOrUpdateInternal(lastRemainingSec, lastTotalSec, workMinutes)
            }
            true
        }
    }

    fun updateCountdown(remainingSec: Int, totalSec: Int) {
        lastRemainingSec = remainingSec.coerceAtLeast(0)
        lastTotalSec = totalSec.coerceAtLeast(1)
        mainHandler.post {
            if (rootView == null) return@post
            applyCountdownValues(lastRemainingSec, lastTotalSec)
        }
    }

    fun dismiss() {
        mainHandler.post {
            val view = rootView ?: return@post
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
            rootView = null
            ringView = null
            secondsTextView = null
            subtitleTextView = null
            expandedCard = null
            minimizedPill = null
            pillTimerText = null
            isMinimized = false
        }
    }

    private fun attachOrUpdateInternal(remainingSec: Int, totalSec: Int, workMinutes: Int) {
        if (rootView != null) {
            applyCountdownValues(remainingSec, totalSec)
            return
        }

        isMinimized = false
        val container = buildOverlayView(remainingSec, totalSec, workMinutes)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(44)
        }

        try {
            windowManager.addView(container, params)
            rootView = container
            applyCountdownValues(remainingSec, totalSec)
        } catch (_: Exception) {
            rootView = null
        }
    }

    private fun applyCountdownValues(remainingSec: Int, totalSec: Int) {
        val elapsedFraction = 1f - (remainingSec.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f)
        ringView?.setProgress(elapsedFraction)
        secondsTextView?.text = "${remainingSec}s"
        pillTimerText?.text = "Eye Rest · ${remainingSec}s"
    }

    private fun setMinimized(minimized: Boolean) {
        isMinimized = minimized
        expandedCard?.visibility = if (minimized) View.GONE else View.VISIBLE
        minimizedPill?.visibility = if (minimized) View.VISIBLE else View.GONE
    }

    private fun buildOverlayView(remainingSec: Int, totalSec: Int, workMinutes: Int): View {
        val wrapper = FrameLayout(context).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        // --- 1. Expanded Floating Overlay Card (Dark Navy-Slate + Emerald Accent) ---
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            elevation = dp(12).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5131A22"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#2A394A"))
            }
        }

        // Header Row: Title + Minimize button
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val dot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                marginEnd = dp(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#10B981"))
            }
        }

        val headerTitle = TextView(context).apply {
            text = "OptiRest · 20-20-20 Eye Rest"
            setTextColor(Color.parseColor("#10B981"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val minimizeBtn = TextView(context).apply {
            text = "Minimize"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1B2532"))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.parseColor("#263445"))
            }
            setOnClickListener { setMinimized(true) }
        }

        headerRow.addView(dot)
        headerRow.addView(headerTitle)
        headerRow.addView(minimizeBtn)
        card.addView(headerRow)

        // Headline
        val titleView = TextView(context).apply {
            text = "Look 20 feet away"
            setTextColor(Color.parseColor("#F1F5F9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        card.addView(titleView)

        val subtitle = TextView(context).apply {
            text = "${workMinutes}m active screen reached · Blink slowly while countdown finishes"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        subtitleTextView = subtitle
        card.addView(subtitle)

        // Circular Countdown Ring + Giant Tabular Seconds
        val ringContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(124), dp(124)).apply {
                topMargin = dp(14)
                bottomMargin = dp(14)
            }
        }

        val ring = CountdownRingView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        ringView = ring

        val centerStack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val secText = TextView(context).apply {
            text = "${remainingSec}s"
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        secondsTextView = secText

        val subLabel = TextView(context).apply {
            text = "remaining"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
        }

        centerStack.addView(secText)
        centerStack.addView(subLabel)
        ringContainer.addView(ring)
        ringContainer.addView(centerStack)
        card.addView(ringContainer)

        // Action Buttons Row (Finish Early / Snooze 5m / Skip)
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val finishBtn = buildActionButton(
            label = "Done",
            bgColor = "#16A388",
            borderColor = "#16A388",
            textColor = "#FFFFFF",
            onClick = { onFinishEarly() }
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f).apply {
                marginEnd = dp(8)
            }
        }

        val snoozeBtn = buildActionButton(
            label = "Snooze 5m",
            bgColor = "#1B2532",
            borderColor = "#2A394A",
            textColor = "#F1F5F9",
            onClick = { onSnooze() }
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f).apply {
                marginEnd = dp(8)
            }
        }

        val skipBtn = buildActionButton(
            label = "Skip",
            bgColor = "#18212C",
            borderColor = "#2A394A",
            textColor = "#94A3B8",
            onClick = { onSkip() }
        ).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f)
        }

        actionsRow.addView(finishBtn)
        actionsRow.addView(snoozeBtn)
        actionsRow.addView(skipBtn)
        card.addView(actionsRow)

        expandedCard = card

        // --- 2. Compact Minimized Pill (keeps live countdown visible on screen over other apps) ---
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(16), dp(10), dp(16), dp(10))
            elevation = dp(10).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5131A22"))
                cornerRadius = dp(999).toFloat()
                setStroke(dp(1), Color.parseColor("#16A388"))
            }
            setOnClickListener { setMinimized(false) }
        }

        val pillDot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                marginEnd = dp(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#10B981"))
            }
        }

        val pillText = TextView(context).apply {
            text = "Eye Rest · ${remainingSec}s"
            setTextColor(Color.parseColor("#FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        pillTimerText = pillText

        val expandHint = TextView(context).apply {
            text = "  Expand"
            setTextColor(Color.parseColor("#10B981"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        pill.addView(pillDot)
        pill.addView(pillText)
        pill.addView(expandHint)
        minimizedPill = pill

        wrapper.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL
            )
        )
        wrapper.addView(
            pill,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL
            )
        )

        val initialFraction = 1f - (remainingSec.toFloat() / totalSec.toFloat()).coerceIn(0f, 1f)
        ring.setProgress(initialFraction)
        return wrapper
    }

    private fun buildActionButton(
        label: String,
        bgColor: String,
        borderColor: String,
        textColor: String,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.parseColor(textColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(11), dp(10), dp(11))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor(borderColor))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    private class CountdownRingView(context: Context) : View(context) {
        private var progress: Float = 0f

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#1F2A36")
            strokeCap = Paint.Cap.ROUND
        }

        private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#10B981")
            strokeCap = Paint.Cap.ROUND
        }

        private val arcBounds = RectF()

        fun setProgress(value: Float) {
            progress = value.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val stroke = width * 0.085f
            trackPaint.strokeWidth = stroke
            arcPaint.strokeWidth = stroke
            val pad = stroke / 2f + 2f
            arcBounds.set(pad, pad, width - pad, height - pad)
            canvas.drawArc(arcBounds, -90f, 360f, false, trackPaint)
            canvas.drawArc(arcBounds, -90f, 360f * progress, false, arcPaint)
        }
    }
}
