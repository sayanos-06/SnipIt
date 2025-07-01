package com.example.snipit.utils

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes

class DriveUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @Suppress("DEPRECATION")
    override suspend fun doWork(): Result {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(applicationContext)

        if (lastAccount == null) {
            Log.e("DriveWorker", "Not signed in")
            return Result.failure()
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            applicationContext, listOf(DriveScopes.DRIVE_APPDATA)
        ).apply {
            selectedAccount = lastAccount.account
        }

        val driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("SnipIt").build()

        return try {
            val exportHelper = ExportHelper(applicationContext)
            exportHelper.exportSnippetsToDrive(driveService)
            Result.success()
        } catch (e: Exception) {
            Log.e("DriveWorker", "Upload failed", e)
            Result.retry()
        }
    }
}