package com.example.snipit.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.model.Bin
import com.example.snipit.utils.TimeUtils

class BinAdapter(
    private val onRestore: (Bin) -> Unit,
    private val onDelete: (Bin) -> Unit
) : ListAdapter<Bin, BinAdapter.BinViewHolder>(DiffCallback()) {

    var onSelectionChanged: (() -> Unit)? = null
    private val selectedBinItems = mutableListOf<Bin>()
    private val allBinItems = mutableListOf<Bin>()
    private var selectionMode: Boolean = false

    inner class BinViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val snippetText: TextView = itemView.findViewById(R.id.snippetText)
        val snippetTime: TextView = itemView.findViewById(R.id.snippetTime)
        val btnRestore: Button = itemView.findViewById(R.id.btnRestore)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BinViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bin, parent, false)
        return BinViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: BinViewHolder, position: Int) {
        val item = getItem(position)
        holder.snippetText.text = item.text
        holder.snippetTime.text = "Deleted ${TimeUtils.getRelativeTime(item.deletedAt)}"
        val isSelected = selectedBinItems.contains(item)

        holder.itemView.alpha = if (isSelected) 0.5f else 1.0f
    
        holder.btnRestore.setOnClickListener { onRestore(item) }
        holder.btnDelete.setOnClickListener { onDelete(item) }

        holder.itemView.setOnClickListener {
            if (selectionMode) {
                toggleSelectedBinItem(item, position)
                onSelectionChanged?.invoke()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Bin>() {
        override fun areItemsTheSame(oldItem: Bin, newItem: Bin): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Bin, newItem: Bin): Boolean {
            return oldItem == newItem
        }
    }

    fun setAllBinItems(newAllBinItems: List<Bin>) {
        allBinItems.clear()
        allBinItems.addAll(newAllBinItems)
    }

    fun getAllBinItems(): List<Bin> = allBinItems.toList()

    fun getSelectedBinItems(): List<Bin> = selectedBinItems.toList()

    @SuppressLint("NotifyDataSetChanged")
    private fun toggleSelectedBinItem(binItem: Bin, position: Int) {
        if (selectedBinItems.contains(binItem)) {
            selectedBinItems.remove(binItem)
        } else {
            selectedBinItems.add(binItem)
        }
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSelectedBinItems(newSelectedBinItems: List<Bin>) {
        selectedBinItems.clear()
        selectedBinItems.addAll(newSelectedBinItems)
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelectedBinItems() {
        selectedBinItems.clear()
        notifyDataSetChanged()
    }

    fun enableSelectionMode() {
        selectionMode = true
    }

    fun disableSelectionMode() {
        selectionMode = false
        clearSelectedBinItems()
    }
}