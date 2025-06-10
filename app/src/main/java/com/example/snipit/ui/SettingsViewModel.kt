package com.example.snipit.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.snipit.R
import com.example.snipit.data.SnippetDatabase
import com.example.snipit.model.Label
import com.example.snipit.model.Snippet
import com.example.snipit.model.SnippetLabelRelation
import com.example.snipit.ui.SettingsActivity.CloudSyncMode
import com.example.snipit.ui.SettingsActivity.ThemeMode
import com.example.snipit.utils.ActionUtils
import com.example.snipit.utils.SyncScheduler
import com.google.android.material.materialswitch.MaterialSwitch
import org.json.JSONArray

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _snipitServiceEnabled = MutableLiveData<Boolean>()
    val snipitServiceEnabled: LiveData<Boolean> get() = _snipitServiceEnabled
    private val _isFloatingIconVisible = MutableLiveData<Boolean>()
    val isFloatingIconVisible: LiveData<Boolean> get() = _isFloatingIconVisible
    private val _cleanupSnippetDays = MutableLiveData<Int>()
    private val _cloudSyncMode = MutableLiveData<CloudSyncMode>()
    val cloudSyncMode: LiveData<CloudSyncMode> get() = _cloudSyncMode
    private val _cleanupOtpHours = MutableLiveData<Int>()
    private val _themeMode = MutableLiveData<ThemeMode>()
    val themeMode: LiveData<ThemeMode> get() = _themeMode
    val repository = SnippetDatabase.getInstance(application).snippetDao()
    private val _suggestedActionsEnabled = MutableLiveData<Boolean>()
    val suggestedActionsEnabled: LiveData<Boolean> get() = _suggestedActionsEnabled

    init {
        _snipitServiceEnabled.value = prefs.getBoolean("snipit_service_enabled", true)
        _isFloatingIconVisible.value = prefs.getBoolean("floating_tray_enabled", false)
        val savedMode = prefs.getInt("theme_mode", ThemeMode.SYSTEM.value)
        _themeMode.value = ThemeMode.fromValue(savedMode)
        val savedCloudSyncMode = prefs.getInt("cloud_sync_mode", CloudSyncMode.OFF.value)
        _cloudSyncMode.value = CloudSyncMode.fromValue(savedCloudSyncMode)
        _suggestedActionsEnabled.value = prefs.getBoolean("suggested_actions_enabled", true)
    }

    fun setFloatingIconState(state: Boolean) {
        _isFloatingIconVisible.value = state
        prefs.edit { putBoolean("floating_tray_enabled", state) }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    fun setSnipitServiceState(
        state: Boolean,
        context: Context,
        snipitServiceSwitch: MaterialSwitch,
        floatingTraySwitch: MaterialSwitch
    ) {
        prefs.edit { putBoolean("snipit_service_enabled", state) }
        _snipitServiceEnabled.value = state
        if(state){
            snipitServiceSwitch.thumbIconDrawable = context.getDrawable(R.drawable.round_check_24)
            floatingTraySwitch.isEnabled = true
            floatingTraySwitch.isChecked = prefs.getBoolean("floating_tray_enabled", true)
        } else {
            snipitServiceSwitch.thumbIconDrawable = context.getDrawable(R.drawable.round_close_24)
            floatingTraySwitch.isChecked = false
            floatingTraySwitch.isEnabled = false
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putInt("theme_mode", mode.value) }
        _themeMode.value = mode
        AppCompatDelegate.setDefaultNightMode(mode.value)
    }

    fun getThemeMode(): ThemeMode {
        val savedMode = prefs.getInt("theme_mode", ThemeMode.SYSTEM.value)
        return ThemeMode.fromValue(savedMode)
    }

    fun setCloudSyncMode(mode: CloudSyncMode) {
        prefs.edit { putInt("cloud_sync_mode", mode.value) }
        _cloudSyncMode.value = mode
        if (mode == CloudSyncMode.GOOGLE_DRIVE) {
            SyncScheduler.scheduleDriveBackup(getApplication())
        } else {
            SyncScheduler.cancelDriveBackup(getApplication())
        }
    }

    fun setCleanupRules(snippetDays: Int, otpHours: Int) {
        _cleanupSnippetDays.value = snippetDays
        _cleanupOtpHours.value = otpHours
    }

    suspend fun restoreSnippets(snippetArray: JSONArray, snippetViewModel: SnippetViewModel) : Int {
        ActionUtils.clearActionCache()
        var count = 0
        for (i in 0 until snippetArray.length()) {
            val item = snippetArray.getJSONObject(i)
            val text = item.getString("text")
            val timestamp = item.getLong("timestamp")
            val labels = item.optJSONArray("labels")?.let { arr ->
                List(arr.length()) { index -> arr.getString(index) }
            } ?: emptyList()

            if (repository.getSnippetByText(text) != null) continue

            val id = repository.insert(Snippet(text = text, timestamp = timestamp)).toInt()
            val labelMap = repository.getAllLabelsDirect().associateBy { it.name }

            for (label in labels) {
                val labelId = labelMap[label]?.id ?: repository.insertLabelDirect(Label(name = label)).toInt()
                repository.insertSnippetLabelRelation(SnippetLabelRelation(id, labelId))
            }
            count ++
        }
        snippetViewModel.refreshSnippets()
        return count
    }

    fun setSuggestedActionsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("suggested_actions_enabled", enabled) }
        _suggestedActionsEnabled.value = enabled
    }
}