package com.example.geminimultimodalliveapi.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.geminimultimodalliveapi.R
import com.example.geminimultimodalliveapi.utils.dpToPx


class OverlayWidgetController(
    private val context: Context,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onSingleClick()
        fun onDoubleClick()
        fun onLongPress()
        fun onTouchGesture(event: MotionEvent)
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private lateinit var floatingView: FrameLayout
    private lateinit var params: WindowManager.LayoutParams
    private var isBubbleVisible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var singleClickRunnable: Runnable? = null

    init {
        setupFloatingView()
    }

    private fun setupFloatingView() {
        floatingView = FrameLayout(context).apply {
            setBackgroundResource(R.drawable.floating_widget_bg_standby)
        }

        val imageView = ImageView(context).apply {
            setImageResource(R.drawable.baseline_mic_24)
            setColorFilter(android.graphics.Color.WHITE)
        }

        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        floatingView.addView(imageView, layoutParams)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            context.dpToPx(60),
            context.dpToPx(60),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = context.resources.displayMetrics.widthPixels - context.dpToPx(70)
            y = context.resources.displayMetrics.heightPixels / 2
        }

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isMoving = false
            private var isLongPressed = false

            private val longPressRunnable = Runnable {
                isLongPressed = true
                callbacks.onLongPress()
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        isLongPressed = false
                        mainHandler.postDelayed(longPressRunnable, 600)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        mainHandler.removeCallbacks(longPressRunnable)
                        if (isLongPressed) {
                            callbacks.onTouchGesture(event)
                        } else if (!isMoving) {
                            handleWidgetClick()
                        }
                        isLongPressed = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                            if (!isLongPressed) {
                                isMoving = true
                                mainHandler.removeCallbacks(longPressRunnable)
                            }
                        }
                        if (isLongPressed) {
                            callbacks.onTouchGesture(event)
                        } else if (isMoving) {
                            params.x = initialX + dx
                            params.y = initialY + dy
                            try {
                                windowManager.updateViewLayout(floatingView, params)
                            } catch (e: Exception) {
                                Log.e("OverlayWidgetController", "Error updating view layout", e)
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    fun show() {
        if (isBubbleVisible) return
        try {
            windowManager.addView(floatingView, params)
            isBubbleVisible = true
            Log.i("OverlayWidgetController", "Overlay bubble added to window")
        } catch (e: Exception) {
            Log.e("OverlayWidgetController", "Error adding bubble to window", e)
        }
    }

    fun hide() {
        if (!isBubbleVisible) return
        try {
            stopPulseAnimation()
            windowManager.removeView(floatingView)
            isBubbleVisible = false
            Log.i("OverlayWidgetController", "Overlay bubble removed from window")
        } catch (e: Exception) {
            Log.e("OverlayWidgetController", "Error removing bubble from window", e)
        }
    }

    private fun handleWidgetClick() {
        if (singleClickRunnable != null) {
            mainHandler.removeCallbacks(singleClickRunnable!!)
            singleClickRunnable = null
            callbacks.onDoubleClick()
        } else {
            val r = Runnable {
                singleClickRunnable = null
                callbacks.onSingleClick()
            }
            singleClickRunnable = r
            mainHandler.postDelayed(r, 250)
        }
    }

    private var scaleAnimator: android.animation.ValueAnimator? = null

    private fun startPulseAnimation() {
        stopPulseAnimation()
        scaleAnimator = android.animation.ValueAnimator.ofFloat(1.0f, 1.15f).apply {
            duration = 1000
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                if (::floatingView.isInitialized) {
                    floatingView.scaleX = scale
                    floatingView.scaleY = scale
                }
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        scaleAnimator?.cancel()
        scaleAnimator = null
        if (::floatingView.isInitialized) {
            floatingView.scaleX = 1.0f
            floatingView.scaleY = 1.0f
        }
    }

    fun updateWidgetColor(isActive: Boolean) {
        mainHandler.post {
            if (::floatingView.isInitialized && isBubbleVisible) {
                if (isActive) {
                    floatingView.setBackgroundResource(R.drawable.floating_widget_bg_active)
                    startPulseAnimation()
                } else {
                    floatingView.setBackgroundResource(R.drawable.floating_widget_bg_standby)
                    stopPulseAnimation()
                }
            }
        }
    }

    fun isVisible(): Boolean = isBubbleVisible


}
