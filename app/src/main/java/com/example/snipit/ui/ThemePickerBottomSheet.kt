package com.example.snipit.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import com.example.snipit.R
import com.example.snipit.ui.SettingsActivity.ThemeMode
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ThemePickerBottomSheet(
    private val currentSelection: ThemeMode = ThemeMode.SYSTEM,
    private val onThemeSelected: (ThemeMode) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.bottom_sheet_theme_picker, container, false)

        val radioGroup = view.findViewById<RadioGroup>(R.id.themeRadioGroup)
        val radioLight = view.findViewById<RadioButton>(R.id.radioLight)
        val radioDark = view.findViewById<RadioButton>(R.id.radioDark)
        val radioSystem = view.findViewById<RadioButton>(R.id.radioSystem)

        when (currentSelection) {
            ThemeMode.LIGHT -> radioLight.isChecked = true
            ThemeMode.DARK -> radioDark.isChecked = true
            ThemeMode.SYSTEM -> radioSystem.isChecked = true
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedMode = when (checkedId) {
                R.id.radioLight -> ThemeMode.LIGHT
                R.id.radioDark -> ThemeMode.DARK
                R.id.radioSystem -> ThemeMode.SYSTEM
                else -> ThemeMode.SYSTEM
            }

            onThemeSelected(selectedMode)
            dismiss()
        }

        return view
    }
}
