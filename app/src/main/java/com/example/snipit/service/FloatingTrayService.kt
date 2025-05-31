package com.example.snipit.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.adapter.TraySnippetAdapter
import com.example.snipit.data.SnippetDatabase
import com.example.snipit.model.Snippet
import com.example.snipit.ui.ClipboardFetcherActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatingTrayService : Service() {

    private lateinit var trayView: View
    private lateinit var windowManager: WindowManager
    private lateinit var recyclerView: RecyclerView
    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable {
        trayView.animate()
            .alpha(0f)
            .translationY(-50f)
            .setDuration(200)
            .withEndAction { stopSelf() }
            .start()
    }
    private lateinit var trayAdapter: TraySnippetAdapter
    private var snippetLiveData: LiveData<List<Snippet>>? = null
    private val snippetObserver = Observer<List<Snippet>> { latest ->
        trayAdapter.submitSortedList(latest)
    }

    @SuppressLint("InflateParams")
    override fun onCreate() {
        super.onCreate()
        trayView = LayoutInflater.from(this).inflate(R.layout.floating_tray, null)
        trayView.findViewById<ImageView>(R.id.btnTrayClose).setColorFilter(getColor(R.color.md_theme_onPrimaryContainer))
        trayView.findViewById<ImageView>(R.id.btnAdd).setColorFilter(getColor(R.color.md_theme_onPrimaryContainer))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            y = 200
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(trayView, params)

        trayView.alpha = 0f
        trayView.translationY = -50f

        trayView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(100)
            .start()

        recyclerView = trayView.findViewById(R.id.trayRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        trayAdapter = TraySnippetAdapter(applicationContext).apply {
            onPinChange = { snippet, isPinned ->
                CoroutineScope(Dispatchers.IO).launch {
                    val db = SnippetDatabase.getInstance(applicationContext)
                    db.snippetDao().updatePinStatus(snippet.id, isPinned)
                }
            }
        }
        recyclerView.adapter = trayAdapter

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val adapter = recyclerView.adapter as TraySnippetAdapter
                val snippet = adapter.currentList[position]

                CoroutineScope(Dispatchers.IO).launch {
                    SnippetDatabase.getInstance(applicationContext).snippetDao()
                        .deleteSnippet(snippet)
                }

                val updatedList = adapter.currentList.toMutableList().apply { removeAt(position) }
                adapter.submitSortedList(updatedList)

                Toast.makeText(applicationContext, "Deleted", Toast.LENGTH_SHORT).show()
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val background = ContextCompat.getDrawable(this@FloatingTrayService, R.drawable.tray_delete_background)
                val icon = ContextCompat.getDrawable(applicationContext, R.drawable.round_delete_24)!!
                val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = iconTop + icon.intrinsicHeight
                val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 42f
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                val text = "Delete"
                val textWidth = textPaint.measureText(text)
                val textHeight = textPaint.descent() - textPaint.ascent()

                if (dX > 0) {
                    background?.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = iconLeft + icon.intrinsicWidth
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                    val textX = iconRight + 16f
                    val textY = itemView.top + (itemView.height + textHeight) / 2 - textPaint.descent()

                    background?.draw(c)
                    icon.draw(c)
                    c.drawText(text, textX, textY, textPaint)
                } else {
                    background?.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    val iconRight = itemView.right - iconMargin
                    val iconLeft = iconRight - icon.intrinsicWidth
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)

                    val textX = iconLeft - textWidth - 16f
                    val textY = itemView.top + (itemView.height + textHeight) / 2 - textPaint.descent()

                    background?.draw(c)
                    icon.draw(c)
                    c.drawText(text, textX, textY, textPaint)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)

        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 250
            removeDuration = 250
            moveDuration = 200
            changeDuration = 200
        }

        val db = SnippetDatabase.getInstance(applicationContext)
        snippetLiveData = db.snippetDao().getAllSnippets()
        snippetLiveData?.observeForever(snippetObserver)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        trayView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(trayView, params)
                    true
                }
                else -> {
                    v.performClick()
                    false
                }
            }
        }

        trayView.findViewById<View>(R.id.btnTrayClose).setOnClickListener {
            trayView.animate()
                .alpha(0f)
                .translationY(-50f)
                .setDuration(200)
                .withEndAction { stopSelf() }
                .start()
        }

        trayView.findViewById<View>(R.id.btnAdd).setOnClickListener {
            val intent = Intent(this, ClipboardFetcherActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            }
            startActivity(intent)

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        snippetLiveData?.removeObserver(snippetObserver)
        try {
            windowManager.removeView(trayView)
        } catch (e: Exception) {
            Log.e("FloatingTrayService", "View not attached", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}