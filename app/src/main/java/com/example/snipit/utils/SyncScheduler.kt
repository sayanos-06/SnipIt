package com.example.snipit.utils

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    fun scheduleDriveBackup(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<DriveUploadWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "DriveBackup",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        Log.d("SyncScheduler", "DriveBackup worker scheduled.")
    }

    fun cancelDriveBackup(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("DriveBackup")
        Log.d("SyncScheduler", "DriveBackup worker canceled.")
    }
}