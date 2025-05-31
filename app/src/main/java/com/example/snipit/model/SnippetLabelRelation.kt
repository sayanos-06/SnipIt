package com.example.snipit.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    primaryKeys = ["snippetId", "labelId"],
    foreignKeys = [
        ForeignKey(
            entity = Snippet::class,
            parentColumns = ["id"],
            childColumns = ["snippetId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Label::class,
            parentColumns = ["id"],
            childColumns = ["labelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("snippetId"), Index("labelId")]
)
data class SnippetLabelRelation (
    val snippetId: Int,
    val labelId: Int
)