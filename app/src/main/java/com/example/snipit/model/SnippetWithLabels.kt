package com.example.snipit.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class SnippetWithLabels(
    @Embedded val snippet: Snippet,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = SnippetLabelRelation::class,
            parentColumn = "snippetId",
            entityColumn = "labelId"
        )
    )
    val labels: List<Label>
)
