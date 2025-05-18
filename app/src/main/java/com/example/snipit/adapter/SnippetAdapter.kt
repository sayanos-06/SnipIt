package com.example.snipit.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.model.Snippet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SnippetAdapter(
    private var snippets: List<Snippet>,
    private val context: Context
) : RecyclerView.Adapter<SnippetAdapter.SnippetViewHolder>() {

    inner class SnippetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.snippetText)
        val time: TextView = itemView.findViewById(R.id.snippetTime)
        val btnCopy: Button = itemView.findViewById(R.id.btnCopy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SnippetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_snippet, parent, false)
        return SnippetViewHolder(view)
    }

    override fun onBindViewHolder(holder: SnippetViewHolder, position: Int) {
        val snippet = snippets[position]

        holder.text.text = snippet.text
        holder.time.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(snippet.timestamp))

        holder.btnCopy.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", snippet.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied again!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int = snippets.size

    fun updateList(newSnippets: List<Snippet>) {
        snippets = newSnippets
        notifyDataSetChanged()
    }
}