package com.example.snipit.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.snipit.data.SnippetDatabase
import com.example.snipit.model.Bin
import com.example.snipit.model.Snippet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BinViewModel(application: Application) : AndroidViewModel(application) {

    private val binDao = SnippetDatabase.getInstance(application).binDao()
    private val snippetDao = SnippetDatabase.getInstance(application).snippetDao()
    val trashedSnippets: LiveData<List<Bin>> = binDao.getAllBinSnippets()
    private val _selectedBinItems = MutableLiveData<List<Bin>>(emptyList())
    val selectedBinItems: LiveData<List<Bin>> = _selectedBinItems

    fun insert(bin: Bin) {
        viewModelScope.launch(Dispatchers.IO) {
            binDao.insert(bin)
        }
    }

    fun restore(bin: Bin) {
        viewModelScope.launch(Dispatchers.IO) {
            val snippet = Snippet(text = bin.text, timestamp = System.currentTimeMillis())
            snippetDao.insert(snippet)
            binDao.deleteById(bin.id)
        }
    }

    fun delete(bin: Bin) {
        viewModelScope.launch(Dispatchers.IO) {
            binDao.deleteById(bin.id)
        }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) {
            binDao.deleteAll()
        }
    }

    fun autoDelete() {
        val threshold = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        viewModelScope.launch(Dispatchers.IO) {
            binDao.autoDeleteOld(threshold)
        }
    }

    fun setSelectedBinItems(binItems: List<Bin>) {
        if (_selectedBinItems.value != binItems) {
            _selectedBinItems.value = binItems
        }
    }

    fun clearSelectedBinItems() {
        _selectedBinItems.value = emptyList()
    }
}
