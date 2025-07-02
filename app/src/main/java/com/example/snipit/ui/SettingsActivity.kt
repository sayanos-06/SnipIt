package com.example.snipit.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import com.example.snipit.R
import com.example.snipit.service.FloatingIconService
import com.example.snipit.utils.DriveServiceHolder
import com.example.snipit.utils.ExportHelper
import com.example.snipit.utils.SyncScheduler
import com.example.snipit.utils.TimeUtils
import com.example.snipit.viewModels.SettingsViewModel
import com.example.snipit.viewModels.SnippetViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    internal val snippetViewModel: SnippetViewModel by viewModels()
    private lateinit var snipitServiceSwitch: MaterialSwitch
    private lateinit var floatingTraySwitch: MaterialSwitch
    private lateinit var tvCloudSync: TextView
    private lateinit var cloudSyncSetting: LinearLayout
    @Suppress("DEPRECATION")
    private lateinit var googleSignInClient: GoogleSignInClient
    private var driveService: Drive? = null
    private lateinit var tvAppearanceMode: TextView
    private lateinit var appearanceSetting: LinearLayout
    private lateinit var cleanupSetting: LinearLayout
    private lateinit var clearClipboardButton: Button
    private lateinit var lastSyncedView: TextView
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var btnBackupNow: Button
    private lateinit var suggestedActionsSwitch: MaterialSwitch
    private lateinit var tvScheduledTime: TextView
    private lateinit var scheduledTimeSetting: LinearLayout
    enum class CloudSyncMode(val value: Int) {
        OFF(0),
        GOOGLE_DRIVE(1);

        companion object {
            fun fromValue(value: Int): CloudSyncMode {
                return CloudSyncMode.entries.firstOrNull { it.value == value } ?: OFF
            }
        }
    }
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

    companion object {
        const val RC_SIGN_IN = 4010
    }

    @SuppressLint("ImplicitSamInstance", "SetTextI18n", "UseCompatLoadingForDrawables")
    @Suppress("DEPRECATION")
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsMain)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        window.statusBarColor = getColor(R.color.md_theme_primary)
        val isDarkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        window.insetsController?.setSystemBarsAppearance(
            if (isDarkTheme) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }

        snipitServiceSwitch = findViewById(R.id.snipitServiceSwitch)
        floatingTraySwitch = findViewById(R.id.floatingTraySwitch)
        tvCloudSync = findViewById(R.id.tvCloudSync)
        cloudSyncSetting = findViewById(R.id.cloudSyncSetting)
        tvAppearanceMode = findViewById(R.id.tvAppearanceMode)
        appearanceSetting = findViewById(R.id.appearanceSetting)
        cleanupSetting = findViewById(R.id.autoCleanupSetting)
        clearClipboardButton = findViewById(R.id.btnClearClipBoard)
        lastSyncedView = findViewById(R.id.lastSyncedText)
        btnBackupNow = findViewById(R.id.btnBackupNow)
        suggestedActionsSwitch = findViewById(R.id.suggestedActionsSwitch)
        progressIndicator = findViewById(R.id.progressIndicator)
        tvScheduledTime = findViewById(R.id.tvScheduledTime)
        scheduledTimeSetting = findViewById(R.id.scheduledTimeSetting)

        val scheduledTime = settingsViewModel.getScheduledBackupTime()
        val formattedTime = String.format(
            Locale.getDefault(),
            "%02d:%02d %s",
            if (scheduledTime.first % 12 == 0) 12 else scheduledTime.first % 12,
            scheduledTime.second,
            if (scheduledTime.first < 12) "AM" else "PM"
        )
        tvScheduledTime.text = formattedTime

        var hasUserInteracted = false
        val prefs = getSharedPreferences("sync_prefs", MODE_PRIVATE)
        val last = prefs.getLong("last_synced_time", 0L)
        lastSyncedView.text = "Last synced: ${TimeUtils.formatSyncTime(last)}"

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

        settingsViewModel.suggestedActionsEnabled.observe(this) { enabled ->
            suggestedActionsSwitch.isChecked = enabled
        }

        suggestedActionsSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsViewModel.setSuggestedActionsEnabled(isChecked)
            if (!hasUserInteracted) return@setOnCheckedChangeListener
            Snackbar.make(findViewById(R.id.settingsMain), "Please restart the app to apply the changes", Snackbar.LENGTH_SHORT)
                .setAction("Restart") {
                    finishAffinity()
                    val restartIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    }
                    restartIntent?.let { startActivity(it) }
                }
                .show()
        }

        suggestedActionsSwitch.post {
            hasUserInteracted = true
        }

        cloudSyncSetting.setOnClickListener {
            CloudSyncBottomSheet { selected ->
                when (selected) {
                    CloudSyncMode.OFF -> {
                        showPermissionDialog(
                            title = "Are you sure?",
                            message = "Cloud sync will be disabled and you will be signed out of your account. Do you want to proceed?",
                            icon = getDrawable(R.drawable.error_24px),
                            positiveButton = "Yes",
                            negativeButton = "No",
                            onPositive = {
                                driveService = null
                                googleSignInClient = GoogleSignIn.getClient(
                                    this,
                                    GoogleSignInOptions.DEFAULT_SIGN_IN
                                )
                                googleSignInClient.signOut()
                                Snackbar.make(
                                    findViewById(R.id.settingsMain),
                                    "Cloud sync disabled",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                                settingsViewModel.setCloudSyncMode(selected)
                            },
                            onNegative = { dialog ->
                                dialog.dismiss()
                            }
                        )
                    }

                    CloudSyncMode.GOOGLE_DRIVE -> {
                        setupGoogleSignIn()
                        signInToGoogle()
                    }
                }
            }.show(supportFragmentManager, "cloud_sync_picker")
        }

        settingsViewModel.cloudSyncMode.observe(this) {
            tvCloudSync.text = when (it) {
                CloudSyncMode.OFF -> "OFF"
                CloudSyncMode.GOOGLE_DRIVE -> "GOOGLE DRIVE"
            }
            btnBackupNow.visibility = when (it) {
                CloudSyncMode.OFF -> View.GONE
                CloudSyncMode.GOOGLE_DRIVE -> View.VISIBLE
            }
        }

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && DriveServiceHolder.driveServiceObject == null) {
            btnBackupNow.isEnabled = false
            buildDriveService(account, true)
            btnBackupNow.isEnabled = true
            ensureDriveBackupScheduled(this)
        }

        btnBackupNow.setOnClickListener {
            progressIndicator.isIndeterminate = true
            progressIndicator.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    ExportHelper(this@SettingsActivity).exportSnippetsToDrive(DriveServiceHolder.driveServiceObject!!, true)
                    withContext(Dispatchers.Main) {
                        progressIndicator.isIndeterminate = false
                        for (i in 1..100) {
                            if (i == 80 || i == 85 || i == 90 || i == 95) progressIndicator.waveAmplitude = progressIndicator.waveAmplitude - 2
                            progressIndicator.progress = i
                            delay(10)
                        }
                        progressIndicator.animate()
                            .alpha(0f)
                            .setDuration(500)
                            .withEndAction {
                                progressIndicator.visibility = View.GONE
                            }
                            .start()
                        Toast.makeText(
                            this@SettingsActivity,
                            "Backup successful",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("Backup", "Error during backup", e)
                    withContext(Dispatchers.Main) {
                        progressIndicator.visibility = View.GONE
                        Toast.makeText(
                            this@SettingsActivity,
                            "Backup failed. Please try again later.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        scheduledTimeSetting.setOnClickListener {
            val (currentHour, currentMinute) = settingsViewModel.getScheduledBackupTime()
            val materialTimePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(currentHour)
                .setMinute(currentMinute)
                .setTitleText("Select Scheduled backup time")
                .build()
            materialTimePicker.show(supportFragmentManager, "time_picker")
            materialTimePicker.addOnPositiveButtonClickListener {
                settingsViewModel.setScheduledBackupTime(materialTimePicker.hour, materialTimePicker.minute, this)
                tvScheduledTime.text  = String.format(
                    Locale.getDefault(),
                    "%02d:%02d %s",
                    if (materialTimePicker.hour % 12 == 0) 12 else materialTimePicker.hour % 12,
                    materialTimePicker.minute,
                    if (materialTimePicker.hour < 12) "AM" else "PM"
                )
                Snackbar.make(
                    findViewById(R.id.settingsMain),
                    "Backup scheduled at ${String.format(
                        Locale.getDefault(),
                        "%02d:%02d %s",
                        if (materialTimePicker.hour % 12 == 0) 12 else materialTimePicker.hour % 12,
                        materialTimePicker.minute,
                        if (materialTimePicker.hour < 12) "AM" else "PM"
                    )}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }


        appearanceSetting.setOnClickListener {
            val currentTheme = settingsViewModel.getThemeMode()
            val sheet = ThemePickerBottomSheet(currentTheme) { selectedMode ->
                settingsViewModel.setThemeMode(selectedMode)
            }
            sheet.show(supportFragmentManager, "ThemePicker")
        }

        cleanupSetting.setOnClickListener {
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val currentSnippetDays = prefs.getInt("cleanup_snippet_days", -1)
            val currentOtpHours = prefs.getInt("cleanup_otp_hours", -1)

            val sheet = CleanupRulesBottomSheet(
                when (currentSnippetDays) {
                    30 -> 1; 60 -> 2; 90 -> 3; else -> 0
                },
                when (currentOtpHours) {
                    12 -> 12; 24 -> 24; 36 -> 36; 48 -> 48; else -> 0
                }
            ) { snippetDays, otpHours ->
                prefs.edit {
                    putInt("cleanup_snippet_days", snippetDays)
                    putInt("cleanup_otp_hours", otpHours)
                }
                settingsViewModel.setCleanupRules(snippetDays, otpHours)
            }

            sheet.show(supportFragmentManager, "CleanupRules")
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

        findViewById<MaterialToolbar>(R.id.settingsToolbar)
            ?.setNavigationOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN && resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                task.result?.let { account ->
                    buildDriveService(account)
                    settingsViewModel.setCloudSyncMode(CloudSyncMode.GOOGLE_DRIVE)
                }
            } else {
                Log.e("Drive", "Google sign-in failed", task.exception)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    @Suppress("DEPRECATION")
    private fun signInToGoogle() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            Snackbar.make(findViewById(R.id.settingsMain), "Already signed in with ${account.email}", Snackbar.LENGTH_LONG).show()
            buildDriveService(account)
            settingsViewModel.setCloudSyncMode(CloudSyncMode.GOOGLE_DRIVE)
        } else {
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildDriveService(account: GoogleSignInAccount, justBuild: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    applicationContext, listOf(DriveScopes.DRIVE_APPDATA)
                ).apply {
                    selectedAccount = account.account
                }

                driveService = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("SnipIt").build()

                try {
                    driveService?.files()?.list()?.setPageSize(1)?.execute()
                } catch (e: UserRecoverableAuthIOException) {
                    withContext(Dispatchers.Main) {
                        startActivityForResult(e.intent, RC_SIGN_IN)
                    }
                    return@launch
                }

                DriveServiceHolder.driveServiceObject = driveService
                if (justBuild) return@launch

                downloadAndRestoreFromDrive(driveService, this@SettingsActivity)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Snackbar.make(
                        findViewById(R.id.settingsMain),
                        "Drive setup failed",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun findBackupFile(driveService: Drive?): File? {
        val result = driveService?.files()?.list()
            ?.setQ("name = 'snipit_backup.json' and trashed = false")
            ?.setSpaces("appDataFolder")
            ?.setFields("files(id, name)")
            ?.execute()

        return result?.files?.firstOrNull()
    }

    fun downloadAndRestoreFromDrive(driveService: Drive?, context: Context, onRestored: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val file = findBackupFile(driveService) ?: run {
                return@launch
            }

            try {
                val outputStream = ByteArrayOutputStream()
                driveService?.files()?.get(file.id)?.executeMediaAndDownloadTo(outputStream)

                val json = outputStream.toString("UTF-8")
                val jsonObject = JSONObject(json)
                val snippetArray = jsonObject.getJSONArray("snippets")

                val importCount = settingsViewModel.restoreSnippets(snippetArray, snippetViewModel)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Restored $importCount snippets", Toast.LENGTH_SHORT)
                        .show()
                    onRestored?.invoke()
                }
            } catch (_: Exception) { }
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
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
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:$packageName".toUri()
                    )
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

    private fun ensureDriveBackupScheduled(context: Context) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("DriveBackup")
            .addListener({
                val infos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork("DriveBackup")
                    .get()

                if (infos.isEmpty()) {
                    Log.d("DriveBackup", "WorkManager job missing. Rescheduling…")
                    val prefs = context.getSharedPreferences("sync_prefs", MODE_PRIVATE)
                    val hour = prefs.getInt("scheduled_hour", 3)
                    val minute = prefs.getInt("scheduled_minute", 0)

                    SyncScheduler.scheduleDriveBackup(context, hour, minute)
                } else {
                    Log.d("DriveBackup", "Backup job already scheduled.")
                }
            }, Executors.newSingleThreadExecutor())
    }


    private fun clearClipboard(view: View) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()
        Snackbar.make(view, "Clipboard cleared", Snackbar.LENGTH_SHORT).show()
    }
}