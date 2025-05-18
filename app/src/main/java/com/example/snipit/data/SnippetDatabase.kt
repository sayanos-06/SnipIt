package com.example.snipit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.snipit.model.Snippet

@Database(entities = [Snippet::class], version = 1)
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
