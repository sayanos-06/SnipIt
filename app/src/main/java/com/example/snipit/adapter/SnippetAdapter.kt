package com.example.snipit.adapter

import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.model.Label
import com.example.snipit.model.Snippet
import com.example.snipit.model.SnippetWithLabels
import com.example.snipit.ui.LabelPickerBottomSheet
import com.example.snipit.ui.MainActivity
import com.example.snipit.ui.SnippetViewModel
import com.example.snipit.utils.TimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar

class SnippetAdapter(
    private val context: Context,
    private val rootView: View
) : ListAdapter<SnippetWithLabels, SnippetAdapter.SnippetViewHolder>(DiffCallback()) {

    var onPinChange: ((Snippet, Boolean) -> Unit)? = null
    var onEditClick: ((Snippet) -> Unit)? = null
    var onDeleteClick: ((Snippet) -> Unit)? = null
    var onSelectionChanged: (() -> Unit)? = null

    private var fullSnippets: List<SnippetWithLabels> = listOf()
    private var snippetsWithLabels: List<SnippetWithLabels> = emptyList()
    private val selectedSnippets = mutableSetOf<Snippet>()

    inner class SnippetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.snippetText)
        val time: TextView = itemView.findViewById(R.id.snippetTime)
        val btnCopy: Button = itemView.findViewById(R.id.btnCopy)
        val pin: CheckBox = itemView.findViewById(R.id.pin)
        val btnExpand: Button = itemView.findViewById(R.id.btnExpand)
        val chipGroup: ChipGroup = itemView.findViewById(R.id.chipGroupLabels)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SnippetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_snippet, parent, false)
        return SnippetViewHolder(view)
    }

    override fun onBindViewHolder(holder: SnippetViewHolder, position: Int) {
        val item = getItem(position)
        val snippet = item.snippet
        val labels = item.labels
        val isSelected = selectedSnippets.contains(snippet)

        holder.itemView.alpha = if (isSelected) 0.5f else 1.0f
        holder.text.text = snippet.text
        holder.time.text = TimeUtils.getRelativeTime(snippet.timestamp, context)

        holder.pin.setOnCheckedChangeListener(null)
        holder.pin.isChecked = snippet.isPinned
        holder.pin.setOnCheckedChangeListener { _, isChecked ->
            if (snippet.isPinned != isChecked) {
                onPinChange?.invoke(snippet, isChecked)
            }
        }

        holder.chipGroup.removeAllViews()

        if (labels.isNotEmpty()) {
            holder.chipGroup.visibility = View.VISIBLE
            labels.forEach { label ->
                val chip = Chip(holder.itemView.context).apply {
                    text = label.name
                    isClickable = false
                    isCheckable = false
                    isChecked = false
                    setOnClickListener(null)
                    setOnCloseIconClickListener(null)
                    tag = null
                }
                holder.chipGroup.addView(chip)
            }
        } else {
            holder.chipGroup.visibility = View.GONE
        }

        holder.btnCopy.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", snippet.text))
            SnippetViewModel.snippetUpdater(context, snippet.text)
            Snackbar.make(rootView, "Copied!", Snackbar.LENGTH_SHORT).show()
        }

        (holder.btnExpand as MaterialButton).setOnClickListener {
            holder.btnExpand.isChecked = true
            val popup = PopupMenu(context, it)
            popup.menuInflater.inflate(R.menu.snippet_menu, popup.menu)
            popup.setForceShowIcon(true)

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_delete -> { onDeleteClick?.invoke(snippet); true }
                    R.id.action_edit -> { onEditClick?.invoke(snippet); true }
                    R.id.action_edit_labels -> {
                        val supportFragmentManager = (context as MainActivity).supportFragmentManager
                        LabelPickerBottomSheet(snippet.id, SnippetViewModel(context.applicationContext as Application))
                            .show(supportFragmentManager, "LabelPicker")
                        true
                    }
                    else -> false
                }
            }

            popup.setOnDismissListener { holder.btnExpand.isChecked = false }
            popup.show()
        }

        holder.itemView.setOnLongClickListener {
            toggleSelectedSnippets(snippet)
            onSelectionChanged?.invoke()
            true
        }

        holder.itemView.setOnClickListener {
            if (selectedSnippets.isNotEmpty()) {
                toggleSelectedSnippets(snippet)
                onSelectionChanged?.invoke()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SnippetWithLabels>() {
        override fun areItemsTheSame(oldItem: SnippetWithLabels, newItem: SnippetWithLabels): Boolean {
            return oldItem.snippet.id == newItem.snippet.id
        }

        override fun areContentsTheSame(oldItem: SnippetWithLabels, newItem: SnippetWithLabels): Boolean {
            val snippetSame = oldItem.snippet == newItem.snippet
            val labelsSame = oldItem.labels.size == newItem.labels.size &&
                    oldItem.labels.all { label -> newItem.labels.any { it.id == label.id && it.name == label.name } }

            return snippetSame && labelsSame
        }
    }

    fun getSelectedSnippets(): List<Snippet> = selectedSnippets.toList()

    fun getSelectedSnippetsWithLabels(): List<SnippetWithLabels> {
        return snippetsWithLabels.filter { selectedSnippets.contains(it.snippet) }
    }

    fun submitSnippetsWithLabels(data: List<SnippetWithLabels>) {
        snippetsWithLabels = data.toList()
        fullSnippets = data.toList()
        submitList(data.toList())
    }

    fun filterSnippetList(query: String) {
        val filtered = if (query.isBlank()) {
            fullSnippets
        } else {
            fullSnippets.filter { it.snippet.text.contains(query, ignoreCase = true) }
        }
        submitList(filtered)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun toggleSelectedSnippets(snippet: Snippet) {
        if (!selectedSnippets.add(snippet)) selectedSnippets.remove(snippet)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAllSnippets() {
        selectedSnippets.clear()
        selectedSnippets.addAll(fullSnippets.map { it.snippet })
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSelectedSnippets(newSelectedSnippets: Set<Snippet>) {
        selectedSnippets.clear()
        selectedSnippets.addAll(newSelectedSnippets)
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelectedSnippets() {
        selectedSnippets.clear()
        notifyDataSetChanged()
    }

    fun getAllSnippetsWithLabels(): List<SnippetWithLabels> = fullSnippets
}
