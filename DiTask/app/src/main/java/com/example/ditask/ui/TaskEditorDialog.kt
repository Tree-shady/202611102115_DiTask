package com.example.ditask.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import com.example.ditask.R
import com.example.ditask.data.Task
import com.example.ditask.databinding.DialogTaskEditBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar
import java.util.UUID

/**
 * 新建 / 编辑任务对话框。只负责收集输入，数据落库由调用方处理。
 */
object TaskEditorDialog {

    fun show(context: Context, task: Task?, onSave: (Task) -> Unit) {
        val binding = DialogTaskEditBinding.inflate(LayoutInflater.from(context))
        var dueTime: Long? = task?.dueTime

        fun renderDue() {
            val value = dueTime
            binding.tvDue.text = if (value == null) {
                context.getString(R.string.due_none)
            } else {
                context.getString(R.string.due_prefix) + " " + formatDate(value)
            }
            binding.btnClearDue.visibility = if (value == null) android.view.View.GONE else android.view.View.VISIBLE
        }

        binding.inputTitle.setText(task?.title)
        binding.inputContent.setText(task?.content)
        when (task?.priority) {
            Task.PRIORITY_URGENT -> binding.chipPriorityUrgent.isChecked = true
            Task.PRIORITY_IMPORTANT -> binding.chipPriorityImportant.isChecked = true
            else -> binding.chipPriorityNormal.isChecked = true
        }
        renderDue()

        binding.btnClearDue.setOnClickListener {
            dueTime = null
            renderDue()
        }

        binding.tvDue.setOnClickListener { view ->
            val calendar = Calendar.getInstance().apply {
                timeInMillis = dueTime ?: timeInMillis
            }
            DatePickerDialog(
                view.context,
                { _, year, month, dayOfMonth ->
                    dueTime = Calendar.getInstance().apply {
                        clear()
                        set(year, month, dayOfMonth, 23, 59, 59)
                    }.timeInMillis
                    renderDue()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (task == null) R.string.new_task else R.string.edit_task)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = binding.inputTitle.text?.toString()?.trim().orEmpty()
            if (title.isEmpty()) {
                binding.inputTitleLayout.error = context.getString(R.string.title_required)
                return@setOnClickListener
            }
            val content = binding.inputContent.text?.toString()?.trim().orEmpty().ifEmpty { null }
            val priority = when (binding.chipGroup.checkedChipId) {
                R.id.chipPriorityUrgent -> Task.PRIORITY_URGENT
                R.id.chipPriorityImportant -> Task.PRIORITY_IMPORTANT
                else -> Task.PRIORITY_NORMAL
            }
            val now = System.currentTimeMillis()
            val entity = task?.copy(
                title = title,
                content = content,
                priority = priority,
                dueTime = dueTime,
                isPendingSync = true,
                updatedAt = now
            ) ?: Task(
                id = UUID.randomUUID().toString(),
                title = title,
                content = content,
                status = Task.STATUS_TODO,
                priority = priority,
                dueTime = dueTime,
                isDeleted = false,
                isPendingSync = true,
                createdAt = now,
                updatedAt = now
            )
            onSave(entity)
            dialog.dismiss()
        }
    }

    private fun formatDate(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return "${calendar.get(Calendar.MONTH) + 1}月${calendar.get(Calendar.DAY_OF_MONTH)}日"
    }
}
