package com.example.snipit.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.example.snipit.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider

class CleanupRulesBottomSheet(
    private val currentSnippetOption: Int,
    private val currentOtpOption: Int,
    private val onSelection: (snippetDays: Int, otpHours: Int) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.bottom_sheet_cleanup_rules, container, false)
        val snippetSlider = view.findViewById<Slider>(R.id.snippetSlider)
        val otpSlider = view.findViewById<Slider>(R.id.otpSlider)
        val btnApply = view.findViewById<Button>(R.id.button)

        snippetSlider.value = currentSnippetOption.toFloat()
        otpSlider.value = currentOtpOption.toFloat()

        btnApply.setOnClickListener {
            val snippetDays = when (snippetSlider.value.toInt()) {
                0 -> -1
                1 -> 30
                2 -> 60
                3 -> 90
                else -> -1
            }

            val otpHours = when (val valOtp = otpSlider.value.toInt()) {
                0 -> -1
                else -> valOtp
            }

            onSelection(snippetDays, otpHours)
            dismiss()
        }
        return view
    }
}