package com.example.snipit.ui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.ActionMode
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.adapter.SnippetAdapter
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import com.example.snipit.model.Snippet
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import androidx.core.view.get
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.view.size
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import com.example.snipit.model.SnippetWithLabels
import androidx.core.view.isNotEmpty
import androidx.work.WorkManager
import com.example.snipit.utils.SyncScheduler
import com.example.snipit.viewModels.SnippetViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: SnippetAdapter
    private lateinit var recyclerView: RecyclerView
    internal val snippetViewModel: SnippetViewModel by viewModels()
    private lateinit var toolbar: MaterialToolbar
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var chipGroupActiveFilters: ChipGroup
    private lateinit var emptyStateText: TextView

    private var actionMode: ActionMode? = null
    private var selectedSnippets: List<SnippetWithLabels> = emptyList()
    private var fullList: List<SnippetWithLabels> = emptyList()
    private var pendingExportFormat: ExportFormat? = null
    internal var currentSearchQuery: String = ""

    enum class ExportFormat { TXT, JSON, CSV }

    private val uriPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.data?.let { treeUri ->
                when (pendingExportFormat) {
                    ExportFormat.TXT -> saveAsTxt(treeUri, selectedSnippets)
                    ExportFormat.JSON -> saveAsJson(treeUri, selectedSnippets)
                    ExportFormat.CSV -> saveAsCsv(treeUri, selectedSnippets)
                    else -> {}
                }
                pendingExportFormat = null
                selectedSnippets = emptyList()
            }
        }

    private val importPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importSnippetsFromFile(it) }
        }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val actionBarView = window.decorView.findViewById<View>(androidx.appcompat.R.id.action_mode_bar)
            toolbar.visibility = View.GONE
            actionBarView.visibility = View.VISIBLE
            menuInflater.inflate(R.menu.multi_select_menu, menu)
            selectAllCheckbox.visibility = View.VISIBLE
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            for (i in 0 until menu.size) {
                menu[i].icon?.setTint(ContextCompat.getColor(this@MainActivity, R.color.md_theme_onPrimary))
            }
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete -> {
                    if (adapter.getSelectedSnippets().isNotEmpty()) bulkDeleteConfirmationDialog(adapter.getSelectedSnippets())
                    else Snackbar.make(recyclerView, "Nothing to delete", Snackbar.LENGTH_SHORT).show()
                    mode.finish()
                    true
                }

                R.id.action_pin -> {
                    if (adapter.getSelectedSnippets().isNotEmpty()) {
                        adapter.getSelectedSnippets().forEach {
                            snippetViewModel.updatePinStatus(it.id, true)
                        }
                    } else Snackbar.make(recyclerView, "Nothing to pin", Snackbar.LENGTH_SHORT).show()
                    mode.finish()
                    true
                }

                R.id.action_more -> {
                    findViewById<View>(R.id.action_more)?.let { multiSelectMoreMenu(it) }
                    true
                }

                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            val actionBarView = window.decorView.findViewById<View>(androidx.appcompat.R.id.action_mode_bar)
            adapter.disableSelectionMode()
            adapter.clearSelectedSnippets()
            snippetViewModel.clearPreviousSelectedSnippets()
            snippetViewModel.clearSelectedSnippets()
            actionMode = null
            selectedSnippets = emptyList()
            selectAllCheckbox.isChecked = false
            actionBarView.visibility = View.GONE
            selectAllCheckbox.visibility = View.GONE
            chipGroupFilter.visibility = View.VISIBLE
            toolbar.visibility = View.VISIBLE
            invalidateOptionsMenu()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("ImplicitSamInstance", "SetTextI18n")
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appExclusionMain)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
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

        toolbar = findViewById(R.id.toolbar)
        selectAllCheckbox = findViewById(R.id.selectAllCheckBox)
        recyclerView = findViewById(R.id.snippetRecyclerView)
        chipGroupFilter = findViewById(R.id.chipGroupFilter)
        chipGroupActiveFilters = findViewById(R.id.chipGroupActiveFilters)
        emptyStateText = findViewById(R.id.emptyStateText)

        setSupportActionBar(toolbar)

        adapter = SnippetAdapter(this, findViewById(android.R.id.content))
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 250
            removeDuration = 250
            moveDuration = 200
            changeDuration = 200
        }

        checkAndRequestPermissions()
        createNotificationChannel(this)
        checkClipboardAndSave()
        setupObservers()
        setupAdapterListeners()
        snippetViewModel.refreshSnippets()
        performAutoCleanup()

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            ensureDriveBackupScheduled(this)
        }
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({
            checkClipboardAndSave()
        }, 1000)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (actionMode != null) return false
        menuInflater.inflate(R.menu.top_app_bar, menu)
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
                adapter.enableSelectionMode()
                adapter.setSelectedSnippets(emptySet())
                multiSelectToolbar(0)
                true
            }

            R.id.action_search -> {
                val searchView = item.actionView as? SearchView

                val searchEditText =
                    searchView?.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
                searchEditText?.setHintTextColor(getColor(R.color.md_theme_outline))
                searchEditText?.setTextColor(getColor(R.color.md_theme_onPrimary))

                val closeButton =
                    searchView?.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
                closeButton?.setColorFilter(
                    getColor(R.color.md_theme_onPrimary),
                    PorterDuff.Mode.SRC_IN
                )

                searchView?.queryHint = "Search snippets..."
                searchView?.isSubmitButtonEnabled = false

                searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        currentSearchQuery = newText.orEmpty()
                        submitList()
                        adapter.refreshVisibleItems()
                        return true
                    }
                })
                true
            }

            R.id.action_more -> {
                val menuView = findViewById<View>(R.id.action_more)
                if (menuView != null) {
                    topBarMoreMenu(menuView)
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupObservers() {

        snippetViewModel.snippetsWithLabels.observe(this) { data ->
            fullList = data
            if (data.isNullOrEmpty()) {
                recyclerView.visibility = View.GONE
                chipGroupFilter.visibility = View.GONE
                emptyStateText.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyStateText.visibility = View.GONE
                submitList()
            }
        }

        snippetViewModel.selectedSnippets.observe(this) { snippets ->
            if (snippets.isNotEmpty()) {
                adapter.setSelectedSnippets(snippets)
                multiSelectToolbar(snippets.size)
            } else {
                actionMode?.finish()
            }
        }

        snippetViewModel.allLabels.observe(this) { labels ->
            chipGroupFilter.removeAllViews()

            val allChip = createFilterChip("All") {
                snippetViewModel.setLabelFilter(null)
                snippetViewModel.setPinnedFilter(null)
                chipGroupActiveFilters.visibility = View.GONE
                submitList()
            }
            allChip.isChecked = true
            chipGroupFilter.addView(allChip)

            chipGroupFilter.addView(createFilterChip("Pinned") {
                snippetViewModel.setLabelFilter(null)
                snippetViewModel.setPinnedFilter(true)
                submitList()
            })

            chipGroupFilter.addView(createFilterChip("Unpinned") {
                snippetViewModel.setLabelFilter(null)
                snippetViewModel.setPinnedFilter(false)
                submitList()
            })

            labels.forEach { label ->
                chipGroupFilter.addView(createFilterChip(label.name) {
                    snippetViewModel.setLabelFilter(label.id)
                    snippetViewModel.setPinnedFilter(null)
                    submitList()
                })
            }
        }

        snippetViewModel.currentLabelFilterId.observe(this) {
            submitList()
        }

        snippetViewModel.pinnedFilter.observe(this) {
            submitList()
        }

        selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (snippetViewModel.previousSelectedSnippets.value.isNullOrEmpty()) {
                    val currentSelection = adapter.getSelectedSnippets().toSet()
                    snippetViewModel.setPreviousSelectedSnippets(currentSelection)
                }
                adapter.selectAllSnippets()
                snippetViewModel.setSelectedSnippets(adapter.getSelectedSnippets().toSet())
            } else {
                val currentSelectionSize = adapter.getSelectedSnippets().size
                if (currentSelectionSize == adapter.currentList.size) {
                    val previous = snippetViewModel.previousSelectedSnippets.value
                    if (!previous.isNullOrEmpty()) {
                        snippetViewModel.setSelectedSnippets(previous)
                    } else {
                        adapter.clearSelectedSnippets()
                        snippetViewModel.setSelectedSnippets(emptySet())
                    }
                    snippetViewModel.clearPreviousSelectedSnippets()
                } else {
                    snippetViewModel.clearPreviousSelectedSnippets()
                    snippetViewModel.setSelectedSnippets(adapter.getSelectedSnippets().toSet())
                }
            }
        }
    }

    private fun setupAdapterListeners() {
        adapter.onPinChange = { snippet, isPinned ->
            snippetViewModel.updatePinStatus(snippet.id, isPinned)
        }

        adapter.onDeleteClick = { snippet ->
            deleteConfirmationDialog(snippet)
        }

        adapter.onEditClick = { snippet ->
            snippetEditDialog(snippet) { newText ->
                val updated = snippet.copy(text = newText, timestamp = System.currentTimeMillis())
                snippetViewModel.updateSnippet(updated)
            }
        }

        adapter.onSelectionChanged = {
            val count = adapter.getSelectedSnippets().size
            if (count > 0) {
                updateSelectAllCheckbox()
                multiSelectToolbar(count)
                snippetViewModel.setSelectedSnippets(adapter.getSelectedSnippets().toSet())
                chipGroupFilter.visibility = View.GONE
                chipGroupActiveFilters.removeAllViews()
                chipGroupActiveFilters.visibility = View.GONE
            } else {
                actionMode?.finish()
                invalidateOptionsMenu()
                chipGroupFilter.visibility = View.VISIBLE
            }
        }
    }

    private fun createFilterChip(text: String, onClick: () -> Unit): Chip {
        return Chip(this).apply {
            this.text = text
            isCheckable = true
            setOnClickListener { onClick() }
        }
    }

    private fun submitList() {
        lifecycleScope.launch {
            var filtered = fullList.filter { item ->
                val matchesLabel = snippetViewModel.currentLabelFilterId.value?.let { labelId ->
                    item.labels.any { it.id == labelId }
                } != false

                val matchesPin = snippetViewModel.pinnedFilter.value?.let { pinned ->
                    item.snippet.isPinned == pinned
                } != false

                matchesLabel && matchesPin
            }

            snippetViewModel.currentLabelFilterId.value?.let { labelId ->
                val labelName = snippetViewModel.allLabels.value?.find { it.id == labelId }?.name ?: return@let
                addOrUpdateActiveChip("filter_label_$labelId", labelName) {
                    snippetViewModel.setLabelFilter(null)
                }
            }

            snippetViewModel.pinnedFilter.value?.let { pinned ->
                val pinLabel = if (pinned) "Pinned" else "Unpinned"
                addOrUpdateActiveChip("filter_pin_$pinned", pinLabel) {
                    snippetViewModel.setPinnedFilter(null)
                }
            }

            if (currentSearchQuery.isNotBlank()) {
                filtered = filtered.filter {
                    val matchesText = it.snippet.text.contains(currentSearchQuery, ignoreCase = true)
                    val matchesLabel = it.labels.any { label ->
                        label.name.contains(currentSearchQuery, ignoreCase = true)
                    }
                    matchesText || matchesLabel
                }
            }

            chipGroupActiveFilters.visibility =
                if (chipGroupActiveFilters.isNotEmpty()) View.VISIBLE else View.GONE

            if (snippetViewModel.currentLabelFilterId.value != null && snippetViewModel.pinnedFilter.value != null) filtered =
                fullList

            adapter.submitSnippetsWithLabels(filtered)
        }
    }

    private fun updateSelectAllCheckbox() {
        val total = adapter.currentList.size
        val selected = adapter.getSelectedSnippets().size
        selectAllCheckbox.isChecked = total > 0 && selected == total
    }

    private fun addOrUpdateActiveChip(tag: String, label: String, onClose: () -> Unit) {
        if (chipGroupActiveFilters.findViewWithTag<Chip>(tag) != null) return

        chipGroupActiveFilters.removeAllViews()
        val chip = Chip(this).apply {
            this.tag = tag
            text = label
            isCloseIconVisible = true
            isCheckable = false
            isClickable = true
            isFocusable = true
            setEnsureMinTouchTargetSize(false)
            setOnCloseIconClickListener {
                onClose()
                chipGroupActiveFilters.removeView(this)
                selectAllChipInGroup()
                submitList()
            }
        }

        chipGroupActiveFilters.addView(chip)
        chipGroupActiveFilters.visibility = View.VISIBLE
    }

    private fun selectAllChipInGroup() {
        for (i in 0 until chipGroupFilter.childCount) {
            val chip = chipGroupFilter.getChildAt(i)
            if (chip is Chip) {
                chip.isChecked = chip.text.toString().equals("All", ignoreCase = true)
            }
        }
    }

    private fun checkClipboardAndSave() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (!prefs.getBoolean("snipit_service_enabled", true)) return

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val copiedText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        Log.d("MainActivity", clipboard.primaryClipDescription.toString())

        val lastSaved = getSharedPreferences("snipit_prefs", MODE_PRIVATE)
            .getString("last_clipboard_text", "")

        if (!copiedText.isNullOrEmpty() && copiedText != lastSaved) {
            snippetViewModel.insertOrUpdateSnippet(copiedText)
            getSharedPreferences("snipit_prefs", MODE_PRIVATE)
                .edit { putString("last_clipboard_text", copiedText) }
        }
    }

    private fun performAutoCleanup() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        prefs.registerOnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "cleanup_snippet_days" || key == "cleanup_otp_hours") {
                val snippetDays = sharedPreferences.getInt("cleanup_snippet_days", -1)
                val otpHours = sharedPreferences.getInt("cleanup_otp_hours", -1)
                snippetViewModel.performAutoCleanup(this.findViewById(R.id.appExclusionMain), snippetDays, otpHours)
            }
        }

        val snippetDays = prefs.getInt("cleanup_snippet_days", -1)
        val otpHours = prefs.getInt("cleanup_otp_hours", -1)
        snippetViewModel.performAutoCleanup(this.findViewById(R.id.appExclusionMain), snippetDays, otpHours)

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

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun checkAndRequestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                title = "Allow Floating Icon",
                message = "SnipIt uses a floating icon to access clipboard. Please allow overlay permission.",
                icon = getDrawable(R.drawable.layers_24px),
                neutralButton = "Cancel",
                positiveButton = "Allow",
                negativeButton = "Decline",
                onPositive = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:$packageName".toUri()
                    )
                    startActivity(intent)
                },
                onNegative = { dialog ->
                    dialog.dismiss()
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Are you sure?")
                        .setMessage("You will have to open the app everytime to confirm the storing of the copied snippet.")
                        .setNeutralButton("Cancel") { newDialog, _ ->
                            newDialog.dismiss()
                        }
                        .setPositiveButton("Yes") { newDialog, _ ->
                            newDialog.dismiss()
                        }
                        .setNegativeButton("No") { _, _ -> checkAndRequestPermissions() }
                        .show()
                }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    fun Context.snippetEditDialog(
        snippet: Snippet,
        onSave: (String) -> Unit
    ) {
        val textInputView = View.inflate(this, R.layout.text_input, null)
        val input = textInputView.findViewById<TextInputEditText>(R.id.textInput)
        input.setText(snippet.text)
        input.setSelection(input.text?.length ?: 0)
        input.setHint("Edit snippet...")

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Snippet")
            .setView(textInputView)
            .setPositiveButton("Save") { _, _ ->
                val newText = input.text.toString().trim()
                if (newText.isNotBlank()) onSave(newText)
            }
            .setNegativeButton("Discard", null)
            .show()
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun deleteConfirmationDialog(snippet: Snippet) {
        showPermissionDialog(
            title = "Delete Snippet",
            message = "Are you sure you want to move this snippet to trash?",
            icon = getDrawable(R.drawable.round_delete_24),
            positiveButton = "Delete",
            negativeButton = "Cancel",
            onPositive = {
                val snippetToDelete = snippet
                lifecycleScope.launch {
                    val labelIds = snippetViewModel.getLabelsForSnippet(snippet.id).map { it.id }
                    snippetViewModel.deleteSnippet(snippetToDelete)
                    Snackbar.make(recyclerView, "Moved to bin.", Snackbar.LENGTH_LONG)
                        .setAction("Undo") {
                            snippetViewModel.restoreSnippetWithLabels(snippetToDelete, labelIds)
                        }
                        .show()
                }

            },
            onNegative = { dialog -> dialog.dismiss() }
        )
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun bulkDeleteConfirmationDialog(snippets: List<Snippet>) {
        showPermissionDialog(
            title = "Delete ${snippets.size} Snippets",
            message = "Are you sure you want to move the selected snippets to bin?",
            icon = getDrawable(R.drawable.round_delete_24),
            positiveButton = "Delete",
            negativeButton = "Cancel",
            onPositive = {
                lifecycleScope.launch {
                    val labelMap = mutableMapOf<Int, List<Int>>()
                    for (snippet in snippets) {
                        val labelIds =
                            snippetViewModel.getLabelsForSnippet(snippet.id).map { it.id }
                        labelMap[snippet.id] = labelIds
                        snippetViewModel.deleteSnippet(snippet)
                    }
                    adapter.clearSelectedSnippets()
                    Snackbar.make(
                        recyclerView,
                        "${snippets.size} snippets moved to bin.",
                        Snackbar.LENGTH_LONG
                    )
                        .setAction("Undo") {
                            lifecycleScope.launch {
                                snippets.forEach { snippet ->
                                    val labelIds = labelMap[snippet.id] ?: emptyList()
                                    snippetViewModel.restoreSnippetWithLabels(snippet, labelIds)
                                }
                            }
                        }
                        .show()
                }
            },
            onNegative = { dialog -> dialog.dismiss() }
        )
    }

    private fun topBarMoreMenu(anchorView: View) {
        val wrapper = ContextThemeWrapper(this, R.style.Widget_SnipIt_PopupMenu)
        val popupMenu = PopupMenu(wrapper, anchorView)
        popupMenu.menuInflater.inflate(R.menu.top_bar_more_menu, popupMenu.menu)
        popupMenu.setForceShowIcon(true)

        popupMenu.setOnMenuItemClickListener { popupItem ->
            when (popupItem.itemId) {
                R.id.action_save_as -> {
                    selectedSnippets = adapter.getAllSnippetsWithLabels()
                    saveShareBottomSheet(isShare = false, selectedSnippets)
                    true
                }

                R.id.action_import -> {
                    showPermissionDialog(
                        title = "Import Snippets",
                        message = "Snippets will only be imported from files exported by SnipIt.",
                        positiveButton = "Import",
                        negativeButton = "Cancel",
                        onPositive = {
                            importPickerLauncher.launch(
                                arrayOf(
                                    "text/*",
                                    "application/json",
                                    "text/csv"
                                )
                            )
                        },
                        onNegative = { dialog -> dialog.dismiss() }
                    )
                    true
                }

                R.id.action_suggest_cleanup -> {
                    val toSuggest = snippetViewModel.getSnippetsForCleanup()
                    if (toSuggest.isEmpty()) {
                        Snackbar.make(this.findViewById(R.id.appExclusionMain), "No old snippets to clean!", Snackbar.LENGTH_SHORT).show()
                    } else {
                        val texts = toSuggest.joinToString("\n\n") { it.snippet.text.take(100) }

                        showPermissionDialog(
                            title = "Suggest Cleanup",
                            message = "These ${toSuggest.size} snippets seem old or unused:\n\n$texts",
                            positiveButton = "Delete All",
                            negativeButton = "Cancel",
                            onPositive = {
                                lifecycleScope.launch {
                                    toSuggest.forEach {
                                        snippetViewModel.deleteSnippet(it.snippet)
                                    }
                                    Snackbar.make(recyclerView, "Deleted ${toSuggest.size} snippets", Snackbar.LENGTH_LONG)
                                        .setAction("Undo") {
                                            toSuggest.forEach {
                                                val labelIds = it.labels.map { l -> l.id }
                                                snippetViewModel.restoreSnippetWithLabels(it.snippet, labelIds)
                                            }
                                        }
                                        .show()
                                }
                            },
                            onNegative = { dialog -> dialog.dismiss() }
                        )
                    }
                    true
                }

                R.id.action_bin -> {
                    startActivity(Intent(this, BinActivity::class.java))
                    true
                }

                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
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
        chipGroupFilter.visibility = View.GONE
        chipGroupActiveFilters.visibility = View.GONE
        actionMode?.title = "$count selected"
    }

    private fun multiSelectMoreMenu(anchorView: View) {
        val wrapper = ContextThemeWrapper(this, R.style.Widget_SnipIt_PopupMenu)
        val popupMenu = PopupMenu(wrapper, anchorView)
        popupMenu.menuInflater.inflate(R.menu.multi_select_more_menu, popupMenu.menu)
        popupMenu.setForceShowIcon(true)

        popupMenu.setOnMenuItemClickListener { popupItem ->
            selectedSnippets = adapter.getSelectedSnippetsWithLabels()
            when (popupItem.itemId) {
                R.id.action_share -> {
                    if (selectedSnippets.isNotEmpty()) saveShareBottomSheet(isShare = true, selectedSnippets)
                    else Snackbar.make(recyclerView, "Nothing to share", Snackbar.LENGTH_SHORT).show()
                    true
                }

                R.id.action_save_as -> {
                    if (selectedSnippets.isNotEmpty()) saveShareBottomSheet(isShare = false, selectedSnippets)
                    else Snackbar.make(recyclerView, "Nothing to save", Snackbar.LENGTH_SHORT).show()
                    true
                }

                else -> false
            }
        }

        popupMenu.show()
    }

    @SuppressLint("SetTextI18n")
    private fun saveShareBottomSheet(isShare: Boolean, snippetsWithLabels: List<SnippetWithLabels>) {
        val bottomSheet = SaveShareBottomSheet(isShare) { option ->
            when (option) {
                SaveShareBottomSheet.OptionType.SHARE_TEXT -> {
                    if (isShare) shareAsText(snippetsWithLabels) else saveInFolder(ExportFormat.TXT)
                }

                SaveShareBottomSheet.OptionType.SHARE_JSON -> {
                    if (isShare) shareAsJson(snippetsWithLabels) else saveInFolder(ExportFormat.JSON)
                }

                SaveShareBottomSheet.OptionType.SHARE_CSV -> {
                    if (isShare) shareAsCsv(snippetsWithLabels) else {
                        saveInFolder(ExportFormat.CSV)
                    }
                }
            }
        }
        bottomSheet.show(supportFragmentManager, bottomSheet.tag)
    }

    private fun saveInFolder(format: ExportFormat) {
        pendingExportFormat = format
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        uriPicker.launch(intent)
    }

    private fun saveAsTxt(treeUri: Uri, selectedSnippets: List<SnippetWithLabels>) {
        if (selectedSnippets.isEmpty()) return

        contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val fileName = "snippets_${System.currentTimeMillis()}"
        val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val docUri = DocumentsContract.createDocument(
            contentResolver,
            docTreeUri,
            "text/plain",
            fileName
        )

        if (docUri != null) {
            val outputStream = contentResolver.openOutputStream(docUri)
            outputStream?.bufferedWriter().use { writer ->
                var saveData = selectedSnippets.joinToString("\n") {
                    var labelNames = ""
                    it.labels.forEach {
                        labelNames += "${it.name};"
                    }
                    "---SNIPPET START---\nText: ${it.snippet.text}\nTimestamp: ${it.snippet.timestamp}\nLabels: $labelNames\n---SNIPPET END---\n"
                }
                saveData = saveData.substring(0, saveData.length - 1)
                writer?.write(saveData)
            }

            Snackbar.make(this.findViewById(R.id.appExclusionMain), "Exported to $fileName.txt", Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(this.findViewById(R.id.appExclusionMain), "Failed to create file", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun saveAsJson(treeUri: Uri, selectedSnippets: List<SnippetWithLabels>) {
        if (selectedSnippets.isEmpty()) return

        contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val jsonArray = JSONArray()
        for (snippetWithLabels in selectedSnippets) {
            val snippet = snippetWithLabels.snippet
            val snippetObj = JSONObject().apply {
                put("text", snippet.text)
                put("timestamp", snippet.timestamp)
                put("labels", JSONArray(snippetWithLabels.labels.map { it.name }))
            }
            jsonArray.put(snippetObj)
        }

        val jsonString = jsonArray.toString(4)
        val fileName = "snippets_${System.currentTimeMillis()}"
        val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val docUri = DocumentsContract.createDocument(
            contentResolver,
            docTreeUri,
            "application/json",
            fileName
        )

        if (docUri != null) {
            val outputStream = contentResolver.openOutputStream(docUri)
            outputStream?.bufferedWriter().use { writer ->
                writer?.write(jsonString)
            }
            Snackbar.make(this.findViewById(R.id.appExclusionMain), "Exported to $fileName.json", Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(this.findViewById(R.id.appExclusionMain), "Failed to create file", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun saveAsCsv(treeUri: Uri, selectedSnippets: List<SnippetWithLabels>) {
        if (selectedSnippets.isEmpty()) return

        contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val fileName = "snippets_${System.currentTimeMillis()}"
        val docTreeUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

        val docUri = DocumentsContract.createDocument(
            contentResolver,
            docTreeUri,
            "text/csv",
            fileName
        )

        if (docUri != null) {
            try {
                contentResolver.openOutputStream(docUri)?.use { outputStream ->
                    val writer = BufferedWriter(OutputStreamWriter(outputStream))

                    writer.write("Text,Timestamp,Labels")
                    writer.newLine()

                    for (snippetWithLabels in selectedSnippets) {
                        val text = snippetWithLabels.snippet.text.replace("\"", "\"\"")
                        val timestamp = snippetWithLabels.snippet.timestamp
                        val labels = snippetWithLabels.labels
                        var labelNames= ""
                        labels.forEach {
                            labelNames += "${it.name}|"
                        }
                        labelNames = labelNames.substring(0, labelNames.length - 1)

                        val csvLine = "\"$text\",$timestamp,\"$labelNames\""
                        writer.write(csvLine)
                        writer.newLine()
                    }

                    writer.flush()
                    Snackbar.make(this.findViewById(R.id.appExclusionMain), "Snippets exported as CSV.", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Snackbar.make(this.findViewById(R.id.appExclusionMain), "Failed to export as CSV.", Snackbar.LENGTH_SHORT).show()
            }
        } else {
            Snackbar.make(this.findViewById(R.id.appExclusionMain), "Failed to create CSV file.", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun shareAsText(selectedSnippets: List<SnippetWithLabels>) {
        if (selectedSnippets.isEmpty()) return

        val shareData = buildString {
            selectedSnippets.forEachIndexed { index, item ->
                val snippet = item.snippet
                val labels = item.labels.joinToString(", ")
                append("Snippet ${index + 1}: ${snippet.text.trim()} (Saved on: ${formatDate(snippet.timestamp)})")
                if (labels.isNotEmpty()) append(" [Labels: $labels]")
                append("\n\n")
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Shared Snippets")
            putExtra(Intent.EXTRA_TEXT, shareData.trim())
        }

        startActivity(Intent.createChooser(shareIntent, "Share snippets via"))
    }

    private fun shareAsJson(selectedSnippets: List<SnippetWithLabels>) {
        if (selectedSnippets.isEmpty()) return

        val jsonArray = JSONArray()
        val obj = JSONObject()
        obj.put("itemCount", selectedSnippets.size)
        jsonArray.put(obj)
        for (snippetWithLabels in selectedSnippets) {
            val snippet = snippetWithLabels.snippet
            val snippetObj = JSONObject().apply {
                put("text", snippet.text)
                put("timestamp", snippet.timestamp)
                put("labels", JSONArray(snippetWithLabels.labels.map { it.name }))
            }
            jsonArray.put(snippetObj)
        }

        val jsonString = jsonArray.toString(4)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, jsonString)
            putExtra(Intent.EXTRA_SUBJECT, "Snippets JSON Export")
        }

        startActivity(Intent.createChooser(intent, "Share Snippets As JSON"))
    }

    private fun shareAsCsv(selectedSnippets: List<SnippetWithLabels>) {
        if (selectedSnippets.isEmpty()) return

        val shareData = buildString {
            append("Text,Timestamp,Labels\n")
            for (snippetWithLabels in selectedSnippets) {
                val text = snippetWithLabels.snippet.text.replace("\"", "\"\"")
                val timestamp = snippetWithLabels.snippet.timestamp
                val labels = snippetWithLabels.labels.joinToString("|").replace("\"", "\"\"")

                val csvLine = "\"$text\",$timestamp,\"$labels\""
                append("$csvLine\n")
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Shared Snippets")
            putExtra(Intent.EXTRA_TEXT, shareData.trim())
        }

        startActivity(Intent.createChooser(shareIntent, "Share snippets via"))
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun importSnippetsFromFile(uri: Uri) {
        val fileName = uri.lastPathSegment ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle("Import Snippets")
            .setMessage("Are you sure you want to import snippets from:\n$fileName?")
            .setPositiveButton("Import") { _, _ -> performImport(uri, fileName) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performImport(uri: Uri, fileName: String) {
        val contentResolver = contentResolver

        try {
            val inputStream = contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader()?.readText() ?: return

            var importedCount = 0
            lifecycleScope.launch {
                val snippets = when {
                    fileName.endsWith(".json") -> parseJsonSnippets(content)
                    fileName.endsWith(".csv") -> parseCsvSnippets(content)
                    fileName.endsWith(".txt") -> parseTxtSnippets(content)
                    else -> emptyList<Triple<String, Long, List<String>>>()
                }

                for ((text, timestamp, labels) in snippets) {
                    if (!snippetViewModel.doesSnippetExist(text)) {
                        snippetViewModel.insertSnippetWithLabels(text, timestamp, labels)
                        importedCount++
                    }
                }
            }
            Snackbar.make(this.findViewById(R.id.appExclusionMain), "Imported $importedCount new snippets", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(this.findViewById(R.id.appExclusionMain), "Failed to import snippets", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun parseJsonSnippets(content: String): List<Triple<String, Long, List<String>>> {
        val result = mutableListOf<Triple<String, Long, List<String>>>()
        val jsonArray = JSONArray(content)

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val text = obj.optString("text", "")
            val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            val labels = obj.optJSONArray("labels")?.let { array ->
                List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
            } ?: emptyList()

            if (text.isNotBlank()) {
                result.add(Triple(text, timestamp, labels))
            }
        }
        return result
    }

    private fun parseCsvSnippets(content: String): List<Triple<String, Long, List<String>>> {
        val result = mutableListOf<Triple<String, Long, List<String>>>()
        val lines = content.lines()
        val startIndex = if (lines.firstOrNull()?.contains("text", ignoreCase = true) == true) 1 else 0

        for (i in startIndex until lines.size) {
            val line = lines[i]
            val parts = line.split(",")
            if (parts.size >= 3) {
                val text = parts[0].removeSurrounding("\"").trim()
                val timestamp = parts[1].trim().toLongOrNull() ?: System.currentTimeMillis()
                val labels = parts[2].split("|").map { it.trim().replace("\"","") }.filter { it.isNotEmpty() }
                result.add(Triple(text, timestamp, labels))
            }
        }
        return result
    }

    private fun parseTxtSnippets(content: String): List<Triple<String, Long, List<String>>> {
        val snippets = mutableListOf<Triple<String, Long, List<String>>>()
        val lines = content.lines()

        var text = ""
        var timestamp = System.currentTimeMillis()
        var labels: List<String> = emptyList()

        for (line in lines) {
            when {
                line.startsWith("Text:") -> text = line.removePrefix("Text:").trim()
                line.startsWith("Timestamp:") -> timestamp = line.removePrefix("Timestamp:").trim().toLongOrNull() ?: System.currentTimeMillis()
                line.startsWith("Labels:") -> labels = line.removePrefix("Labels:").split(";").map { it.trim() }.filter { it.isNotEmpty() }
                line.contains("--- SNIPPET END ---") -> {
                    if (text.isNotEmpty()) {
                        snippets.add(Triple(text, timestamp, labels))
                    }
                    text = ""
                    labels = emptyList()
                    timestamp = System.currentTimeMillis()
                }
            }
        }

        return snippets
    }

    fun createNotificationChannel(context: Context) {
        val channel = android.app.NotificationChannel(
            "drive_backup_channel",
            "Drive Backup Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications about Drive backup status"
        }
        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun ensureDriveBackupScheduled(context: Context) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("DriveBackup")
            .addListener({
                val infos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork("DriveBackup")
                    .get()

                if (infos.isEmpty()) {
                    Log.d("DriveBackup", "WorkManager job missing. Rescheduling…")
                    val prefs = context.getSharedPreferences("sync_prefs", MODE_PRIVATE)
                    val hour = prefs.getInt("scheduled_hour", 3)
                    val minute = prefs.getInt("scheduled_minute", 0)

                    SyncScheduler.scheduleDriveBackup(context, hour, minute)
                } else {
                    Log.d("DriveBackup", "Backup job already scheduled.")
                }
            }, Executors.newSingleThreadExecutor())
    }
}