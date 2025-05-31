package com.example.snipit.ui

import android.app.Application
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.snipit.ui.SettingsActivity.ThemeMode

class AppObserver : Application(), LifecycleObserver {

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val modeValue = prefs.getInt("theme_mode", ThemeMode.SYSTEM.value)
        AppCompatDelegate.setDefaultNightMode(modeValue)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onEnterForeground() {
        sendBroadcast(Intent("com.example.snipit.HIDE_ICON"))
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onEnterBackground() {
        sendBroadcast(Intent("com.example.snipit.SHOW_ICON"))
    }
}