package com.example.snipit.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val color: String = "#FF9800"
)