package com.example.snipit.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.snipit.model.Snippet

@Dao
interface SnippetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(snippet: Snippet)

    @Query("SELECT * FROM Snippet ORDER BY timestamp DESC")
    fun getAllSnippets(): LiveData<List<Snippet>>

    @Query("SELECT * FROM Snippet WHERE text = :text LIMIT 1")
    suspend fun getSnippetByText(text: String): Snippet?

    @Update
    suspend fun updateSnippet(snippet: Snippet)

    @Delete
    suspend fun deleteSnippet(snippet: Snippet)
}
