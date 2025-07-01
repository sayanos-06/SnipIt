package com.example.snipit.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.snipit.R
import com.example.snipit.ui.MainActivity
import kotlin.math.absoluteValue

class FloatingIconService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var trashView: View
    private lateinit var trashParams: WindowManager.LayoutParams
    private val iconVisibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.snipit.SHOW_ICON" -> {
                    Handler(Looper.getMainLooper()).post {
                        floatingView.animate().alpha(1f).setDuration(200).withStartAction {
                            floatingView.visibility = View.VISIBLE
                        }.start()
                    }
                }
                "com.example.snipit.HIDE_ICON" -> {
                    Handler(Looper.getMainLooper()).post {
                        floatingView.animate().alpha(0f).setDuration(200).withEndAction {
                            floatingView.visibility = View.GONE
                        }.start()
                    }
                }
            }
        }
    }
    private lateinit var gestureDetector: GestureDetector

    @SuppressLint("InflateParams")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_icon, null)
        trashView = LayoutInflater.from(this).inflate(R.layout.floating_trash_zone, null)
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                openTray()
                return true
            }
        })

        floatingView.isClickable = true
        floatingView.isFocusable = true

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 50
        params.y = 100
        params.width = dpToPx(60)
        params.height = dpToPx(60)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)

        trashParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        windowManager.addView(trashView, trashParams)
        trashView.visibility = View.GONE

        registerReceiver(
            iconVisibilityReceiver,
            IntentFilter().apply {
                addAction("com.example.snipit.SHOW_ICON")
                addAction("com.example.snipit.HIDE_ICON")
            },
            RECEIVER_EXPORTED
        )

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        gestureDetector.setIsLongpressEnabled(false)
        floatingView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)

            when(event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    showTrash()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)

                    val inTrashZone = isInTrashZone(params)
                    trashView.alpha = if (inTrashZone) 1f else 0.6f
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideTrash()
                    if (isInTrashZone(params)) {
                        vibrate()
                        trashView.animate().scaleX(1.2f).scaleY(1.2f).alpha(1f).setDuration(150).start()
                        Toast.makeText(this@FloatingIconService, "Floating icon removed", Toast.LENGTH_SHORT).show()
                        sendBroadcast(Intent("com.example.snipit.FLOATING_ICON_REMOVED"))
                        stopSelf()
                    } else {
                        if (gestureDetector.onTouchEvent(event)) {
                            v.performClick()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (!prefs.getBoolean("snipit_service_enabled", true)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager.removeView(floatingView)
            windowManager.removeView(trashView)
        } catch (e: Exception) {
            Log.e("FloatingIconService", "Error removing views", e)
        }
        unregisterReceiver(iconVisibilityReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showTrash() {
        trashView.visibility = View.VISIBLE
        trashView.animate().alpha(0.6f).setDuration(200).start()
    }

    private fun hideTrash() {
        trashView.animate().alpha(0f).setDuration(200).withEndAction {
            trashView.visibility = View.GONE
        }.start()
    }

    private fun isInTrashZone(params: WindowManager.LayoutParams): Boolean {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels

        val iconCenterX = screenWidth - params.x
        val iconBottomY = params.y + floatingView.height

        val trashXRange = screenWidth / 2 - 150..screenWidth / 2 + 150
        val trashYThreshold = screenHeight - 300

        return iconCenterX in trashXRange && iconBottomY >= trashYThreshold
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun openTray() {
        startService(Intent(this, FloatingTrayService::class.java))
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}