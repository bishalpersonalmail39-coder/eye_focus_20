package com.optirest.eyecare.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
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

/**
 * Full-screen translucent frosted eye-rest overlay displayed directly over all other apps.
 * Matches the clean CareU-style rest screen:
 *  - "Time to Rest Your Eyes"
 *  - "20-20-20 Rule: Look at an object at least 20 feet away and blink slowly."
 *  - Large luminous cyan MM:SS tabular countdown (e.g. 00:20 -> 00:15 -> 00:00)
 *  - Side-by-side "Delay 5 Min" and "Skip Break" buttons
 */
class BreakCountdownOverlayManager(
    private val context: Context,
    private val onSnooze: () -> Unit,
    private val onSkip: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootView: View? = null
    private var timerTextView: TextView? = null
    private var lastRenderedSec: Int = -1
    private var actionHandled: Boolean = false

    val isShowing: Boolean
        get() = rootView != null

    fun show(remainingSec: Int): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            return false
        }
        val safeSec = remainingSec.coerceAtLeast(0)
        return if (Looper.myLooper() == Looper.getMainLooper()) {
            attachOrUpdateInternal(safeSec)
            rootView != null
        } else {
            mainHandler.post { attachOrUpdateInternal(safeSec) }
            true
        }
    }

    fun updateCountdown(remainingSec: Int) {
        val safeSec = remainingSec.coerceAtLeast(0)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            renderSeconds(safeSec)
        } else {
            mainHandler.post { renderSeconds(safeSec) }
        }
    }

    fun dismiss() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal()
        } else {
            mainHandler.post { dismissInternal() }
        }
    }

    private fun attachOrUpdateInternal(remainingSec: Int) {
        if (rootView != null) {
            renderSeconds(remainingSec)
            return
        }

        actionHandled = false
        lastRenderedSec = -1

        val overlay = buildFullScreenOverlay(remainingSec)
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        val useBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            try {
                windowManager.isCrossWindowBlurEnabled
            } catch (_: Exception) {
                false
            }
        if (useBlur) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (useBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    blurBehindRadius = dp(18)
                } catch (_: Exception) {
                }
            }
        }

        overlay.alpha = 0f
        try {
            windowManager.addView(overlay, params)
            rootView = overlay
            renderSeconds(remainingSec)
            overlay.animate()
                .alpha(1f)
                .setDuration(180L)
                .start()
        } catch (_: Exception) {
            rootView = null
            timerTextView = null
        }
    }

    private fun renderSeconds(remainingSec: Int) {
        if (rootView == null || remainingSec == lastRenderedSec) return
        lastRenderedSec = remainingSec
        timerTextView?.text = formatMmSs(remainingSec)
    }

    private fun dismissInternal() {
        val view = rootView ?: return
        rootView = null
        timerTextView = null
        lastRenderedSec = -1
        actionHandled = true

        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .setDuration(140L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    safeRemoveView(view)
                }

                override fun onAnimationCancel(animation: Animator) {
                    safeRemoveView(view)
                }
            })
            .start()
    }

    private fun safeRemoveView(view: View) {
        try {
            if (view.isAttachedToWindow) {
                windowManager.removeViewImmediate(view)
            }
        } catch (_: Exception) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildFullScreenOverlay(initialSeconds: Int): View {
        val backdrop = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#CC141B24"))
            isClickable = true
            isFocusable = false
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val titleView = TextView(context).apply {
            text = "Time to Rest Your Eyes"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(8f, 0f, 2f, Color.parseColor("#66000000"))
        }

        val subtitleView = TextView(context).apply {
            text = "20-20-20 Rule: Look at an object at least 20 feet away and blink slowly."
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        }

        val countdownView = TextView(context).apply {
            text = formatMmSs(initialSeconds)
            setTextColor(Color.parseColor("#00D4FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 58f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            setShadowLayer(16f, 0f, 0f, Color.parseColor("#5500D4FF"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(24)
                bottomMargin = dp(26)
            }
        }
        timerTextView = countdownView

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val delayBtn = TextView(context).apply {
            text = "Delay 5 Min"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            minWidth = dp(136)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#330B1015"))
                cornerRadius = dp(4).toFloat()
                setStroke(dp(1), Color.parseColor("#B8C7D9"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(16)
            }
            setOnClickListener {
                if (actionHandled) return@setOnClickListener
                actionHandled = true
                onSnooze()
            }
        }

        val skipBtn = TextView(context).apply {
            text = "Skip Break"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            minWidth = dp(136)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#00B8F4"))
                cornerRadius = dp(4).toFloat()
            }
            setOnClickListener {
                if (actionHandled) return@setOnClickListener
                actionHandled = true
                onSkip()
            }
        }

        buttonsRow.addView(delayBtn)
        buttonsRow.addView(skipBtn)

        content.addView(titleView)
        content.addView(subtitleView)
        content.addView(countdownView)
        content.addView(buttonsRow)

        backdrop.addView(content)
        return backdrop
    }

    private fun formatMmSs(totalSec: Int): String {
        val safe = totalSec.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return "%02d:%02d".format(mins, secs)
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
