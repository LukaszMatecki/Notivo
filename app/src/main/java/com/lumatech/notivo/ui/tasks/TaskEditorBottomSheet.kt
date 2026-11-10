package com.lumatech.notivo.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.BottomSheetTaskEditorBinding
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.model.TaskPriority
import com.lumatech.notivo.util.DateUtils

/**
 * Modal bottom sheet for creating or editing a planner task with due date and priority.
 */
class TaskEditorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTaskEditorBinding? = null
    private val binding get() = checkNotNull(_binding)

    private var selectedDueDate: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTaskEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val existingId = arguments?.getLong(ARG_TASK_ID, 0L) ?: 0L
        val existingTitle = arguments?.getString(ARG_TASK_TITLE).orEmpty()
        val existingDesc = arguments?.getString(ARG_TASK_DESC)
        val rawDueDate = arguments?.getLong(ARG_TASK_DUE_DATE, -1L) ?: -1L
        val existingPriority = TaskPriority.fromLevel(
            arguments?.getInt(ARG_TASK_PRIORITY, TaskPriority.MEDIUM.level)
                ?: TaskPriority.MEDIUM.level
        )
        val linkedNoteId = arguments?.getLong(ARG_LINKED_NOTE_ID, -1L)?.takeIf { it > 0L }
        val isArchived = arguments?.getBoolean(ARG_TASK_ARCHIVED, false) ?: false

        if (existingId > 0L) {
            binding.tvTaskSheetHeading.setText(R.string.task_edit_title)
            binding.etTaskTitle.setText(existingTitle)
            binding.etTaskDescription.setText(existingDesc.orEmpty())
            binding.btnArchiveTask.visibility = View.VISIBLE
            binding.btnArchiveTask.setText(
                if (isArchived) R.string.task_action_unarchive else R.string.task_action_archive
            )
            binding.btnArchiveTask.setOnClickListener {
                setFragmentResult(
                    REQ_TASK_ARCHIVE_TOGGLED,
                    Bundle().apply {
                        putLong(EXTRA_TASK_ID, existingId)
                        putBoolean(EXTRA_IS_ARCHIVED, !isArchived)
                    }
                )
                dismiss()
            }
        } else {
            binding.tvTaskSheetHeading.setText(R.string.task_new_title)
            binding.btnArchiveTask.visibility = View.GONE
        }

        selectedDueDate = rawDueDate.takeIf { it > 0L }
        renderDueDateButton()

        when (existingPriority) {
            TaskPriority.LOW -> binding.chipPriorityLow.isChecked = true
            TaskPriority.MEDIUM -> binding.chipPriorityMedium.isChecked = true
            TaskPriority.HIGH -> binding.chipPriorityHigh.isChecked = true
        }

        binding.btnPickDueDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.task_field_due_date)
                .apply {
                    selectedDueDate?.let { setSelection(it) }
                }
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                selectedDueDate = millis
                renderDueDateButton()
            }
            picker.show(parentFragmentManager, "TaskDatePicker")
        }

        binding.btnClearDueDate.setOnClickListener {
            selectedDueDate = null
            renderDueDateButton()
        }

        binding.btnSaveTask.setOnClickListener {
            val title = binding.etTaskTitle.text?.toString()?.trim().orEmpty()
            if (title.isEmpty()) {
                binding.etTaskTitle.error = getString(R.string.task_field_title)
                return@setOnClickListener
            }

            val priority = when (binding.chipGroupPriority.checkedChipId) {
                R.id.chipPriorityLow -> TaskPriority.LOW
                R.id.chipPriorityHigh -> TaskPriority.HIGH
                else -> TaskPriority.MEDIUM
            }

            setFragmentResult(
                REQ_TASK_SAVED,
                Bundle().apply {
                    putLong(EXTRA_TASK_ID, existingId)
                    putString(EXTRA_TASK_TITLE, title)
                    putString(EXTRA_TASK_DESC, binding.etTaskDescription.text?.toString())
                    putLong(EXTRA_TASK_DUE_DATE, selectedDueDate ?: -1L)
                    putInt(EXTRA_TASK_PRIORITY, priority.level)
                    putLong(EXTRA_LINKED_NOTE_ID, linkedNoteId ?: -1L)
                }
            )
            dismiss()
        }
    }

    private fun renderDueDateButton() {
        val date = selectedDueDate
        if (date != null) {
            binding.btnPickDueDate.text = DateUtils.formatTaskDueDate(date)
            binding.btnClearDueDate.visibility = View.VISIBLE
        } else {
            binding.btnPickDueDate.setText(R.string.task_due_date_none)
            binding.btnClearDueDate.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "TaskEditorBottomSheet"
        const val REQ_TASK_SAVED = "req_task_saved"
        const val REQ_TASK_ARCHIVE_TOGGLED = "req_task_archive_toggled"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_DESC = "extra_task_desc"
        const val EXTRA_TASK_DUE_DATE = "extra_task_due_date"
        const val EXTRA_TASK_PRIORITY = "extra_task_priority"
        const val EXTRA_LINKED_NOTE_ID = "extra_linked_note_id"
        const val EXTRA_IS_ARCHIVED = "extra_is_archived"

        private const val ARG_TASK_ID = "arg_task_id"
        private const val ARG_TASK_TITLE = "arg_task_title"
        private const val ARG_TASK_DESC = "arg_task_desc"
        private const val ARG_TASK_DUE_DATE = "arg_task_due_date"
        private const val ARG_TASK_PRIORITY = "arg_task_priority"
        private const val ARG_LINKED_NOTE_ID = "arg_linked_note_id"
        private const val ARG_TASK_ARCHIVED = "arg_task_archived"

        fun newInstance(task: Task? = null): TaskEditorBottomSheet = TaskEditorBottomSheet().apply {
            arguments = Bundle().apply {
                putLong(ARG_TASK_ID, task?.id ?: 0L)
                putString(ARG_TASK_TITLE, task?.title ?: "")
                putString(ARG_TASK_DESC, task?.description)
                putLong(ARG_TASK_DUE_DATE, task?.dueDate ?: -1L)
                putInt(ARG_TASK_PRIORITY, task?.priority?.level ?: TaskPriority.MEDIUM.level)
                putLong(ARG_LINKED_NOTE_ID, task?.noteId ?: -1L)
                putBoolean(ARG_TASK_ARCHIVED, task?.isArchived ?: false)
            }
        }
    }
}
