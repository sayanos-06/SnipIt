package com.example.snipit.service

import android.Manifest
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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
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
            Log.d("FloatingIconService", "Broadcast received: ${intent?.action}")
            when (intent?.action) {
                "com.example.snipit.SHOW_ICON" -> {
                    Handler(Looper.getMainLooper()).post {
                        floatingView.animate().alpha(1f).setDuration(200).withStartAction {
                            floatingView.visibility = View.VISIBLE
                        }.start()
                        Log.d("FloatingIconService", "Floating icon shown")
                    }
                }
                "com.example.snipit.HIDE_ICON" -> {
                    Handler(Looper.getMainLooper()).post {
                        floatingView.animate().alpha(0f).setDuration(200).withEndAction {
                            floatingView.visibility = View.GONE
                        }.start()
                        Log.d("FloatingIconService", "Floating icon hidden")
                    }
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_icon, null)

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

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)

        trashView = LayoutInflater.from(this).inflate(R.layout.floating_trash_zone, null)
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
            Context.RECEIVER_EXPORTED
        )
        Log.d("FloatingIconService", "iconVisibilityReceiver registered")

        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY

                        showTrash()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX - (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)

                        val inTrashZone = isInTrashZone(params)
                        trashView.alpha = if (inTrashZone) 1f else 0.6f
                        return true
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
                            val xDiff = (event.rawX - initialTouchX).toInt().absoluteValue
                            val yDiff = (event.rawY - initialTouchY).toInt().absoluteValue
                            Log.d("FloatingIconService", "X Diff: $xDiff, Y Diff: $yDiff")

                            if (xDiff < 10 && yDiff < 10) {
                                val intent =
                                    Intent(this@FloatingIconService, MainActivity::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                startActivity(intent)
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
        Log.d("FloatingIconService", "Floating icon service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("FloatingIconService", "Floating Icon Service Started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        windowManager.removeView(floatingView)
        unregisterReceiver(iconVisibilityReceiver)
        windowManager.removeView(trashView)
        Log.d("FloatingIconService", "Floating Icon Service Destroyed")
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
        // Get screen height
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
}
