package com.example.snipit.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import com.example.snipit.model.SuggestedAction
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import com.example.snipit.ai.DucklingClient
import okhttp3.internal.http2.Http2Reader
import java.net.HttpURLConnection
import java.net.URL

object ActionUtils {

    private val actionCache = mutableMapOf<Int, List<SuggestedAction>>()

    @SuppressLint("UseCompatLoadingForDrawables")
    fun getSuggestedActions(context: Context, text: String, id: Int, onComplete: (List<SuggestedAction>) -> Unit) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("suggested_actions_enabled", true)
        if (!enabled) {
            onComplete(listOf())
            return
        }

        actionCache[id]?.let {
            onComplete(it)
            return
        }

        val actions = mutableListOf<SuggestedAction>()
        val packageManager = context.packageManager

        val fullUrlRegex1 = Regex("""https?://\S+""")
        val fullUrlRegex2 = Regex("""http?://\S+""")
        val fallbackUrlRegex = Regex("""(?<!@)(?<!\S)(?:www\.)?[a-zA-Z0-9\-]+\.[a-zA-Z]{2,}(?:/\S*)?(?!\S)""")
        val match =
            if (fullUrlRegex1.containsMatchIn(text)) fullUrlRegex1.find(text)
            else if (fullUrlRegex2.containsMatchIn(text)) fullUrlRegex2.find(text)
            else if (fallbackUrlRegex.containsMatchIn(text)) fallbackUrlRegex.find(text)
            else null
        match?.value.let { rawUrl ->
            val fullUrl = rawUrl?.startsWith("http")?.let { if (!it) "https://$rawUrl" else rawUrl }
            if (fullUrl != null) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, fullUrl.toUri())
                    val resolveInfo = packageManager.resolveActivity(intent, 0)
                    if (resolveInfo != null) {
                        val packageName = resolveInfo.activityInfo.packageName
                        val icon = try {
                            if (!packageName.contains("chrome")) {
                                packageManager.getApplicationInfo(packageName, 0).let {
                                    packageManager.getApplicationIcon(it)
                                }
                            } else {
                                null
                            }
                        } catch (_: Exception) {
                            null
                        }
                        actions.add(SuggestedAction("Open Link", icon, intent))
                    }
                } catch (_: Exception) { }
            }
        }


        Regex("""\+?[0-9][0-9()\-\s]{10,}""").find(text)?.value?.let { phone ->
            val intent = Intent(Intent.ACTION_DIAL, "tel:$phone".toUri())
            val icon = intent.resolveActivity(packageManager)?.let {
                packageManager.getApplicationIcon(it.packageName)
            }
            actions.add(SuggestedAction("Call", icon, intent))
        }

        Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").find(text)?.value?.let { email ->
            val intent = Intent(Intent.ACTION_SENDTO, "mailto:$email".toUri())
            val icon = intent.resolveActivity(packageManager)?.let {
                packageManager.getApplicationIcon(it.packageName)
            }
            actions.add(SuggestedAction("Send Email", icon, intent))
        }

        try {
            DucklingClient.parse(text,
                onSuccess = { results ->
                    for (r in results) {
                        if (r.dim == "time") {
                            val detectedEventTime = r.value
                            val offsetDateTime = java.time.OffsetDateTime.parse(detectedEventTime)
                            val startMillis = offsetDateTime.toInstant().toEpochMilli()

                            val intent = Intent(Intent.ACTION_INSERT).apply {
                                data = CalendarContract.Events.CONTENT_URI
                                putExtra(CalendarContract.Events.TITLE, "New Event")
                                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                            }

                            val icon = intent.resolveActivity(packageManager)?.let {
                                packageManager.getApplicationIcon(it.packageName)
                            }
                            actions.add(SuggestedAction("Add Event", icon, intent))
                        }
                    }

                    onComplete(actions)
                },
                onError = { error ->
                    onComplete(actions)
                    Log.e("Duckling", "Parse error: ${error.message}")
                }
            )
        } catch (e: Error) {
            onComplete(actions)
            Log.e("ActionUtils", "Error: ${e.message}")
        }
        actionCache[id] = actions
    }

    fun Drawable.toBitmapDrawable(context: Context, sizePx: Int): BitmapDrawable {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        if (this is AdaptiveIconDrawable) {
            val drawable = LayerDrawable(arrayOf(foreground))
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
        } else {
            this.setBounds(0, 0, sizePx, sizePx)
            this.draw(canvas)
        }
        return bitmap.toDrawable(context.resources)
    }

    fun clearActionCache() {
        actionCache.clear()
    }
}