package com.example.snipit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.snipit.model.Label
import com.example.snipit.model.Snippet
import com.example.snipit.model.SnippetLabelRelation

@Database(
    entities = [Snippet::class, Label::class, SnippetLabelRelation::class],
    version = 1,
    exportSchema = false
)
abstract class SnippetDatabase : RoomDatabase() {
    abstract fun snippetDao(): SnippetDao

    companion object {
        @Volatile private var INSTANCE: SnippetDatabase? = null

        fun getInstance(context: Context): SnippetDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SnippetDatabase::class.java,
                    "snippets_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
