package com.example.snipit.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import com.example.snipit.data.SnippetDatabase
import com.example.snipit.model.*
import com.example.snipit.utils.LabelSuggester
import kotlinx.coroutines.*

class SnippetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SnippetDatabase.getInstance(application).snippetDao()
    private val _snippetsWithLabels = MutableLiveData<List<SnippetWithLabels>>()
    val snippetsWithLabels: LiveData<List<SnippetWithLabels>> get() = _snippetsWithLabels
    val allLabels: LiveData<List<Label>> = repository.getAllLabels()
    private val _selectedSnippets = MutableLiveData<Set<Snippet>>(emptySet())
    val selectedSnippets: LiveData<Set<Snippet>> = _selectedSnippets
    private val _previousSelectedSnippets = MutableLiveData<Set<Snippet>>(emptySet())
    val previousSelectedSnippets: LiveData<Set<Snippet>> = _previousSelectedSnippets
    private val _currentLabelFilterId = MutableLiveData<Int?>(null)
    val currentLabelFilterId: LiveData<Int?> = _currentLabelFilterId
    private val _pinnedFilter = MutableLiveData<Boolean?>(null)
    val pinnedFilter: LiveData<Boolean?> = _pinnedFilter

    companion object {
        fun snippetUpdater(context: Context, text: String) {
            CoroutineScope(Dispatchers.IO).launch {
                val dao = SnippetDatabase.getInstance(context).snippetDao()
                val existing = dao.getSnippetByText(text)
                val now = System.currentTimeMillis()
                if (existing != null) {
                    dao.updateSnippet(existing.copy(timestamp = now))
                } else {
                    dao.insert(Snippet(text = text, timestamp = now))
                }
            }
        }
    }

    init {
        refreshSnippets()
    }

    fun refreshSnippets() {
        viewModelScope.launch {
            val updated = repository.getAllSnippetsWithLabelsDirect().toList()
            Log.d("SnippetViewModel", "Updating Snippets!")
            _snippetsWithLabels.postValue(updated)
        }
        Log.d("SnippetViewModel", "Refreshing Snippets!")
    }

    fun insertOrUpdateSnippet(text: String) {
        viewModelScope.launch {
            val snippet: Snippet
            val existing = repository.getSnippetByText(text)
            val now = System.currentTimeMillis()
            snippet = if (existing != null) {
                val updated = existing.copy(timestamp = now)
                val id = repository.updateSnippet(updated)
                updated.copy(id = id)
            } else {
                val newSnippet = Snippet(text = text, timestamp = now)
                val id = repository.insert(newSnippet)
                newSnippet.copy(id = id.toInt())
            }

            val suggestedLabels = LabelSuggester.suggestLabels(text)
            val existingLabels = repository.getAllLabelsDirect().toMutableList()
            val labelMap = existingLabels.associateBy { it.name }.toMutableMap()

            val matchedLabelIds = mutableListOf<Int>()
            for (name in suggestedLabels) {
                val label = labelMap[name]
                if (label != null) {
                    matchedLabelIds.add(label.id)
                } else {
                    val newLabel = Label(name = name)
                    val newId = repository.insertLabelDirect(newLabel)
                    matchedLabelIds.add(newId.toInt())
                    labelMap[name] = newLabel.copy(id = newId.toInt())
                }
            }

            matchedLabelIds.forEach { labelId ->
                repository.insertSnippetLabelRelation(SnippetLabelRelation(snippet.id, labelId))
            }

            refreshSnippets()
        }
    }

    suspend fun doesSnippetExist(text: String): Boolean {
        val existing = repository.getSnippetByText(text.trim())
        return existing != null
    }

    fun insertSnippetWithLabels(text: String, timestamp: Long, labels: List<String>) {
        viewModelScope.launch {
            val snippet = Snippet(text = text.trim(), timestamp = timestamp)
            val snippetId = repository.insert(snippet).toInt()
            val existingLabels = repository.getAllLabelsDirect()
            val labelMap = existingLabels.associateBy { it.name }.toMutableMap()
            val labelIds = mutableListOf<Int>()
            for (name in labels.distinct()) {
                val existingLabel = labelMap[name]
                val labelId = if (existingLabel != null) {
                    existingLabel.id
                } else {
                    val newId = repository.insertLabelDirect(Label(name = name))
                    labelMap[name] = Label(id = newId.toInt(), name = name)
                    newId.toInt()
                }
                labelIds.add(labelId)
            }
            labelIds.forEach { labelId ->
                repository.insertSnippetLabelRelation(SnippetLabelRelation(snippetId, labelId))
            }
            refreshSnippets()
        }
    }

    fun deleteSnippet(snippet: Snippet) = viewModelScope.launch {
        repository.deleteSnippet(snippet)
        refreshSnippets()
    }

    fun restoreSnippetWithLabels(snippet: Snippet, labelIds: List<Int>) {
        viewModelScope.launch {
            repository.insert(snippet)
            repository.clearLabelsForSnippet(snippet.id)
            labelIds.forEach { id ->
                repository.insertSnippetLabelRelation(SnippetLabelRelation(snippet.id, id))
            }
            refreshSnippets()
        }
    }

    fun updateSnippet(snippet: Snippet) = viewModelScope.launch {
        repository.updateSnippet(snippet)
        refreshSnippets()
    }

    fun updatePinStatus(id: Int, isPinned: Boolean) = viewModelScope.launch {
        repository.updatePinStatus(id, isPinned)
        refreshSnippets()
    }

    suspend fun getLabelsForSnippet(snippetId: Int): List<Label> {
        return repository.getSnippetWithLabelsDirect(snippetId).labels
    }

    fun insertLabel(label: Label, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                repository.insertLabel(label)
            }
            onComplete(id)
            refreshSnippets()
        }
    }

    fun assignLabelsToSnippet(snippetId: Int, selectedLabelIds: Set<Int>) {
        viewModelScope.launch {
            repository.clearLabelsForSnippet(snippetId)
            selectedLabelIds.forEach { id ->
                repository.insertSnippetLabelRelation(SnippetLabelRelation(snippetId, id))
            }
            Log.d("SnippetViewModel", "Assigned labels to snippet")
            refreshSnippets()
        }
    }

    fun deleteLabel(label: Label) {
        viewModelScope.launch {
            repository.deleteRelationsByLabelId(label.id)
            repository.deleteLabel(label)
            Log.d("SnippetViewModel", "Deleted label with ID: ${label.id} and name: ${label.name}")
            refreshSnippets()
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

    fun setLabelFilter(labelId: Int?) {
        _currentLabelFilterId.value = labelId
    }

    fun setPinnedFilter(pinned: Boolean?) {
        _pinnedFilter.value = pinned
    }
}