package com.lumatech.notivo.ui.tasks

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.ItemTaskRowBinding
import com.lumatech.notivo.databinding.ItemTaskSectionHeaderBinding
import com.lumatech.notivo.domain.model.FontSizeOption
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.model.TaskPriority
import com.lumatech.notivo.ui.common.colorRes
import com.lumatech.notivo.ui.common.performConfirmHaptic
import com.lumatech.notivo.ui.common.performHapticSafe
import com.lumatech.notivo.util.DateUtils

/**
 * Grouped task list adapter rendering section headers and task items with priority indicators
 * and animated completion strikethrough.
 */
class TaskAdapter(
    private val onTaskToggle: (Task, Boolean) -> Unit,
    private val onTaskClick: (Task) -> Unit,
    private val onTaskLongClick: (Task, View) -> Unit
) : ListAdapter<TaskListItem, RecyclerView.ViewHolder>(DiffCallback) {

    private var fontScale: Float = FontSizeOption.MEDIUM.scaleFactor
    private val pendingToggleIds = mutableSetOf<Long>()

    fun updateFontSize(option: FontSizeOption) {
        if (fontScale != option.scaleFactor) {
            fontScale = option.scaleFactor
            if (itemCount > 0) {
                notifyItemRangeChanged(0, itemCount)
            }
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TaskListItem.SectionHeader -> TYPE_HEADER
        is TaskListItem.TaskRow -> TYPE_TASK
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                ItemTaskSectionHeaderBinding.inflate(inflater, parent, false)
            )
            TYPE_TASK -> TaskViewHolder(
                ItemTaskRowBinding.inflate(inflater, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TaskListItem.SectionHeader -> (holder as HeaderViewHolder).bind(item)
            is TaskListItem.TaskRow -> (holder as TaskViewHolder).bind(item)
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemTaskSectionHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(header: TaskListItem.SectionHeader) {
            binding.tvTaskSectionTitle.setText(header.titleRes)
            binding.tvTaskSectionCount.text = header.count.toString()
        }
    }

    inner class TaskViewHolder(
        private val binding: ItemTaskRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: TaskListItem.TaskRow) {
            val task = row.task
            val context = binding.root.context

            pendingToggleIds.remove(task.id)
            binding.taskCardRoot.animate().cancel()
            binding.cbTaskDone.setOnCheckedChangeListener(null)
            binding.cbTaskDone.isChecked = task.isCompleted
            binding.tvTaskTitle.text = task.title
            binding.tvTaskTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * fontScale)
            binding.tvTaskMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f * fontScale)
            binding.tvTaskTitle.setStruck(task.isCompleted)

            binding.taskCardRoot.alpha = if (task.isCompleted) 0.62f else 1f

            val metaParts = buildList {
                task.dueDate?.let { add(DateUtils.formatTaskDueDate(it)) }
                task.description?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            if (metaParts.isNotEmpty()) {
                binding.tvTaskMeta.visibility = View.VISIBLE
                binding.tvTaskMeta.text = metaParts.joinToString(" · ")
                val isOverdue = !task.isCompleted &&
                    task.dueDate != null &&
                    DateUtils.isOverdue(task.dueDate)
                val metaColor = if (isOverdue) {
                    MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorError)
                } else {
                    MaterialColors.getColor(
                        binding.root,
                        com.google.android.material.R.attr.colorOnSurfaceVariant
                    )
                }
                binding.tvTaskMeta.setTextColor(metaColor)
            } else {
                binding.tvTaskMeta.visibility = View.GONE
            }

            binding.viewPriorityDot.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, task.priority.colorRes())
            )

            binding.cbTaskDone.setOnCheckedChangeListener { buttonView, isChecked ->
                if (!pendingToggleIds.add(task.id)) return@setOnCheckedChangeListener
                buttonView.performConfirmHaptic()
                binding.taskCardRoot.animate()
                    .alpha(if (isChecked) 0.62f else 1f)
                    .setDuration(220L)
                    .start()
                binding.tvTaskTitle.animateStrike(isChecked) {
                    pendingToggleIds.remove(task.id)
                    onTaskToggle(task, isChecked)
                }
            }
            binding.taskCardRoot.setOnClickListener {
                if (!pendingToggleIds.contains(task.id)) {
                    onTaskClick(task)
                }
            }
            binding.taskCardRoot.setOnLongClickListener { view ->
                view.performHapticSafe(HapticFeedbackConstants.LONG_PRESS)
                onTaskLongClick(task, view)
                true
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TaskListItem>() {
        override fun areItemsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean {
            return when {
                oldItem is TaskListItem.SectionHeader && newItem is TaskListItem.SectionHeader ->
                    oldItem.titleRes == newItem.titleRes
                oldItem is TaskListItem.TaskRow && newItem is TaskListItem.TaskRow ->
                    oldItem.task.id == newItem.task.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean =
            oldItem == newItem
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TASK = 1
    }
}
