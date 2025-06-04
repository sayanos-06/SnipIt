package com.example.snipit.utils

import android.content.Context
import com.example.snipit.data.SnippetDatabase
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import org.json.JSONArray
import org.json.JSONObject

class ExportHelper(private val context: Context) {

    suspend fun exportSnippetsToDrive(driveService: Drive) {
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

        val metadata = File()
            .setName(fileName)
            .setMimeType("application/json")

        val contentStream = ByteArrayContent.fromString("application/json", root.toString(2))

        driveService.files().create(metadata, contentStream).execute()
    }
}