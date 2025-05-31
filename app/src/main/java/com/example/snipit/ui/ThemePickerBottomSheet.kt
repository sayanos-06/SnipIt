package com.example.snipit.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.example.snipit.R
import com.example.snipit.ui.SettingsActivity.ThemeMode
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ThemePickerBottomSheet(
    private val onThemeSelected: (ThemeMode) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.bottom_sheet_theme_picker, container, false)

        view.findViewById<LinearLayout>(R.id.optionLight).setOnClickListener {
            onThemeSelected(ThemeMode.LIGHT)
            dismiss()
        }
        view.findViewById<LinearLayout>(R.id.optionDark).setOnClickListener {
            onThemeSelected(ThemeMode.DARK)
            dismiss()
        }
        view.findViewById<LinearLayout>(R.id.optionSystem).setOnClickListener {
            onThemeSelected(ThemeMode.SYSTEM)
            dismiss()
        }

        return view
    }
}
