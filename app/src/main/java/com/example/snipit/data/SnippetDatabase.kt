package com.example.snipit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.snipit.model.Bin
import com.example.snipit.model.Label
import com.example.snipit.model.Snippet
import com.example.snipit.model.SnippetLabelRelation

@Database(
    entities = [
        Snippet::class,
        Label::class,
        SnippetLabelRelation::class,
        Bin::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SnippetDatabase : RoomDatabase() {

    abstract fun snippetDao(): SnippetDao
    abstract fun binDao(): BinDao

    companion object {
        @Volatile private var INSTANCE: SnippetDatabase? = null

        val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bin (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        originalId INTEGER,
                        text TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        deletedAt INTEGER NOT NULL,
                        labels TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS bin_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        originalId INTEGER,
                        text TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        deletedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                database.execSQL("""
                    INSERT INTO bin_new (id, originalId, text, timestamp, deletedAt)
                    SELECT id, originalId, text, timestamp, deletedAt FROM bin
                """.trimIndent())

                database.execSQL("DROP TABLE bin")
                database.execSQL("ALTER TABLE bin_new RENAME TO bin")
            }
        }

        fun getInstance(context: Context): SnippetDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SnippetDatabase::class.java,
                    "snippets_db"
                )
                    .addMigrations(MIGRATION_1_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
        }
    }
}

