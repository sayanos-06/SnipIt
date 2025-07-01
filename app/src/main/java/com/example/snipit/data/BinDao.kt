package com.example.snipit.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.snipit.model.Bin

@Dao
interface BinDao {

    @Query("SELECT * FROM Bin ORDER BY deletedAt DESC")
    fun getAllBinSnippets(): LiveData<List<Bin>>

    @Insert
    suspend fun insert(binSnippet: Bin)

    @Query("DELETE FROM Bin WHERE originalId = :snippetId")
    suspend fun deleteBySnippetId(snippetId: Int)

    @Query("DELETE FROM Bin WHERE id = :binId")
    suspend fun deleteById(binId: Int)

    @Query("DELETE FROM Bin")
    suspend fun deleteAll()

    @Query("DELETE FROM Bin WHERE deletedAt < :threshold")
    suspend fun autoDeleteOld(threshold: Long)
}