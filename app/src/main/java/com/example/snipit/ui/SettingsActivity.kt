package com.example.snipit.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.snipit.R
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.core.net.toUri
import com.example.snipit.service.FloatingIconService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class SettingsActivity : AppCompatActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    private lateinit var snipitServiceSwitch: MaterialSwitch
    private lateinit var floatingTraySwitch: MaterialSwitch
    private lateinit var tvAppearanceMode: TextView
    private lateinit var appearanceSetting: LinearLayout
    private lateinit var clearClipboardButton: Button
    enum class ThemeMode(val value: Int) {
        LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
        DARK(AppCompatDelegate.MODE_NIGHT_YES),
        SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        companion object {
            fun fromValue(value: Int): ThemeMode {
                return ThemeMode.entries.firstOrNull { it.value == value } ?: SYSTEM
            }
        }
    }

    @SuppressLint("ImplicitSamInstance")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsMain)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        snipitServiceSwitch = findViewById(R.id.snipitServiceSwitch)
        floatingTraySwitch = findViewById(R.id.floatingTraySwitch)
        tvAppearanceMode = findViewById(R.id.tvAppearanceMode)
        appearanceSetting = findViewById(R.id.appearanceSetting)
        clearClipboardButton = findViewById(R.id.btnClearClipBoard)

        settingsViewModel.isFloatingIconVisible.observe(this) { isEnabled ->
            floatingTraySwitch.isChecked = isEnabled
        }

        floatingTraySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    checkAndRequestPermissions()
                } else if (!isServiceRunning(FloatingIconService::class.java)) {
                    startService(Intent(this, FloatingIconService::class.java))
                }
            } else {
                stopService(Intent(this, FloatingIconService::class.java))
            }
            settingsViewModel.setFloatingIconState(isChecked)
        }

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.snipit.FLOATING_ICON_REMOVED") {
                    settingsViewModel.setFloatingIconState(false)
                }
            }
        },
            IntentFilter("com.example.snipit.FLOATING_ICON_REMOVED"),
            RECEIVER_EXPORTED
        )

        settingsViewModel.snipitServiceEnabled.observe (this) {
            snipitServiceSwitch.isChecked = it
        }

        snipitServiceSwitch.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            settingsViewModel.setSnipitServiceState(isChecked, this, snipitServiceSwitch, floatingTraySwitch)
        }

        appearanceSetting.setOnClickListener {
            val sheet = ThemePickerBottomSheet { selectedMode ->
                settingsViewModel.setThemeMode(selectedMode)
            }
            sheet.show(supportFragmentManager, "ThemePicker")
        }

        settingsViewModel.themeMode.observe(this) { mode ->
            tvAppearanceMode.text = when (mode) {
                ThemeMode.LIGHT -> "Light"
                ThemeMode.DARK -> "Dark"
                ThemeMode.SYSTEM -> "Automatic"
            }
        }

        clearClipboardButton.setOnClickListener {
            showClearClipboardConfirmation(findViewById(R.id.settingsMain))
        }

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.materialToolbar)
            ?.setNavigationOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == serviceClass.name
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun showPermissionDialog(
        title: String,
        message: String,
        icon: Drawable? = null,
        neutralButton: String? = null,
        positiveButton: String,
        negativeButton: String,
        onPositive: () -> Unit,
        onNegative: (dialog: DialogInterface) -> Unit
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setIcon(icon)
            .setNeutralButton(neutralButton) { dialog, _ -> onNegative(dialog) }
            .setPositiveButton(positiveButton) { _, _ -> onPositive() }
            .setNegativeButton(negativeButton) { dialog, _ -> onNegative(dialog) }
            .show()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun checkAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                title = "Allow Floating Icon",
                message = "SnipIt uses a floating icon to access clipboard. Please allow overlay permission.",
                icon = getDrawable(R.drawable.layers_24px),
                neutralButton = "Cancel",
                positiveButton = "Allow",
                negativeButton = "Decline",
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
                            floatingTraySwitch.isChecked = false
                        }
                        .setPositiveButton("Yes") { newDialog, _ ->
                            newDialog.dismiss()
                            floatingTraySwitch.isChecked = false
                        }
                        .setNegativeButton("No") { _, _ -> checkAndRequestPermissions() }
                        .show()
                }
            )
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun showClearClipboardConfirmation(view: View) {
        showPermissionDialog(
            title = "Clear Clipboard?",
            message = "This will erase your current clipboard content. Do you want to proceed?",
            icon = getDrawable(R.drawable.error_24px),
            positiveButton = "Clear",
            negativeButton = "Cancel",
            onPositive = { clearClipboard(view) },
            onNegative = { dialog -> dialog.dismiss() }
        )
    }

    private fun clearClipboard(view: View) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()
        Snackbar.make(view, "Clipboard cleared", Snackbar.LENGTH_SHORT).show()
    }
}