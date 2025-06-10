package com.example.snipit.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.snipit.data.SnippetDatabase
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ExportHelper(private val context: Context) {

    suspend fun exportSnippetsToDrive(driveService: Drive) {
        try {
            delay(5000)
            val dao = SnippetDatabase.getInstance(context).snippetDao()
            val snippets = dao.getAllSnippetsWithLabelsDirect()

            val jsonArray = JSONArray()
            snippets.forEach { item ->
                val obj = JSONObject()
                obj.put("text", item.snippet.text)
                obj.put("timestamp", item.snippet.timestamp)
                obj.put("labels", JSONArray(item.labels.map { it.name }))
                jsonArray.put(obj)
            }

            val root = JSONObject().apply {
                put("snippetCount", snippets.size)
                put("snippets", jsonArray)
            }

            val fileName = "snipit_backup.json"

            val metadata = File().apply {
                name = fileName
                mimeType = "application/json"
                parents = listOf("appDataFolder")
            }

            val contentStream = ByteArrayContent.fromString("application/json", root.toString(2))
            withContext(Dispatchers.IO) {
                driveService.files().create(metadata, contentStream)
                    .setFields("id")
                    .execute()
            }

            Handler(Looper.getMainLooper()).post {
                val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
                prefs.edit { putLong("last_synced_time", System.currentTimeMillis()) }
                Toast.makeText(context, "Backup uploaded to Google Drive successfully", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("Drive", "Error uploading backup to Google Drive", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}