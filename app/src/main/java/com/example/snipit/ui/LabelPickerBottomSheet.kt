package com.example.snipit.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.adapter.LabelPickerAdapter
import com.example.snipit.model.Label
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        val editLabelName = view.findViewById<TextInputEditText>(R.id.editLabelName)
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
                        editLabelName.text?.clear()
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
        val textInputView = View.inflate(this.context, R.layout.text_input, null)
        val input = textInputView.findViewById<TextInputEditText>(R.id.textInput)
        input?.setText(label.name)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit or Delete Tag")
            .setView(textInputView)
            .setPositiveButton("Rename") { _, _ ->
                lifecycleScope.launch {

                    val newName = input?.text.toString().trim()
                    if (newName.isNotEmpty()) {
                        snippetViewModel.updateLabel(label.copy(name = newName))
                    }
                }
            }
            .setNegativeButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    snippetViewModel.deleteLabel(label)
                    Snackbar.make(requireView(), "Tag deleted", Snackbar.LENGTH_SHORT)
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
