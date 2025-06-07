package com.example.snipit.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.provider.CalendarContract
import android.util.Log
import com.example.snipit.data.SuggestedAction
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import com.example.snipit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ActionUtils {
    @SuppressLint("UseCompatLoadingForDrawables")
    fun getSuggestedActions(context: Context, text: String): List<SuggestedAction> {
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
            fullUrl?.replace("\n","")
            if (fullUrl != null) {
                try {
                    if (isUrlValid(fullUrl)) return@let
                    val intent = Intent(Intent.ACTION_VIEW, fullUrl.toUri())
                    val resolveInfo = packageManager.resolveActivity(intent, 0)
                    if (resolveInfo != null) {
                        val packageName = resolveInfo.activityInfo.packageName
                        val icon = try {
                            if (!packageName.contains("chrome")) {
                                packageManager.getApplicationInfo(packageName, 0).let {
                                    packageManager.getApplicationIcon(it)
                                }
                            }
                            else {
                                Log.d("ActionUtils", "It's chrome")
                                null
                            }
                        } catch (e: Exception) {
                            Log.d("ActionUtils", "Failed to get icon for package: $packageName Error: ${e.localizedMessage}")
                            null
                        }
                        actions.add(SuggestedAction("Open Link", icon, intent))
                    } else {
                        Log.e("ActionUtils", "No activity found to handle intent: $intent")
                    }
                } catch (e: Exception) {
                    Log.e("ActionUtils", "Failed to create intent: ${e.localizedMessage}")
                }
            }
        }

        Regex("""\+?[0-9][0-9()\-\s]{7,}""").find(text)?.value?.let { phone ->
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

        if (text.contains("meeting", ignoreCase = true) || Regex("""\d{1,2}/\d{1,2}/\d{2,4}""").containsMatchIn(text)) {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, "New Event")
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis())
            }
            val icon = intent.resolveActivity(packageManager)?.let {
                packageManager.getApplicationIcon(it.packageName)
            }
            actions.add(SuggestedAction("Add to Calendar", icon, intent))
        }

        return actions
    }

    private fun isUrlValid(url: String): Boolean {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.instanceFollowRedirects = true
            connection.connect()
            val code = connection.responseCode
            connection.disconnect()
            if (code in 200..399) return true
        } catch (_: Exception) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.instanceFollowRedirects = true
                connection.connect()
                val code = connection.responseCode
                connection.disconnect()
                return code in 200..399
            } catch (_: Exception) {
                return false
            }
        }
        return false
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
}