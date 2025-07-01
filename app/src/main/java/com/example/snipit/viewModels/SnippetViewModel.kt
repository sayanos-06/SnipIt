package com.example.snipit.viewModels

import android.app.Application
import android.content.Context
import android.util.Log
import android.view.View
import androidx.lifecycle.*
import com.example.snipit.ai.DucklingClient
import com.example.snipit.data.SnippetDatabase
import com.example.snipit.model.*
import com.example.snipit.utils.ActionUtils
import com.example.snipit.utils.LabelSuggester
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*

class SnippetViewModel(application: Application) : AndroidViewModel(application) {

    private val snippetRepo = SnippetDatabase.getInstance(application).snippetDao()
    private val binRepo = SnippetDatabase.getInstance(application).binDao()
    private val _snippetsWithLabels = MutableLiveData<List<SnippetWithLabels>>()
    val snippetsWithLabels: LiveData<List<SnippetWithLabels>> get() = _snippetsWithLabels
    val allLabels: LiveData<List<Label>> = snippetRepo.getAllLabels()
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
            val allSnippets = snippetRepo.getAllSnippetsWithLabelsDirect()
            _snippetsWithLabels.postValue(allSnippets)
        }
    }

    fun insertOrUpdateSnippet(text: String) {
        viewModelScope.launch {
            ActionUtils.clearActionCache()
            val snippet: Snippet
            val existing = snippetRepo.getSnippetByText(text)
            val now = System.currentTimeMillis()
            snippet = if (existing != null) {
                val updated = existing.copy(timestamp = now)
                val id = snippetRepo.updateSnippet(updated)
                updated.copy(id = id)
            } else {
                val newSnippet = Snippet(text = text, timestamp = now)
                val id = snippetRepo.insert(newSnippet)
                newSnippet.copy(id = id.toInt())
            }

            val suggestedLabels = LabelSuggester.suggestLabels(text).toMutableList()
            DucklingClient.parse(text,
                onSuccess = { results ->
                    viewModelScope.launch {
                        for (r in results) {
                            val label = when (r.dim) {
                                "time" -> "Event"
                                else -> continue
                            }
                            if (label !in suggestedLabels) {
                                suggestedLabels.add(label)
                            }
                        }
                        val existingLabels = snippetRepo.getAllLabelsDirect().toMutableList()
                        val labelMap = existingLabels.associateBy { it.name }.toMutableMap()

                        val matchedLabelIds = mutableListOf<Int>()
                        for (name in suggestedLabels) {
                            val label = labelMap[name]
                            if (label != null) {
                                matchedLabelIds.add(label.id)
                            } else {
                                val newLabel = Label(name = name)
                                val newId = snippetRepo.insertLabelDirect(newLabel)
                                matchedLabelIds.add(newId.toInt())
                                labelMap[name] = newLabel.copy(id = newId.toInt())
                            }
                        }

                        matchedLabelIds.forEach { labelId ->
                            snippetRepo.insertSnippetLabelRelation(SnippetLabelRelation(snippet.id, labelId))
                        }

                        refreshSnippets()
                    }
                },
                onError = { error ->
                    Log.e("Duckling", "Parse error: ${error.message}")
                    viewModelScope.launch {
                        val existingLabels = snippetRepo.getAllLabelsDirect().toMutableList()
                        val labelMap = existingLabels.associateBy { it.name }.toMutableMap()

                        val matchedLabelIds = mutableListOf<Int>()
                        for (name in suggestedLabels) {
                            val label = labelMap[name]
                            if (label != null) {
                                matchedLabelIds.add(label.id)
                            } else {
                                val newLabel = Label(name = name)
                                val newId = snippetRepo.insertLabelDirect(newLabel)
                                matchedLabelIds.add(newId.toInt())
                                labelMap[name] = newLabel.copy(id = newId.toInt())
                            }
                        }

                        matchedLabelIds.forEach { labelId ->
                            snippetRepo.insertSnippetLabelRelation(
                                SnippetLabelRelation(
                                    snippet.id,
                                    labelId
                                )
                            )
                        }

                        refreshSnippets()
                    }
                }
            )
        }
    }

    suspend fun doesSnippetExist(text: String): Boolean {
        val existing = snippetRepo.getSnippetByText(text.trim())
        return existing != null
    }

    fun insertSnippetWithLabels(text: String, timestamp: Long, labels: List<String>) {
        viewModelScope.launch {
            ActionUtils.clearActionCache()
            val snippet = Snippet(text = text.trim(), timestamp = timestamp)
            val snippetId = snippetRepo.insert(snippet).toInt()
            val existingLabels = snippetRepo.getAllLabelsDirect()
            val labelMap = existingLabels.associateBy { it.name }.toMutableMap()
            val labelIds = mutableListOf<Int>()
            for (name in labels.distinct()) {
                val existingLabel = labelMap[name]
                val labelId = if (existingLabel != null) {
                    existingLabel.id
                } else {
                    val newId = snippetRepo.insertLabelDirect(Label(name = name))
                    labelMap[name] = Label(id = newId.toInt(), name = name)
                    newId.toInt()
                }
                labelIds.add(labelId)
            }
            labelIds.forEach { labelId ->
                snippetRepo.insertSnippetLabelRelation(SnippetLabelRelation(snippetId, labelId))
            }
            refreshSnippets()
        }
    }

    fun deleteSnippet(snippet: Snippet) = viewModelScope.launch {
        snippetRepo.deleteSnippet(snippet)
        val binItem = Bin(
            originalId = snippet.id,
            text = snippet.text,
            timestamp = snippet.timestamp,
            deletedAt = System.currentTimeMillis()
        )
        binRepo.insert(binItem)
        refreshSnippets()
    }

    fun restoreSnippetWithLabels(snippet: Snippet, labelIds: List<Int>) {
        viewModelScope.launch {
            ActionUtils.clearActionCache()
            binRepo.deleteBySnippetId(snippet.id)
            snippetRepo.insert(snippet)
            snippetRepo.clearLabelsForSnippet(snippet.id)
            labelIds.forEach { id ->
                snippetRepo.insertSnippetLabelRelation(SnippetLabelRelation(snippet.id, id))
            }
            refreshSnippets()
        }
    }

    fun updateSnippet(snippet: Snippet) = viewModelScope.launch {
        snippetRepo.updateSnippet(snippet)
        refreshSnippets()
    }

    fun updatePinStatus(id: Int, isPinned: Boolean) = viewModelScope.launch {
        snippetRepo.updatePinStatus(id, isPinned)
        refreshSnippets()
    }

    suspend fun getLabelsForSnippet(snippetId: Int): List<Label> {
        return snippetRepo.getSnippetWithLabelsDirect(snippetId).labels
    }

    fun insertLabel(label: Label, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                snippetRepo.insertLabel(label)
            }
            onComplete(id)
            refreshSnippets()
        }
    }

    fun updateLabel(updatedLabel: Label) {
        viewModelScope.launch {
            snippetRepo.updateLabel(updatedLabel)
            refreshSnippets()
        }
    }

    fun assignLabelsToSnippet(snippetId: Int, selectedLabelIds: Set<Int>) {
        viewModelScope.launch {
            snippetRepo.clearLabelsForSnippet(snippetId)
            selectedLabelIds.forEach { id ->
                snippetRepo.insertSnippetLabelRelation(SnippetLabelRelation(snippetId, id))
            }
            refreshSnippets()
        }
    }

    fun deleteLabel(label: Label) {
        viewModelScope.launch {
            snippetRepo.deleteRelationsByLabelId(label.id)
            snippetRepo.deleteLabel(label)
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

    fun trackAccess(snippetId: Int) {
        viewModelScope.launch {
            snippetRepo.incrementAccess(snippetId, System.currentTimeMillis())
        }
    }

    fun getSnippetsForCleanup(): List<SnippetWithLabels> {
        val now = System.currentTimeMillis()
        return snippetsWithLabels.value.orEmpty().filter {
            val ageScore = (now - it.snippet.timestamp) / (1000 * 60 * 60 * 24)
            val useScore = it.snippet.accessCount
            val isPinned = it.snippet.isPinned
            !isPinned && (ageScore > 7 && useScore < 2)
        }
    }

    fun performAutoCleanup(view: View, snippetDays: Int, otpHours: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            var deletedCount = 0

            if (snippetDays > 0) {
                deletedCount += snippetRepo.deleteSnippetsOlderThan(System.currentTimeMillis() - snippetDays * 24 * 60 * 60 * 1000L)
            }

            if (otpHours > 0) {
                deletedCount += snippetRepo.deleteOtpSnippetsOlderThan(System.currentTimeMillis() - otpHours * 60 * 60 * 1000L)
            }

            if (deletedCount > 0) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(view, "Deleted $deletedCount snippets", Snackbar.LENGTH_SHORT).show()
                    refreshSnippets()
                }
            }
        }
    }
}