package com.example.snipit.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat.getDrawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.snipit.data.SnippetDatabase
import com.example.snipit.model.Snippet
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.example.snipit.R
import com.google.android.material.materialswitch.MaterialSwitch

class SnippetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SnippetDatabase.getInstance(application).snippetDao()
    val snippets: LiveData<List<Snippet>> = repository.getAllSnippets()
    private val _selectedSnippets = MutableLiveData<Set<Snippet>>(emptySet())
    val selectedSnippets: LiveData<Set<Snippet>> = _selectedSnippets
    private val _previousSelectedSnippets = MutableLiveData<Set<Snippet>>(emptySet())
    val previousSelectedSnippets: LiveData<Set<Snippet>> = _previousSelectedSnippets
    private val _isFloatingIconEnabled = MutableLiveData<Boolean>()
    val isFloatingIconEnabled: LiveData<Boolean> = _isFloatingIconEnabled

    init {
        val prefs = application.getSharedPreferences("snipit_prefs", Context.MODE_PRIVATE)
        _isFloatingIconEnabled.value = prefs.getBoolean("floating_icon_enabled", false)
    }

    fun insertOrUpdateSnippet(text: String) {
        viewModelScope.launch {
            val existingSnippet = repository.getSnippetByText(text)
            if (existingSnippet != null) {
                val updatedSnippet = existingSnippet.copy(timestamp = System.currentTimeMillis())
                repository.updateSnippet(updatedSnippet)
            } else {
                val newSnippet = Snippet(text = text, timestamp = System.currentTimeMillis())
                repository.insert(newSnippet)
            }
        }
    }

    fun setSelectedSnippets(snippets: Set<Snippet>) {
        if (_selectedSnippets.value != snippets) {
            _selectedSnippets.value = snippets
        }
    }

    fun clearSelectedSnippets() {
        _selectedSnippets.value = emptySet()
    }

    fun setPreviousSelectedSnippets(snippets: Set<Snippet>) {
        _previousSelectedSnippets.value = snippets
    }

    fun clearPreviousSelectedSnippets() {
        _previousSelectedSnippets.value = emptySet()
    }

    fun deleteSnippet(snippet: Snippet) = viewModelScope.launch {
        repository.deleteSnippet(snippet)
    }

    fun updatePinStatus(id: Int, isPinned: Boolean) {
        viewModelScope.launch {
            repository.updatePinStatus(id, isPinned)
        }
    }

    fun setFloatingIconEnabled(context: Context, state: Boolean, floatingIconSwitch: MaterialSwitch) {
        _isFloatingIconEnabled.value = state
        if (state) {
            floatingIconSwitch.setThumbIconDrawable(getDrawable(context, R.drawable.round_check_24))
        } else {
            floatingIconSwitch.setThumbIconDrawable(getDrawable(context, R.drawable.round_close_24))
        }
        val prefs = getApplication<Application>().getSharedPreferences("snipit_prefs", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("floating_icon_enabled", state) }
    }
}