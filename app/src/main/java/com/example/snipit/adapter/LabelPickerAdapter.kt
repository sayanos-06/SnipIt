package com.example.snipit.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.model.Label

class LabelPickerAdapter(
    private val onSelectionChanged: (Int) -> Unit,
    private val onLabelLongClick: (Label) -> Unit
) : ListAdapter<Label, LabelPickerAdapter.LabelViewHolder>(LabelDiffCallback()) {

    private val selectedIds = mutableSetOf<Int>()

    inner class LabelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.labelCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LabelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_label_checkbox, parent, false)
        return LabelViewHolder(view)
    }

    override fun onBindViewHolder(holder: LabelViewHolder, position: Int) {
        val label = getItem(position)
        holder.checkBox.text = label.name
        holder.checkBox.isChecked = selectedIds.contains(label.id)

        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedIds.add(label.id) else selectedIds.remove(label.id)
            onSelectionChanged(label.id)
        }

        holder.itemView.setOnLongClickListener {
            onLabelLongClick(label)
            true
        }
    }

    class LabelDiffCallback : DiffUtil.ItemCallback<Label>() {
        override fun areItemsTheSame(oldItem: Label, newItem: Label): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Label, newItem: Label): Boolean = oldItem == newItem
    }

    fun submitLabels(newLabels: List<Label>, preselected: Set<Int>) {
        selectedIds.clear()
        selectedIds.addAll(preselected)
        submitList(newLabels.toList())
    }

    fun getSelectedLabelIds(): Set<Int> = selectedIds.toSet()
}