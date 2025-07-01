package com.example.snipit.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.model.Snippet
import com.example.snipit.viewModels.SnippetViewModel

class TraySnippetAdapter(
    private val context: Context
) : ListAdapter<Snippet, TraySnippetAdapter.TrayViewHolder>(DiffCallback()) {

    var onPinChange: ((Snippet, Boolean) -> Unit)? = null
    private var fullTraySnippetList: List<Snippet> = emptyList()

    inner class TrayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val snippetText: TextView = itemView.findViewById(R.id.snippetText)
        val pinCheckBox: CheckBox = itemView.findViewById(R.id.snippetPin)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tray_snippet, parent, false)
        return TrayViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrayViewHolder, position: Int) {
        val snippet = getItem(position)
        holder.snippetText.text = snippet.text
        holder.itemView.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Snippet", snippet.text)
            clipboard.setPrimaryClip(clip)
            SnippetViewModel.snippetUpdater(context, snippet.text)
            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
        }
        holder.pinCheckBox.setOnCheckedChangeListener(null)
        holder.pinCheckBox.isChecked = snippet.isPinned
        holder.pinCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (snippet.isPinned != isChecked) {
                onPinChange?.invoke(snippet, isChecked)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Snippet>() {
        override fun areItemsTheSame(oldItem: Snippet, newItem: Snippet): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Snippet, newItem: Snippet): Boolean {
            return oldItem == newItem
        }
    }

    fun submitSortedList(newList: List<Snippet>) {
        fullTraySnippetList = newList.sortedWith(
            compareByDescending<Snippet> { it.isPinned }
                .thenByDescending { it.timestamp }
        )
        submitList(fullTraySnippetList)
    }
}