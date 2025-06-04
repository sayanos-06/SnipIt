package com.example.snipit.adapter

import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.ViewGroup.MarginLayoutParams
import android.widget.*
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.drawable.DrawableCompat
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
import com.example.snipit.utils.ActionUtils
import com.example.snipit.utils.TimeUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialSplitButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.marginEnd
import androidx.core.view.updateLayoutParams
import com.example.snipit.utils.ActionUtils.toBitmapDrawable

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
        val splitBtnLayout: MaterialSplitButton = itemView.findViewById(R.id.actionSplitButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SnippetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_snippet, parent, false)
        return SnippetViewHolder(view)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onBindViewHolder(holder: SnippetViewHolder, position: Int) {
        val item = getItem(position)
        val snippet = item.snippet
        val labels = item.labels
        val isSelected = selectedSnippets.contains(snippet)
        val actions = ActionUtils.getSuggestedActions(context, snippet.text)

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

        if(actions.isNotEmpty()) {
            holder.splitBtnLayout.visibility = View.VISIBLE
            val layout = holder.splitBtnLayout
            layout.removeAllViews()
            val size = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 48f, context.resources.displayMetrics
            ).toInt()

            val primary = actions.first()
            val primaryIcon = primary.icon?.mutate()?.toBitmapDrawable(context, size)
            val primaryButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = primary.label
                setPadding(0, 0, 16.dpToPx(context), 0)
                icon = primaryIcon
                iconTint = null
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 44f, context.resources.displayMetrics
                ).toInt()
                setOnClickListener { context.startActivity(primary.intent) }
            }
            layout.addView(primaryButton)

            if (actions.size > 1) {
                val menuButton = MaterialButton(context, null, com.google.android.material.R.attr.materialIconButtonOutlinedStyle).apply {
                    icon = context.getDrawable(com.google.android.material.R.drawable.m3_split_button_chevron_avd)
                    setOnClickListener { button ->
                        val popupMenu = PopupMenu(context, button)
                        popupMenu.setForceShowIcon(true)
                        actions.drop(1).forEachIndexed { index, action ->
                            popupMenu.menu.add(0, index, index, action.label).apply {
                                icon = action.icon
                            }
                        }
                        popupMenu.setOnMenuItemClickListener {
                            context.startActivity(actions[it.itemId + 1].intent)
                            true
                        }
                        popupMenu.setOnDismissListener {
                            (button as MaterialButton).isChecked = false
                        }
                        popupMenu.show()
                    }
                }
                layout.addView(menuButton)
            }
        }
        else {
            holder.splitBtnLayout.visibility = View.GONE
        }

        holder.btnCopy.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Copied Text", snippet.text))
            SnippetViewModel.snippetUpdater(context, snippet.text)
            if (context is MainActivity) {
                context.snippetViewModel.trackAccess(snippet.id)
            }
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
        (context as MainActivity).snippetViewModel.refreshSnippets()
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

    fun Int.dpToPx(context: Context): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics
        ).toInt()
    }
}
