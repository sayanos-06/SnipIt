package com.example.snipit.adapter

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.model.Snippet
import com.example.snipit.utils.TimeUtils
import com.google.android.material.button.MaterialButton

class SnippetAdapter(
    private val context: Context
) : ListAdapter<Snippet, SnippetAdapter.SnippetViewHolder>(DiffCallback()) {

    var onPinChange: ((Snippet, Boolean) -> Unit)? = null
    private var fullSnippets: List<Snippet> = listOf()
    var onDeleteClick: ((Snippet) -> Unit)? = null
    private var selectedSnippets = mutableSetOf<Snippet>()
    var onSelectionChanged: (() -> Unit)? = null

    inner class SnippetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.snippetText)
        val time: TextView = itemView.findViewById(R.id.snippetTime)
        val btnCopy: Button = itemView.findViewById(R.id.btnCopy)
        val pin: CheckBox = itemView.findViewById(R.id.pin)
        val btnExpand: Button = itemView.findViewById(R.id.btnExpand)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SnippetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_snippet, parent, false)
        return SnippetViewHolder(view)
    }

    override fun onBindViewHolder(holder: SnippetViewHolder, position: Int) {
        val snippet = getItem(position)
        val relativeTime = TimeUtils.getRelativeTime(snippet.timestamp, holder.itemView.context)
        val isSelected = selectedSnippets.contains(snippet)
        holder.itemView.alpha = if (isSelected) 0.5f else 1.0f

        holder.text.text = snippet.text
        holder.time.text = relativeTime
        holder.pin.setOnCheckedChangeListener(null)

        holder.pin.isChecked = snippet.isPinned

        holder.pin.setOnCheckedChangeListener { _, isChecked ->
            if (snippet.isPinned != isChecked) {
                onPinChange?.invoke(snippet, isChecked)
            }
        }

        holder.btnCopy.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", snippet.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied again!", Toast.LENGTH_SHORT).show()
        }

        (holder.btnExpand as MaterialButton).setOnClickListener {
            holder.btnExpand.isChecked = true

            val popup = PopupMenu(context, it)
            popup.menuInflater.inflate(R.menu.snippet_menu, popup.menu)

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_delete -> {
                        onDeleteClick?.invoke(snippet)
                        true
                    }
                    else -> false
                }
            }

            popup.setOnDismissListener {
                holder.btnExpand.isChecked = false
            }
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

    class   DiffCallback : DiffUtil.ItemCallback<Snippet>() {
        override fun areItemsTheSame(oldItem: Snippet, newItem: Snippet): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Snippet, newItem: Snippet): Boolean {
            return oldItem == newItem
        }
    }

    fun getAllSnippets(): List<Snippet> = fullSnippets

    fun updateSnippetList(newSnippets: List<Snippet>) {
        fullSnippets = newSnippets
        submitList(newSnippets)
    }

    fun filterSnippetList(query: String) {
        val filtered = if (query.isBlank()) {
            fullSnippets
        } else {
            fullSnippets.filter {
                it.text.contains(query, ignoreCase = true)
            }
        }
        submitList(filtered)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun toggleSelectedSnippets(snippet: Snippet) {
        if (selectedSnippets.contains(snippet)) {
            selectedSnippets.remove(snippet)
        } else {
            selectedSnippets.add(snippet)
        }
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAllSnippets() {
        selectedSnippets = emptyList<Snippet>().toMutableSet()
        selectedSnippets = fullSnippets.toMutableSet()
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setSelectedSnippets(newSelectedSnippets: Set<Snippet>) {
        if((fullSnippets.filter { newSelectedSnippets.contains(it) }) == newSelectedSnippets) return
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

    fun getSelectedSnippets(): List<Snippet> = selectedSnippets.toList()
}