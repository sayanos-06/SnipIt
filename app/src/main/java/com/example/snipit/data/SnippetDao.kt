package com.example.snipit.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.snipit.model.Label
import com.example.snipit.model.LabelWithSnippets
import com.example.snipit.model.Snippet
import com.example.snipit.model.SnippetLabelRelation
import com.example.snipit.model.SnippetWithLabels

@Dao
interface SnippetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(snippet: Snippet): Long

    @Query("SELECT * FROM Snippet ORDER BY isPinned DESC, timestamp DESC")
    fun getAllSnippets(): LiveData<List<Snippet>>

    @Query("SELECT * FROM Snippet ORDER BY isPinned DESC, timestamp DESC")
    suspend fun getAllSnippetsDirect(): List<Snippet>

    @Query("SELECT * FROM Snippet WHERE text = :text LIMIT 1")
    suspend fun getSnippetByText(text: String): Snippet?

    @Update
    suspend fun updateSnippet(snippet: Snippet): Int

    @Query("UPDATE Snippet SET isPinned = :pinned WHERE id = :id")
    suspend fun updatePinStatus(id: Int, pinned: Boolean)

    @Delete
    suspend fun deleteSnippet(snippet: Snippet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabel(label: Label): Long

    @Insert
    suspend fun insertLabelDirect(label: Label): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSnippetLabelRelation(relation: SnippetLabelRelation)

    @Transaction
    @Query("SELECT * FROM Snippet WHERE id = :snippetId ORDER BY isPinned DESC, timestamp DESC")
    fun getSnippetWithLabelsLive(snippetId: Int): LiveData<SnippetWithLabels>

    @Transaction
    @Query("SELECT * FROM Snippet ORDER BY isPinned DESC, timestamp DESC")
    fun getAllSnippetsWithLabelsLive(): LiveData<List<SnippetWithLabels>>

    @Transaction
    @Query("SELECT * FROM Snippet ORDER BY isPinned DESC, timestamp DESC")
    suspend fun getAllSnippetsWithLabelsDirect(): List<SnippetWithLabels>

    @Transaction
    @Query("SELECT * FROM Snippet WHERE id = :snippetId ORDER BY isPinned DESC, timestamp DESC")
    suspend fun getSnippetWithLabelsDirect(snippetId: Int): SnippetWithLabels

    @Transaction
    @Query("SELECT * FROM Label WHERE id = :labelId")
    fun getLabelWithSnippetsLive(labelId: Int): LiveData<LabelWithSnippets>

    @Transaction
    @Query("SELECT * FROM Label")
    fun getAllLabelsWithSnippets(): LiveData<List<LabelWithSnippets>>

    @Transaction
    @Query("SELECT * FROM Label WHERE id = :labelId")
    suspend fun getLabelWithSnippetsDirect(labelId: Int): LabelWithSnippets

    @Query("DELETE FROM SnippetLabelRelation WHERE snippetId = :snippetId")
    suspend fun clearLabelsForSnippet(snippetId: Int)

    @Delete
    suspend fun deleteLabel(label: Label)

    @Delete
    suspend fun deleteSnippetLabelRelation(relation: SnippetLabelRelation)

    @Query("DELETE FROM SnippetLabelRelation WHERE labelId = :labelId")
    suspend fun deleteRelationsByLabelId(labelId: Int)

    @Query("SELECT * FROM Label ORDER BY name ASC")
    fun getAllLabels(): LiveData<List<Label>>

    @Query("SELECT * FROM Label ORDER BY name ASC")
    suspend fun getAllLabelsDirect(): List<Label>
}
