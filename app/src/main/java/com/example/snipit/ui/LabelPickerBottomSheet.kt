package com.example.snipit.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.adapter.LabelPickerAdapter
import com.example.snipit.model.Label
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LabelPickerBottomSheet(
    private val snippetId: Int,
    private val snippetViewModel: SnippetViewModel
) : BottomSheetDialogFragment() {

    private lateinit var adapter: LabelPickerAdapter
    private val selectedLabelIds = mutableSetOf<Int>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_label_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.labelRecyclerView)
        val editLabelName = view.findViewById<EditText>(R.id.editLabelName)
        val btnAddLabel = view.findViewById<Button>(R.id.btnAddLabel)

        adapter = LabelPickerAdapter (
            onSelectionChanged = { labelId ->
                if (selectedLabelIds.contains(labelId)) {
                    selectedLabelIds.remove(labelId)
                } else {
                    selectedLabelIds.add(labelId)
                }
            },
            onLabelLongClick = { label ->
                editDeleteLabelDialog(label)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        btnAddLabel.setOnClickListener {
            lifecycleScope.launch {
                val name = editLabelName.text.toString().trim()
                val colorHex = getRandomColorHex()
                if (name.isNotEmpty()) {
                    snippetViewModel.insertLabel(Label(name = name, color = colorHex)) {
                        editLabelName.text.clear()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            snippetViewModel.allLabels.observe(viewLifecycleOwner) { labels ->
                CoroutineScope(Dispatchers.Main).launch {
                    val selected = snippetViewModel.getLabelsForSnippet(snippetId)
                    val selectedIds = selected.map { it.id }.toSet()
                    selectedLabelIds.clear()
                    selectedLabelIds.addAll(selectedIds)
                    adapter.submitLabels(labels, selectedIds)
                }
            }
        }

        view.findViewById<Button>(R.id.btnApplyLabels).setOnClickListener {
            val selectedIds = adapter.getSelectedLabelIds()
            snippetViewModel.assignLabelsToSnippet(snippetId, selectedIds)
            Log.d("LabelPickerBottomSheet", "Assigning them")
            dismiss()
        }
    }

    private fun editDeleteLabelDialog(label: Label) {
        val input = EditText(requireContext()).apply {
            setText(label.name)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit or Delete Label")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                lifecycleScope.launch {
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        snippetViewModel.insertLabel(label.copy(name = newName))
                    }
                }
            }
            .setNegativeButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    snippetViewModel.deleteLabel(label)
                    Snackbar.make(requireView(), "Label deleted", Snackbar.LENGTH_SHORT)
                        .setAction("Undo") {
                            snippetViewModel.insertLabel(label)
                        }
                        .show()
                }
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun getRandomColorHex(): String {
        val colors = listOf("#F44336", "#E91E63", "#9C27B0", "#3F51B5", "#03A9F4", "#009688", "#4CAF50", "#FF9800")
        return colors.random()
    }
}
