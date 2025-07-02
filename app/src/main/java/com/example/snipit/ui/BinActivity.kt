package com.example.snipit.ui

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ActionMode
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.adapter.BinAdapter
import com.example.snipit.model.Bin
import com.example.snipit.model.Snippet
import com.example.snipit.viewModels.BinViewModel
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlin.collections.forEach

class BinActivity : AppCompatActivity() {

    private lateinit var binToolbar: MaterialToolbar
    private lateinit var binRecyclerView: RecyclerView
    private lateinit var emptyBinText: TextView
    private lateinit var binAdapter: BinAdapter
    private lateinit var remoteText: TextView
    internal val binViewModel: BinViewModel by viewModels()
    private var actionMode: ActionMode? = null
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val actionBarView = window.decorView.findViewById<View>(androidx.appcompat.R.id.action_mode_bar)
            binToolbar.visibility = View.GONE
            actionBarView.visibility = View.VISIBLE
            menuInflater.inflate(R.menu.bin_multi_select_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            for (i in 0 until menu.size) {
                menu[i].icon?.setTint(ContextCompat.getColor(this@BinActivity, R.color.md_theme_onPrimary))
            }
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete -> {
                    if (binAdapter.getSelectedBinItems().isNotEmpty()) bulkDeleteConfirmationDialog(binAdapter.getSelectedBinItems())
                    else Toast.makeText(this@BinActivity, "Nothing to delete", Toast.LENGTH_SHORT).show()
                    mode.finish()
                    true
                }

                R.id.action_restore -> {
                    if (binAdapter.getSelectedBinItems().isNotEmpty()) bulkRestoreConfirmationDialog(binAdapter.getSelectedBinItems())
                    else Toast.makeText(this@BinActivity, "Nothing to restore", Toast.LENGTH_SHORT).show()
                    mode.finish()
                    true
                }

                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            val actionBarView = window.decorView.findViewById<View>(androidx.appcompat.R.id.action_mode_bar)
            binAdapter.disableSelectionMode()
            binAdapter.clearSelectedBinItems()
            binViewModel.clearSelectedBinItems()
            actionMode = null
            actionBarView.visibility = View.GONE
            binToolbar.visibility = View.VISIBLE
            invalidateOptionsMenu()
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_bin)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.binMain)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = ContextCompat.getColor(this, R.color.md_theme_primary)
        val isDarkTheme = resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        window.insetsController?.setSystemBarsAppearance(
            if (isDarkTheme) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }

        binToolbar = findViewById(R.id.binToolbar)
        binToolbar.title = getString(R.string.recycle_bin)
        binRecyclerView = findViewById(R.id.binRecyclerView)
        emptyBinText = findViewById(R.id.emptyBinText)
        remoteText = findViewById(R.id.textView)

        setSupportActionBar(binToolbar)

        binViewModel.autoDelete()

        binAdapter = BinAdapter(
            onRestore = {
                showPermissionDialog(
                    title = "Restore Snippet",
                    message = "Are you sure you want to restore this snippet?",
                    icon = getDrawable(R.drawable.replay_24px),
                    positiveButton = "Restore",
                    negativeButton = "Cancel",
                    onPositive = {
                        binViewModel.restore(it)
                        Snackbar.make(binRecyclerView, "Snippet restored", Snackbar.LENGTH_SHORT).show()
                    },
                    onNegative = { dialog ->
                        dialog.dismiss()
                    }
                )
            },
            onDelete = { bin ->
                showPermissionDialog(
                    title = "Delete Snippet",
                    message = "Are you sure you want to permanently delete this snippet?",
                    icon = getDrawable(R.drawable.round_delete_24),
                    positiveButton = "Delete",
                    negativeButton = "Cancel",
                    onPositive = {
                        binViewModel.delete(bin)
                        Snackbar.make(binRecyclerView, "Snippet permanently deleted", Snackbar.LENGTH_SHORT)
                            .setAction("Undo") {
                                binViewModel.insert(bin)
                            }
                            .show()
                    },
                    onNegative = { dialog ->
                        dialog.dismiss()
                    }
                )
            }
        )
        binAdapter.onSelectionChanged = {
            val selected = binAdapter.getSelectedBinItems()
            if (selected.isNotEmpty()) {
                multiSelectToolbar(selected.size)
                binViewModel.setSelectedBinItems(selected)
            } else {
                actionMode?.finish()
                invalidateOptionsMenu()
            }
        }

        binRecyclerView.layoutManager = LinearLayoutManager(this)
        binRecyclerView.adapter = binAdapter
        binRecyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 250
            removeDuration = 250
            moveDuration = 200
            changeDuration = 200
        }

        binViewModel.trashedSnippets.observe(this) { data ->
            binAdapter.submitList(data)
            binAdapter.setAllBinItems(data)
            emptyBinText.visibility = if (data.isEmpty()) View.VISIBLE else View.GONE
            remoteText.visibility = if (data.isEmpty()) View.GONE else View.VISIBLE
        }

        binViewModel.selectedBinItems.observe(this) { binItems ->
            if (binItems.isNotEmpty()) {
                binAdapter.setSelectedBinItems(binItems)
                multiSelectToolbar(binItems.size)
            } else {
                actionMode?.finish()
                invalidateOptionsMenu()
            }
        }

        findViewById<MaterialToolbar>(R.id.binToolbar).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.bin_top_bar, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        for (i in 0 until menu.size) {
            val item = menu[i]
            item.icon?.setTint(ContextCompat.getColor(this, R.color.md_theme_onPrimary))
            if (item.title != null) {
                val color = ContextCompat.getColor(this, R.color.md_theme_onPrimary)
                val spannableTitle = SpannableString(item.title).apply {
                    setSpan(ForegroundColorSpan(color), 0, length, 0)
                }
                item.title = spannableTitle
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                if (binAdapter.getAllBinItems().isNotEmpty()) {
                    binAdapter.enableSelectionMode()
                    binAdapter.setSelectedBinItems(emptyList())
                    multiSelectToolbar(0)
                } else {
                    Toast.makeText(this, "Nothing to edit", Toast.LENGTH_SHORT).show()
                }
                true
            }

            R.id.action_more -> {
                val menuView = findViewById<View>(R.id.action_more)
                if (menuView != null) {
                    binTopBarMoreMenu(menuView)
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun binTopBarMoreMenu(anchorView: View) {
        val wrapper = ContextThemeWrapper(this, R.style.Widget_SnipIt_PopupMenu)
        val popupMenu = PopupMenu(wrapper, anchorView)
        popupMenu.menuInflater.inflate(R.menu.bin_top_bar_more_menu, popupMenu.menu)
        popupMenu.setForceShowIcon(true)

        popupMenu.setOnMenuItemClickListener { popupItem ->
            when (popupItem.itemId) {
                R.id.action_empty -> {
                    if (binAdapter.getAllBinItems().isNotEmpty())
                        bulkDeleteConfirmationDialog(binAdapter.getAllBinItems(), true)
                    else
                        Toast.makeText(this, "Nothing to empty", Toast.LENGTH_SHORT).show()
                    true
                }

                R.id.action_restore_all -> {
                    if (binAdapter.getAllBinItems().isNotEmpty())
                        bulkRestoreConfirmationDialog(binAdapter.getAllBinItems(), true)
                    else
                        Toast.makeText(this, "Nothing to restore", Toast.LENGTH_SHORT).show()
                    true
                }

                else -> false
            }
        }
        popupMenu.show()
    }

    private fun multiSelectToolbar(count: Int) {
        if (actionMode == null) {
            actionMode = startActionMode(actionModeCallback)
        }
        actionMode?.title = "$count selected"
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun bulkDeleteConfirmationDialog(binItems: List<Bin>, emptyBin: Boolean = false) {
        showPermissionDialog(
            title = "Delete ${binItems.size} Snippets",
            message = if (!emptyBin) "Are you sure you want to permanently delete the selected snippets?"
            else "Are you sure you want to empty the Recycle Bin?",
            icon = getDrawable(R.drawable.round_delete_24),
            positiveButton = "Delete",
            negativeButton = "Cancel",
            onPositive = {
                lifecycleScope.launch {
                    for (item in binItems) {
                        binViewModel.delete(item)
                    }
                    binAdapter.clearSelectedBinItems()
                    Snackbar.make(
                        binRecyclerView,
                        if (!emptyBin) "${binItems.size} snippets permanently deleted."
                        else "Recycle Bin emptied.",
                        Snackbar.LENGTH_LONG
                    )
                        .setAction("Undo") {
                            lifecycleScope.launch {
                                binItems.forEach { item ->
                                    binViewModel.insert(item)
                                }
                            }
                        }
                        .show()
                }
            },
            onNegative = { dialog -> dialog.dismiss() }
        )
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun bulkRestoreConfirmationDialog(binItems: List<Bin>, restoreAll: Boolean = false) {
        showPermissionDialog(
            title = "Restore ${binItems.size} Snippets",
            message = if (!restoreAll) "Are you sure you want to restore the selected snippets?"
            else "Are you sure you want to restore all snippets from the Recycle Bin?",
            icon = getDrawable(R.drawable.replay_24px),
            positiveButton = "Restore",
            negativeButton = "Cancel",
            onPositive = {
                lifecycleScope.launch {
                    for (item in binItems) {
                        binViewModel.restore(item)
                    }
                    binAdapter.clearSelectedBinItems()
                    Snackbar.make(
                        binRecyclerView,
                        if (!restoreAll) "${binItems.size} snippets has been restore."
                        else "Every snippet has been restored",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            },
            onNegative = { dialog -> dialog.dismiss() }
        )
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun showPermissionDialog(
        title: String,
        message: String,
        icon: Drawable? = null,
        neutralButton: String? = null,
        positiveButton: String,
        negativeButton: String,
        onPositive: () -> Unit,
        onNegative: (dialog: DialogInterface) -> Unit
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setIcon(icon)
            .setNeutralButton(neutralButton) { dialog, _ -> onNegative(dialog) }
            .setPositiveButton(positiveButton) { _, _ -> onPositive() }
            .setNegativeButton(negativeButton) { dialog, _ -> onNegative(dialog) }
            .show()
    }
}