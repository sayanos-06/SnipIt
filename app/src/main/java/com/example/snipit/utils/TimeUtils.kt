package com.example.snipit.utils

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object TimeUtils {
    fun getRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val calNow = Calendar.getInstance()
        val calThen = Calendar.getInstance().apply { timeInMillis = timestamp }

        if (calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)) {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            return timeFormat.format(Date(timestamp))
        }

        calNow.add(Calendar.DAY_OF_YEAR, -1)
        if (calNow.get(Calendar.YEAR) == calThen.get(Calendar.YEAR) &&
            calNow.get(Calendar.DAY_OF_YEAR) == calThen.get(Calendar.DAY_OF_YEAR)) {
            return "Yesterday"
        }

        val daysAgo = TimeUnit.MILLISECONDS.toDays(diff).toInt() + 1
        val weeksAgo = daysAgo / 7
        val monthsAgo = daysAgo / 30
        val yearsAgo = daysAgo / 365

        return when {
            daysAgo < 7 -> "$daysAgo days ago"
            weeksAgo < 4 -> "$weeksAgo week${if (weeksAgo > 1) "s" else ""} ago"
            monthsAgo < 12 -> "$monthsAgo month${if (monthsAgo > 1) "s" else ""} ago"
            else -> "$yearsAgo year${if (yearsAgo > 1) "s" else ""} ago"
        }
    }

    fun formatSyncTime(timestamp: Long): String {
        return if (timestamp <= 0L) "Never" else {
            val sdf = SimpleDateFormat("hh:mm a, dd MMM yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
