package com.example.snipit.utils

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.snipit.data.SnippetDatabase
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.content.edit
import com.example.snipit.R
import com.example.snipit.model.Label
import com.example.snipit.model.Snippet
import com.example.snipit.model.SnippetLabelRelation
import com.example.snipit.model.SnippetWithLabels
import com.example.snipit.ui.MainActivity
import com.example.snipit.viewModels.SnippetViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ExportHelper(private val context: Context) {

    suspend fun exportSnippetsToDrive(driveService: Drive, override: Boolean = false) {
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

            if (!override) {
                val newSnippets = downloadAndRestoreFromDrive(driveService)
                newSnippets?.forEach { item ->
                    if (snippets.none { it.snippet.text == item.snippet.text }) {
                        val obj = JSONObject()
                        obj.put("text", item.snippet.text)
                        obj.put("timestamp", item.snippet.timestamp)
                        obj.put("labels", JSONArray(item.labels.map { it.name }))
                        jsonArray.put(obj)
                    }
                }
            }

            val root = JSONObject().apply {
                put("snippetCount", jsonArray.length())
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
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val builder = NotificationCompat.Builder(context, "drive_backup_channel")
                    .setSmallIcon(R.drawable.notification_small_icon)
                    .setContentTitle("SnipIt Backup Completed")
                    .setContentText("Your snippets were uploaded to Google Drive successfully.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                val notificationManager = NotificationManagerCompat.from(context)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationManager.notify(1001, builder.build())
                        }
                    } else {
                        notificationManager.notify(1001, builder.build())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("Drive", "Error displaying notification", e)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("Drive", "Error uploading backup to Google Drive", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
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

    suspend fun downloadAndRestoreFromDrive(driveService: Drive?): List<SnippetWithLabels>? {
        val file = findBackupFile(driveService) ?: run {
            return null
        }

        return try {
            val outputStream = ByteArrayOutputStream()
            driveService?.files()?.get(file.id)?.executeMediaAndDownloadTo(outputStream)

            val json = outputStream.toString("UTF-8")
            val jsonObject = JSONObject(json)
            val snippetArray = jsonObject.getJSONArray("snippets")

            restoreSnippets(snippetArray)
        } catch (e: Exception) {
            Log.e("Drive", "Error downloading backup from Google Drive", e)
            emptyList()
        }
    }

    suspend fun restoreSnippets(snippetArray: JSONArray): List<SnippetWithLabels> {
        ActionUtils.clearActionCache()
        val newSnippets: MutableList<SnippetWithLabels> = mutableListOf()
        val repository = SnippetDatabase.getInstance(context).snippetDao()
        val bin = SnippetDatabase.getInstance(context).binDao()
        for (i in 0 until snippetArray.length()) {
            val item = snippetArray.getJSONObject(i)
            val text = item.getString("text")
            val timestamp = item.getLong("timestamp")
            val labels = item.optJSONArray("labels")?.let { arr ->
                List(arr.length()) { index -> arr.getString(index) }
            } ?: emptyList()

            if (repository.getSnippetByText(text) != null) continue
            if (bin.getBinSnippetByText(text) != null) continue

            val id = repository.insert(Snippet(text = text, timestamp = timestamp)).toInt()
            val labelMap = repository.getAllLabelsDirect().associateBy { it.name }
            val newLabelIdMap = emptyMap<Int, String>().toMutableMap()
            for (label in labels) {
                val labelId = labelMap[label]?.id ?: repository.insertLabelDirect(Label(name = label)).toInt()
                newLabelIdMap += labelId to label
                repository.insertSnippetLabelRelation(SnippetLabelRelation(id, labelId))
            }
            val eachSnippetlabels = newLabelIdMap.map { Label(it.key, it.value) }
            newSnippets.add(SnippetWithLabels(
                Snippet(id, text, timestamp),
                eachSnippetlabels
            ))
        }
        return newSnippets.toList()
    }
}