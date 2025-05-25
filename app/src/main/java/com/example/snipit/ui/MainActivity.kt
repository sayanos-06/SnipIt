package com.example.snipit.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.snipit.R
import com.example.snipit.adapter.SnippetAdapter
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.snipit.service.FloatingIconService
import androidx.core.content.edit
import androidx.recyclerview.widget.DefaultItemAnimator
import com.example.snipit.model.Snippet
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
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

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: SnippetAdapter
    private lateinit var recyclerView: RecyclerView
    private val snippetViewModel: SnippetViewModel by viewModels()
    private lateinit var toolbar: MaterialToolbar
    private lateinit var floatingIconSwitch: MaterialSwitch
    var actionMode: ActionMode? = null
    private var selectedSnippets: List<Snippet> = emptyList()
    private var pendingExportFormat: ExportFormat? = null
    enum class ExportFormat { TXT, JSON, CSV }
    private val uriPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { treeUri ->
                when (pendingExportFormat) {
                    ExportFormat.TXT -> saveAsTxt(treeUri, selectedSnippets)
                    ExportFormat.JSON -> saveAsJson(treeUri, selectedSnippets)
                    ExportFormat.CSV -> saveAsCsv(treeUri, selectedSnippets)
                    else -> Toast.makeText(this, "Export format not selected", Toast.LENGTH_SHORT).show()
                }
                pendingExportFormat = null
            }
        }
    }
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            toolbar.visibility = View.GONE
            mode.menuInflater.inflate(R.menu.multi_select_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            selectAllCheckbox.visibility = View.VISIBLE
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete -> {
                    val toDelete = adapter.getSelectedSnippets()
                    if (toDelete.isNotEmpty()) {
                        bulkDeleteConfirmationDialog(toDelete)
                    }
                    mode.finish()
                    true
                }
                R.id.action_pin -> {
                    val toPin = adapter.getSelectedSnippets()
                    if (toPin.isNotEmpty()) {
                        toPin.forEach { snippet ->
                            snippetViewModel.updatePinStatus(snippet.id, true)
                        }
                    }
                    mode.finish()
                    true
                }
                R.id.action_more -> {
                    val menuView = findViewById<View>(R.id.action_more)
                    if (menuView != null) {
                        multiSelectMoreMenu(menuView)
                    }
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            adapter.clearSelectedSnippets()
            snippetViewModel.clearPreviousSelectedSnippets()
            snippetViewModel.clearSelectedSnippets()
            actionMode = null
            selectedSnippets = emptyList()

            Handler(Looper.getMainLooper()).postDelayed({
                toolbar.visibility = View.VISIBLE
            }, 475)
            selectAllCheckbox.visibility = View.GONE
            selectAllCheckbox.isChecked = false
        }
    }
    private lateinit var selectAllCheckbox: CheckBox

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("ImplicitSamInstance")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        toolbar = findViewById(R.id.toolbar)
        floatingIconSwitch = findViewById(R.id.floatingIconSwitch)
        selectAllCheckbox = findViewById(R.id.selectAllCheckBox)
        recyclerView = findViewById(R.id.snippetRecyclerView)

        sendBroadcast(Intent("com.example.snipit.HIDE_ICON"))

        checkAndRequestPermissions()
        setSupportActionBar(toolbar)
        loadSnippets()

        snippetViewModel.isFloatingIconEnabled.observe(this) { isEnabled ->
            floatingIconSwitch.isChecked = isEnabled
            if (isEnabled) {
                if (!isServiceRunning(FloatingIconService::class.java)) {
                    startService(Intent(this, FloatingIconService::class.java))
                }
            } else {
                stopService(Intent(this, FloatingIconService::class.java))
            }
        }

        floatingIconSwitch.setOnCheckedChangeListener { _, isChecked ->
            snippetViewModel.setFloatingIconEnabled(this, isChecked, floatingIconSwitch)
        }

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.snipit.FLOATING_ICON_REMOVED") {
                    snippetViewModel.setFloatingIconEnabled(this@MainActivity,false, floatingIconSwitch)
                }
            }
        },
            IntentFilter("com.example.snipit.FLOATING_ICON_REMOVED"),
            Context.RECEIVER_EXPORTED)

        adapter = SnippetAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        adapter.onPinChange = { snippet, isPinned ->
            snippetViewModel.updatePinStatus(snippet.id, isPinned)
        }

        adapter.onDeleteClick = { snippet ->
            deleteConfirmationDialog(snippet)
        }

        adapter.onSelectionChanged = {
            val selectedCount = adapter.getSelectedSnippets().size
            if (selectedCount > 0) {
                multiSelectToolbar(selectedCount)
                snippetViewModel.setSelectedSnippets(adapter.getSelectedSnippets().toSet())
            } else {
                actionMode?.finish()
                invalidateOptionsMenu()
            }
        }

        recyclerView.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 250
            removeDuration = 250
            moveDuration = 200
            changeDuration = 200
        }

        snippetViewModel.selectedSnippets.observe(this) { snippets ->
            if (snippets.isNotEmpty()) {
                adapter.setSelectedSnippets(snippets)
                multiSelectToolbar(snippets.size)
            } else {
                actionMode?.finish()
            }
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
                val previous = snippetViewModel.previousSelectedSnippets.value
                if (!previous.isNullOrEmpty()) {
                    snippetViewModel.setSelectedSnippets(previous)
                } else {
                    adapter.clearSelectedSnippets()
                    snippetViewModel.setSelectedSnippets(emptySet())
                }
                snippetViewModel.clearPreviousSelectedSnippets()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sendBroadcast(Intent("com.example.snipit.HIDE_ICON"))
        Handler(Looper.getMainLooper()).postDelayed({
            checkClipboardAndSave()
        }, 1000)
    }

    override fun onPause() {
        super.onPause()
        sendBroadcast(Intent("com.example.snipit.SHOW_ICON"))
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (actionMode != null) return false
        menuInflater.inflate(R.menu.top_app_bar, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        for (i in 0 until menu.size) {
            menu[i].icon?.setTint(ContextCompat.getColor(this, R.color.md_theme_onPrimary))
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                val searchView = item.actionView as? SearchView

                val searchEditText = searchView?.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
                searchEditText?.setHintTextColor(getColor(R.color.md_theme_outline))
                searchEditText?.setTextColor(getColor(R.color.md_theme_onPrimary))

                val closeButton = searchView?.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
                closeButton?.setColorFilter(getColor(R.color.md_theme_onPrimary), PorterDuff.Mode.SRC_IN)

                searchView?.queryHint = "Search snippets..."
                searchView?.isSubmitButtonEnabled = false

                searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        adapter.filterSnippetList(newText ?: "")
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

    private fun loadSnippets() {
        snippetViewModel.snippets.observe(this) { snippetList ->
            if (adapter.getAllSnippets() != snippetList) {
                adapter.updateSnippetList(snippetList)
            }
        }
    }

    private fun checkClipboardAndSave() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData: ClipData? = clipboard.primaryClip
        val copiedText = clipData?.getItemAt(0)?.text?.toString()?.trim()

        val sharedPrefs = getSharedPreferences("snipit_prefs", Context.MODE_PRIVATE)
        val lastSaved = sharedPrefs.getString("last_clipboard_text", "")

        if (!copiedText.isNullOrEmpty() && copiedText != lastSaved) {
            snippetViewModel.insertOrUpdateSnippet(copiedText)
            sharedPrefs.edit { putString("last_clipboard_text", copiedText) }
        }
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
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:$packageName".toUri())
                    startActivity(intent)
                },
                onNegative = { dialog ->
                    dialog.dismiss()
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Are you sure?")
                        .setMessage("You will have to open the app everytime to confirm the storing of the copied snippet.")
                        .setNeutralButton("Cancel") { newDialog, _ ->
                            newDialog.dismiss()
                            floatingIconSwitch.isEnabled = false
                        }
                        .setPositiveButton("Yes") { newDialog, _ ->
                            newDialog.dismiss()
                            floatingIconSwitch.isEnabled = false
                        }
                        .setNegativeButton("No") { _, _ -> checkAndRequestPermissions() }
                        .show()
                }
            )
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun deleteConfirmationDialog(snippet: Snippet) {
        showPermissionDialog(
            title = "Delete Snippet",
            message = "Are you sure you want to delete this snippet?",
            icon = getDrawable(R.drawable.round_delete_24),
            positiveButton = "Delete",
            negativeButton = "Cancel",
            onPositive = {
                snippetViewModel.deleteSnippet(snippet)
                Snackbar.make(recyclerView, "Snippet deleted.", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        snippetViewModel.insertOrUpdateSnippet(snippet.text)
                    }
                    .show()
            },
            onNegative = { dialog -> dialog.dismiss() }
        )
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun bulkDeleteConfirmationDialog(snippets: List<Snippet>) {
        showPermissionDialog(
            title = "Delete ${snippets.size} Snippets",
            message = "Are you sure you want to delete the selected snippets?",
            icon = getDrawable(R.drawable.round_delete_24),
            positiveButton = "Delete",
            negativeButton = "Cancel",
            onPositive = {
                snippets.forEach { snippetViewModel.deleteSnippet(it) }
                adapter.clearSelectedSnippets()
                Snackbar.make(recyclerView, "${snippets.size} snippets deleted.", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        snippets.forEach { snippetViewModel.insertOrUpdateSnippet(it.text) }
                    }
                    .show()
            },
            onNegative = { dialog -> dialog.dismiss() }
        )
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == serviceClass.name
        }
    }

    private fun topBarMoreMenu(anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.top_bar_more_menu, popupMenu.menu)

        try {
            val fields = popupMenu.javaClass.declaredFields
            for (field in fields) {
                if (field.name == "mPopup") {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popupMenu)
                    val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                    val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
                    setForceIcons.invoke(menuPopupHelper, true)
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        popupMenu.setOnMenuItemClickListener { popupItem ->
            when (popupItem.itemId) {
                R.id.action_save_as -> {
                    saveShareBottomSheet(isShare = false, adapter.getAllSnippets())
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

    private fun multiSelectMoreMenu(anchorView: View) {
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.multi_select_more_menu, popupMenu.menu)

        try {
            val fields = popupMenu.javaClass.declaredFields
            for (field in fields) {
                if (field.name == "mPopup") {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popupMenu)
                    val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                    val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.java)
                    setForceIcons.invoke(menuPopupHelper, true)
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        popupMenu.setOnMenuItemClickListener { popupItem ->
            when (popupItem.itemId) {
                R.id.action_share -> {
                    selectedSnippets = adapter.getSelectedSnippets()
                    saveShareBottomSheet(isShare = true, selectedSnippets)
                    true
                }
                R.id.action_save_as -> {
                    selectedSnippets = adapter.getSelectedSnippets()
                    saveShareBottomSheet(isShare = false, selectedSnippets)
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    @SuppressLint("SetTextI18n")
    private fun saveShareBottomSheet(isShare: Boolean, selectedSnippets: List<Snippet>) {
        val bottomSheet = SaveShareBottomSheet(isShare) { option ->
            when (option) {
                SaveShareBottomSheet.OptionType.SHARE_TEXT -> {
                    if (isShare) shareAsText(selectedSnippets) else saveInFolder(ExportFormat.TXT)
                }
                SaveShareBottomSheet.OptionType.SHARE_JSON -> {
                    if (isShare) shareAsJson(selectedSnippets) else saveInFolder(ExportFormat.JSON)
                }
                SaveShareBottomSheet.OptionType.SHARE_CSV -> {
                    if (isShare) shareAsCsv(selectedSnippets) else saveInFolder(ExportFormat.CSV)
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

    private fun saveAsTxt(treeUri: Uri, selectedSnippets: List<Snippet>) {
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
                val saveData = selectedSnippets.joinToString("\n") {
                    "snippet: "+it.text+" timestamp: "+it.timestamp
                }
                writer?.write(saveData)
            }

            Toast.makeText(this, "Exported to $fileName.txt", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAsJson(treeUri: Uri, selectedSnippets: List<Snippet>) {
        if (selectedSnippets.isEmpty()) return

        contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val jsonArray = JSONArray()
        val obj = JSONObject()
        obj.put("itemCount", selectedSnippets.size)
        jsonArray.put(obj)
        for (snippet in selectedSnippets) {
            val snippetObj = JSONObject()
            snippetObj.put("text", snippet.text)
            snippetObj.put("timestamp", snippet.timestamp)
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
            fileName)

        if (docUri != null) {
            val outputStream = contentResolver.openOutputStream(docUri)
            outputStream?.bufferedWriter().use { writer ->
                writer?.write(jsonString)
            }
            Toast.makeText(this, "Exported to $fileName.json", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to create file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAsCsv(treeUri: Uri, selectedSnippets: List<Snippet>) {
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

                    writer.write("Text,Timestamp")
                    writer.newLine()

                    for (snippet in selectedSnippets) {
                        val escapedText = snippet.text.replace("\"", "\"\"") // escape double quotes
                        val csvLine = "\"$escapedText\",${snippet.timestamp}"
                        writer.write(csvLine)
                        writer.newLine()
                    }

                    writer.flush()
                    Toast.makeText(this, "Snippets exported as CSV.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to export as CSV.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Failed to create CSV file.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareAsText(selectedSnippets: List<Snippet>) {
        if (selectedSnippets.isEmpty()) return

        val shareData = buildString {
            selectedSnippets.forEachIndexed { index, snippet ->
                append("Snippet ${index + 1}: ")
                append("${snippet.text.trim()} (Saved on: ${formatDate(snippet.timestamp)})\n")
                append("\n")
            }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Shared Snippets")
            putExtra(Intent.EXTRA_TEXT, shareData.trim())
        }

        startActivity(Intent.createChooser(shareIntent, "Share snippets via"))
    }

    private fun shareAsJson(selectedSnippets: List<Snippet>) {
        if (selectedSnippets.isEmpty()) return

        val jsonArray = JSONArray()
        val obj = JSONObject()
        obj.put("itemCount", selectedSnippets.size)
        jsonArray.put(obj)
        for (snippet in selectedSnippets) {
            val snippetObj = JSONObject()
            snippetObj.put("text", snippet.text)
            snippetObj.put("timestamp", snippet.timestamp)
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

    private fun shareAsCsv(selectedSnippets: List<Snippet>) {
        if (selectedSnippets.isEmpty()) return

        val shareData = buildString {
            append("Text,Timestamp\n")
            selectedSnippets.forEachIndexed { _, snippet ->
                append("\"${snippet.text.trim().replace("\"", "\"\"").replace("\n", " ")}\",")
                append("${snippet.timestamp}\n")
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
}