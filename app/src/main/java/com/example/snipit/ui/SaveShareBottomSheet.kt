package com.example.snipit.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.snipit.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SaveShareBottomSheet(
    private val isShare: Boolean,
    private val onOptionSelected: (OptionType) -> Unit
) : BottomSheetDialogFragment() {

    enum class OptionType {
        SHARE_TEXT, SHARE_JSON, SHARE_CSV
    }

    companion object {
        const val TAG = "ModalBottomSheet"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.bottom_sheet_share_export, container, false)

        val textOptionLabel = view.findViewById<TextView>(R.id.textOption)
        textOptionLabel.text = if (isShare) "Share as" else "Save as"

        view.findViewById<LinearLayout>(R.id.optionText).setOnClickListener {
            onOptionSelected(OptionType.SHARE_TEXT)
            dismiss()
        }
        view.findViewById<LinearLayout>(R.id.optionJson).setOnClickListener {
            onOptionSelected(OptionType.SHARE_JSON)
            dismiss()
        }
        view.findViewById<LinearLayout>(R.id.optionCsv).setOnClickListener {
            onOptionSelected(OptionType.SHARE_CSV)
            dismiss()
        }

        return view
    }
}