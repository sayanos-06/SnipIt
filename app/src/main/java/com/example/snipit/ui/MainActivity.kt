package com.example.snipit.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.adapter.SnippetAdapter
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.example.snipit.service.FloatingIconService
import androidx.core.content.edit
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: SnippetAdapter
    private lateinit var recyclerView: RecyclerView
    private val snippetViewModel: SnippetViewModel by viewModels()
    private lateinit var toolbar: MaterialToolbar
    private lateinit var floatingIconSwitch: MaterialSwitch

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("ImplicitSamInstance")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        checkAndRequestPermissions()

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        sendBroadcast(Intent("com.example.snipit.HIDE_ICON"))
        Log.d("MainActivity", "Floating icon hide broadcast sent")

        floatingIconSwitch = findViewById(R.id.floatingIconSwitch)

        snippetViewModel.isFloatingIconEnabled.observe(this) { isEnabled ->
            floatingIconSwitch.isChecked = isEnabled

            if (isEnabled) {
                if (!isServiceRunning(FloatingIconService::class.java)) {
                    startService(Intent(this, FloatingIconService::class.java))
                }
            } else {
                stopService(Intent(this, FloatingIconService::class.java))
            }
        }

        floatingIconSwitch.setOnCheckedChangeListener { _, isChecked ->
            snippetViewModel.setFloatingIconEnabled(this, isChecked, floatingIconSwitch)
        }

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.snipit.FLOATING_ICON_REMOVED") {
                    snippetViewModel.setFloatingIconEnabled(this@MainActivity,false, floatingIconSwitch)
                }
            }
        },
            IntentFilter("com.example.snipit.FLOATING_ICON_REMOVED"),
            Context.RECEIVER_EXPORTED)

        recyclerView = findViewById(R.id.snippetRecyclerView)
        adapter = SnippetAdapter(listOf(), this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loadSnippets()
    }

    override fun onResume() {
        super.onResume()
        sendBroadcast(Intent("com.example.snipit.HIDE_ICON"))
        Log.d("MainActivity", "Floating icon hide broadcast sent")
        Handler(Looper.getMainLooper()).postDelayed({
            checkClipboardAndSave()
        }, 1000)
    }

    override fun onPause() {
        super.onPause()
        sendBroadcast(Intent("com.example.snipit.SHOW_ICON"))
        Log.d("MainActivity", "Floating icon show broadcast sent")
    }

    private fun loadSnippets() {
        snippetViewModel.snippets.observe(this) { snippetList ->
            adapter.updateList(snippetList)
            Log.d("MainActivity", "Snippets loaded to recycler view")
        }
    }

    private fun checkClipboardAndSave() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData: ClipData? = clipboard.primaryClip
        val copiedText = clipData?.getItemAt(0)?.text?.toString()?.trim()
        Log.d("MainActivity", "Copied text: $copiedText")

        val sharedPrefs = getSharedPreferences("snipit_prefs", Context.MODE_PRIVATE)
        val lastSaved = sharedPrefs.getString("last_clipboard_text", "")

        if (!copiedText.isNullOrEmpty() && copiedText != lastSaved) {
            snippetViewModel.insertOrUpdateSnippet(copiedText)
            sharedPrefs.edit { putString("last_clipboard_text", copiedText) }
        }
    }

    private fun checkAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                title = "Allow Floating Icon",
                message = "SnipIt uses a floating icon to access clipboard. Please allow overlay permission.",
                onPositive = {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:$packageName".toUri())
                    startActivity(intent)
                },
                onNegative = { dialog ->
                    dialog.dismiss()
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Are you sure?")
                        .setMessage("You will have to open the app everytime to confirm the storing of the copied snippet.")
                        .setNeutralButton("Cancel") { newDialog, _ ->
                            newDialog.dismiss()
                            floatingIconSwitch.isEnabled = false
                        }
                        .setPositiveButton("Yes") { newDialog, _ ->
                            newDialog.dismiss()
                            floatingIconSwitch.isEnabled = false
                        }
                        .setNegativeButton("No") { _, _ -> checkAndRequestPermissions() }
                        .show()
                }
            )
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun showPermissionDialog(
        title: String,
        message: String,
        onPositive: () -> Unit,
        onNegative: (dialog: android.content.DialogInterface) -> Unit
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setIcon(getDrawable(R.drawable.layers_24px))
            .setNeutralButton("Cancel") { dialog, _ -> onNegative(dialog) }
            .setPositiveButton("Allow") { _, _ -> onPositive() }
            .setNegativeButton("Decline") { dialog, _ -> onNegative(dialog) }
            .show()
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == serviceClass.name
        }
    }
}