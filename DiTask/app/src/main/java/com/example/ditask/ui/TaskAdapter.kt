package com.example.ditask.ui

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ditask.R
import com.example.ditask.data.Task
import com.example.ditask.databinding.ItemTaskBinding
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskAdapter(
    private val onClick: (Task) -> Unit,
    private val onStatusClick: (Task) -> Unit,
    private val onLongClick: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(
        private val binding: ItemTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            val context = binding.root.context
            val done = task.status == Task.STATUS_DONE

            binding.tvTitle.text = task.title
            binding.tvTitle.paintFlags = if (done) {
                binding.tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            binding.tvTitle.setTextColor(
                MaterialColors.getColor(
                    binding.tvTitle,
                    if (done) com.google.android.material.R.attr.colorOnSurfaceVariant
                    else com.google.android.material.R.attr.colorOnSurface
                )
            )

            binding.tvContent.isVisible = !task.content.isNullOrBlank()
            binding.tvContent.text = task.content

            if (done) {
                binding.btnStatus.setImageResource(R.drawable.ic_check_circle)
                binding.btnStatus.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(binding.btnStatus, com.google.android.material.R.attr.colorPrimary)
                )
            } else {
                binding.btnStatus.setImageResource(R.drawable.ic_circle)
                binding.btnStatus.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(
                        binding.btnStatus,
                        if (task.status == Task.STATUS_DOING) com.google.android.material.R.attr.colorPrimary
                        else com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                )
            }

            val priorityText: Int
            val priorityColor: Int
            when (task.priority) {
                Task.PRIORITY_URGENT -> {
                    priorityText = R.string.priority_urgent
                    priorityColor = R.color.priority_urgent
                }
                Task.PRIORITY_IMPORTANT -> {
                    priorityText = R.string.priority_important
                    priorityColor = R.color.priority_important
                }
                else -> {
                    priorityText = R.string.priority_normal
                    priorityColor = R.color.priority_normal
                }
            }
            val accent = ContextCompat.getColor(context, priorityColor)
            // 已完成的任务降低优先级标签的存在感
            val pillColor = if (done) {
                MaterialColors.getColor(binding.tvPriority, com.google.android.material.R.attr.colorOnSurfaceVariant)
            } else {
                accent
            }
            binding.tvPriority.text = context.getString(priorityText)
            binding.tvPriority.setTextColor(pillColor)
            binding.tvPriority.backgroundTintList = ColorStateList.valueOf(
                (pillColor and 0x00FFFFFF) or (PRIORITY_PILL_ALPHA shl 24)
            )
            binding.priorityBar.backgroundTintList = ColorStateList.valueOf(pillColor)

            val dueTime = task.dueTime
            if (dueTime == null) {
                binding.tvDue.isVisible = false
            } else {
                binding.tvDue.isVisible = true
                binding.tvDue.text = context.getString(R.string.due_prefix) + " " + DUE_FORMAT.format(Date(dueTime))
                val overdue = !done && dueTime < System.currentTimeMillis()
                val dueColor = if (overdue) {
                    ContextCompat.getColor(context, R.color.priority_urgent)
                } else {
                    MaterialColors.getColor(binding.tvDue, com.google.android.material.R.attr.colorOnSurfaceVariant)
                }
                binding.tvDue.setTextColor(dueColor)
                binding.tvDue.compoundDrawableTintList = ColorStateList.valueOf(dueColor)
            }

            binding.root.setOnClickListener { onClick(task) }
            binding.root.setOnLongClickListener { onLongClick(task); true }
            binding.btnStatus.setOnClickListener { onStatusClick(task) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
    }

    companion object {
        private val DUE_FORMAT = SimpleDateFormat("M月d日", Locale.CHINA)

        /** 优先级标签背景透明度（0-255） */
        private const val PRIORITY_PILL_ALPHA = 40
    }
}
