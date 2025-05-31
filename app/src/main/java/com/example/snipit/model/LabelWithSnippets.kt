package com.example.snipit.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class LabelWithSnippets(
    @Embedded val label: Label,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = SnippetLabelRelation::class,
            parentColumn = "labelId",
            entityColumn = "snippetId"
        )
    )
    val snippets: List<Snippet>
)
