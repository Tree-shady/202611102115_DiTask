package com.example.ditask.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import com.example.ditask.R
import com.example.ditask.data.ServerSettings
import com.example.ditask.databinding.DialogServerSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 服务器地址设置对话框：校验通过后把规范化地址交给调用方保存。
 */
object ServerSettingsDialog {

    fun show(context: Context, current: String, onConfirm: (String) -> Unit) {
        val binding = DialogServerSettingsBinding.inflate(LayoutInflater.from(context))
        binding.inputServer.setText(current)
        binding.inputServer.setSelection(current.length)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.server_settings)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = ServerSettings.normalize(binding.inputServer.text?.toString().orEmpty())
            when {
                url.isEmpty() ->
                    binding.inputServerLayout.error = context.getString(R.string.server_required)
                !ServerSettings.isValid(url) ->
                    binding.inputServerLayout.error = context.getString(R.string.server_invalid)
                else -> {
                    dialog.dismiss()
                    onConfirm(url)
                }
            }
        }
    }
}
