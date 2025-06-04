package com.example.snipit.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import com.example.snipit.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CleanupRulesBottomSheet(
    private val currentSnippetOption: Int,
    private val currentOtpOption: Int,
    private val onSelection: (snippetDays: Int, otpHours: Int) -> Unit
) : BottomSheetDialogFragment() {

    private val snippetOptions = listOf(
        "Never", "After 1 month", "After 2 months", "After 3 months"
    )
    private val otpOptions = listOf(
        "Never", "After 24 hours", "After 36 hours", "After 48 hours"
    )

    private var selectedSnippetIndex = currentSnippetOption
    private var selectedOtpIndex = currentOtpOption

    @SuppressLint("SetTextI18n")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.bottom_sheet_cleanup_rules, container, false)
        val containerLayout = view.findViewById<LinearLayout>(R.id.containerRules)

        val title = TextView(requireContext()).apply {
            text = "Auto-Cleanup Settings"
            textSize = 18f
            setPadding(8, 8, 8, 16)
        }

        containerLayout.addView(title)

        containerLayout.addView(createOptionGroup("Clean snippets", snippetOptions, currentSnippetOption) {
            selectedSnippetIndex = it
        })

        containerLayout.addView(createOptionGroup("Clean OTPs", otpOptions, currentOtpOption) {
            selectedOtpIndex = it
        })

        val applyButton = Button(requireContext()).apply {
            text = "Apply"
            setOnClickListener {
                val snippetDays = when (selectedSnippetIndex) {
                    1 -> 30
                    2 -> 60
                    3 -> 90
                    else -> -1
                }
                val otpHours = when (selectedOtpIndex) {
                    1 -> 24
                    2 -> 36
                    3 -> 48
                    else -> -1
                }
                onSelection(snippetDays, otpHours)
                dismiss()
            }
        }

        containerLayout.addView(applyButton)
        return view
    }

    private fun createOptionGroup(title: String, options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit): View {
        val group = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        val titleText = TextView(requireContext()).apply {
            text = title
            textSize = 16f
        }

        group.addView(titleText)

        options.forEachIndexed { index, label ->
            val radio = RadioButton(requireContext()).apply {
                text = label
                isChecked = index == selectedIndex
                setOnClickListener { onSelected(index) }
            }
            group.addView(radio)
        }

        return group
    }
}