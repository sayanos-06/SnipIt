package com.example.snipit.model

import android.content.Intent
import android.graphics.drawable.Drawable

data class SuggestedAction(
    val label: String,
    val icon: Drawable?,
    val intent: Intent
)