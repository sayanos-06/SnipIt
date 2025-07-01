package com.example.snipit.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Bin(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalId: Int? = null,
    val text: String,
    val timestamp: Long,
    val deletedAt: Long
)