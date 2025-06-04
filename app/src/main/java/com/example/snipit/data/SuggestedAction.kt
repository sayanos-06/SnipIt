package com.example.snipit.data

import android.content.Intent
import android.graphics.drawable.Drawable

data class SuggestedAction(
    val label: String,
    val icon: Drawable?,
    val intent: Intent
)