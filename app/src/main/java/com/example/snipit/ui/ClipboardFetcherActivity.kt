package com.example.snipit.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.snipit.R
import com.example.snipit.data.SnippetDatabase
import com.example.snipit.model.Snippet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClipboardFetcherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_clipboard_fetcher)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Handler(Looper.getMainLooper()).postDelayed({}, 1000)
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        val text = clipData?.getItemAt(0)?.text?.toString()?.trim()

        if (!text.isNullOrEmpty()) {
            val dao = SnippetDatabase.getInstance(applicationContext).snippetDao()
            CoroutineScope(Dispatchers.IO).launch {
                if (dao.getSnippetByText(text) == null) {
                    dao.insert(Snippet(text = text, timestamp = System.currentTimeMillis()))
                }
            }
            finish()
        } else {
            finish()
        }
    }
}