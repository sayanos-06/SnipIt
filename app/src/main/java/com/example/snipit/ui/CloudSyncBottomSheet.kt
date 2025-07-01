package com.example.snipit.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.example.snipit.R
import com.example.snipit.ui.SettingsActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CloudSyncBottomSheet(
    private val onOptionSelected: (SettingsActivity.CloudSyncMode) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_cloud_sync, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.btnGoogleDrive).setOnClickListener {
            onOptionSelected(SettingsActivity.CloudSyncMode.GOOGLE_DRIVE)
            dismiss()
        }
        view.findViewById<Button>(R.id.btnDisabled).setOnClickListener {
            onOptionSelected(SettingsActivity.CloudSyncMode.OFF)
            dismiss()
        }
    }
}