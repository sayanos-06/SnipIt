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
import com.example.snipit.ui.SettingsActivity.ThemeMode
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _snipitServiceEnabled = MutableLiveData<Boolean>()
    val snipitServiceEnabled: LiveData<Boolean> get() = _snipitServiceEnabled
    private val _isFloatingIconVisible = MutableLiveData<Boolean>()
    val isFloatingIconVisible: LiveData<Boolean> get() = _isFloatingIconVisible
    private val _themeMode = MutableLiveData<ThemeMode>()
    val themeMode: LiveData<ThemeMode> get() = _themeMode

    init {
        _snipitServiceEnabled.value = prefs.getBoolean("snipit_service_enabled", true)
        _isFloatingIconVisible.value = prefs.getBoolean("floating_tray_enabled", false)
        val savedMode = prefs.getInt("theme_mode", ThemeMode.SYSTEM.value)
        _themeMode.value = ThemeMode.fromValue(savedMode)
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
}