    package com.example.snipit.ui

    import android.app.Application
    import android.content.Context
    import android.util.Log
    import androidx.core.content.ContextCompat.getDrawable
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.LiveData
    import androidx.lifecycle.MutableLiveData
    import androidx.lifecycle.viewModelScope
    import com.example.snipit.data.SnippetDatabase
    import com.example.snipit.model.Snippet
    import kotlinx.coroutines.launch
    import androidx.core.content.edit
    import com.example.snipit.R
    import com.google.android.material.materialswitch.MaterialSwitch

    class SnippetViewModel(application: Application) : AndroidViewModel(application) {

        val snippets: LiveData<List<Snippet>> =
            SnippetDatabase.getInstance(application).snippetDao().getAllSnippets()
        private val _isFloatingIconEnabled = MutableLiveData<Boolean>()
        val isFloatingIconEnabled: LiveData<Boolean> = _isFloatingIconEnabled

        init {
            // Load saved state from SharedPreferences
            val prefs = application.getSharedPreferences("snipit_prefs", Context.MODE_PRIVATE)
            _isFloatingIconEnabled.value = prefs.getBoolean("floating_icon_enabled", false)
        }

        fun insertOrUpdateSnippet(text: String) {
            viewModelScope.launch {
                val dao = SnippetDatabase.getInstance(getApplication()).snippetDao()
                val existingSnippet = dao.getSnippetByText(text)
                Log.d("MainActivity", "Existing snippet: $existingSnippet")
                if (existingSnippet != null) {
                    Log.d("MainActivity", "Snippet exists. Updating timestamp")
                    val updatedSnippet = existingSnippet.copy(timestamp = System.currentTimeMillis())
                    dao.updateSnippet(updatedSnippet)
                    Log.d("MainActivity", "Snippet exists. Updated timestamp to move it to top: $text")
                } else {
                    Log.d("MainActivity", "Snippet doesn't exist. Inserting new snippet")
                    val newSnippet = Snippet(text = text, timestamp = System.currentTimeMillis())
                    dao.insert(newSnippet)
                    Log.d("MainActivity", "New snippet inserted: $text")
                }
            }
        }

        fun setFloatingIconEnabled(context: Context, state: Boolean, floatingIconSwitch: MaterialSwitch) {
            _isFloatingIconEnabled.value = state
            Log.d("MainActivity", "Floating icon state changed to $state")
            if (state) {
                floatingIconSwitch.setThumbIconDrawable(getDrawable(context, R.drawable.round_check_24))
            } else {
                floatingIconSwitch.setThumbIconDrawable(getDrawable(context, R.drawable.round_close_24))
            }

            // Save to SharedPreferences
            val prefs = getApplication<Application>().getSharedPreferences("snipit_prefs", Context.MODE_PRIVATE)
            prefs.edit { putBoolean("floating_icon_enabled", state) }
        }
    }