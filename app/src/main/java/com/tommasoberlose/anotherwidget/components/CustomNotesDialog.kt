package com.ramybaheeg.yetanotherwidget.components

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ramybaheeg.yetanotherwidget.R
import com.ramybaheeg.yetanotherwidget.databinding.CustomNotesDialogLayoutBinding
import com.ramybaheeg.yetanotherwidget.global.Preferences

class CustomNotesDialog(context: Context, callback: (() -> Unit)?) : BottomSheetDialog(context, R.style.BottomSheetDialogTheme) {

    private var binding: CustomNotesDialogLayoutBinding = CustomNotesDialogLayoutBinding.inflate(LayoutInflater.from(context))

    init {
        binding.notes.setText(Preferences.customNotes)

        binding.actionPositive.setOnClickListener {
            Preferences.customNotes = binding.notes.text.toString()
            this.dismiss()
            callback?.invoke()
        }

        binding.notes.requestFocus()

        setContentView(binding.root)
    }
}