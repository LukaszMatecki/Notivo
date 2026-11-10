package com.lumatech.notivo.ui.tasks

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.FragmentTaskListBinding
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.model.TaskPriority
import com.lumatech.notivo.ui.common.BindingFragment
import com.lumatech.notivo.ui.common.ExpandableFabView
import com.lumatech.notivo.ui.common.NotivoOptionSheet
import com.lumatech.notivo.ui.common.NotivoSnackbarHelper
import com.lumatech.notivo.ui.common.NotivoViewModelFactory
import com.lumatech.notivo.ui.common.areAnimationsEnabled
import com.lumatech.notivo.ui.common.collectWithLifecycle
import com.lumatech.notivo.ui.common.performHapticSafe
import com.lumatech.notivo.ui.common.serviceLocator
import com.lumatech.notivo.util.DateUtils

/**
 * Planner screen showing active, completed, and archived tasks with a pill switcher,
 * animated completion strikethrough, swipe-to-delete, and contextual options.
 */
class TaskListFragment : BindingFragment<FragmentTaskListBinding>(
    FragmentTaskListBinding::inflate
) {
    private val viewModel: TaskListViewModel by viewModels {
        NotivoViewModelFactory(serviceLocator)
    }

    private lateinit var taskAdapter: TaskAdapter
    private var currentTab: TaskViewTab = TaskViewTab.ACTIVE
    private var hasRenderedInitialTab: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener(TaskEditorBottomSheet.REQ_TASK_SAVED) { _, bundle ->
            val existingId = bundle.getLong(TaskEditorBottomSheet.EXTRA_TASK_ID, 0L)
            if (existingId == 0L) {
                viewModel.onTabSelected(TaskViewTab.ACTIVE)
            }
            viewModel.saveTask(
                existingId = existingId,
                title = bundle.getString(TaskEditorBottomSheet.EXTRA_TASK_TITLE).orEmpty(),
                description = bundle.getString(TaskEditorBottomSheet.EXTRA_TASK_DESC),
                dueDate = bundle.getLong(TaskEditorBottomSheet.EXTRA_TASK_DUE_DATE, -1L)
                    .takeIf { it > 0L },
                priority = TaskPriority.fromLevel(
                    bundle.getInt(TaskEditorBottomSheet.EXTRA_TASK_PRIORITY, TaskPriority.MEDIUM.level)
                ),
                noteId = bundle.getLong(TaskEditorBottomSheet.EXTRA_LINKED_NOTE_ID, -1L)
                    .takeIf { it > 0L }
            )
        }
        setFragmentResultListener(TaskEditorBottomSheet.REQ_TASK_ARCHIVE_TOGGLED) { _, bundle ->
            val taskId = bundle.getLong(TaskEditorBottomSheet.EXTRA_TASK_ID, 0L)
            val archived = bundle.getBoolean(TaskEditorBottomSheet.EXTRA_IS_ARCHIVED, false)
            if (taskId > 0L) {
                viewModel.setTaskArchivedById(taskId, archived)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabSwitcher()
        setupRecyclerView()
        setupSearchAndButtons()
        setupFab()
        observeViewModel()
    }

    override fun onDestroyView() {
        hasRenderedInitialTab = false
        super.onDestroyView()
    }

    private fun setupTabSwitcher() {
        binding.tabTasksActive.setOnClickListener { viewModel.onTabSelected(TaskViewTab.ACTIVE) }
        binding.tabTasksArchived.setOnClickListener { viewModel.onTabSelected(TaskViewTab.ARCHIVED) }
        binding.taskTabInnerContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            positionTabIndicator(currentTab, animate = false)
        }
    }

    private fun setupRecyclerView() {
        taskAdapter = TaskAdapter(
            onTaskToggle = { task, isCompleted ->
                viewModel.setTaskCompletion(task, isCompleted)
            },
            onTaskClick = { task ->
                TaskEditorBottomSheet.newInstance(task)
                    .show(parentFragmentManager, TaskEditorBottomSheet.TAG)
            },
            onTaskLongClick = { task, _ -> showTaskOptionsSheet(task) }
        )

        binding.rvTasks.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = taskAdapter
        }

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return if (viewHolder is TaskAdapter.TaskViewHolder) {
                    super.getSwipeDirs(recyclerView, viewHolder)
                } else {
                    0
                }
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val row = taskAdapter.currentList.getOrNull(pos) as? TaskListItem.TaskRow ?: return
                binding.rvTasks.performHapticSafe(HapticFeedbackConstants.LONG_PRESS)
                viewModel.deleteTask(row.task)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.rvTasks)
    }

    private fun setupSearchAndButtons() {
        binding.btnTasksSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        binding.etSearchTasks.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                binding.btnClearTaskSearch.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                viewModel.onSearchQueryChanged(text)
            }
        })

        binding.btnClearTaskSearch.setOnClickListener {
            binding.etSearchTasks.setText("")
        }

        binding.btnEmptyAddTask.setOnClickListener { openNewTaskSheet() }
    }

    private fun setupFab() {
        binding.expandableFabTasks.setActions(
            listOf(
                ExpandableFabView.FabAction(
                    labelRes = R.string.fab_new_task,
                    iconRes = R.drawable.ic_tasks,
                    onClick = { openNewTaskSheet() }
                )
            )
        )
    }

    private fun observeViewModel() {
        collectWithLifecycle(viewModel.uiState) { state ->
            updateTabSwitcherUi(state.selectedTab)
            taskAdapter.updateFontSize(state.fontSizeOption)
            taskAdapter.submitList(state.items)
            renderEmptyState(state)
        }

        collectWithLifecycle(viewModel.events) { event ->
            when (event) {
                is TaskListEvent.ShowUndoDelete -> {
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        messageRes = R.string.task_deleted_msg,
                        iconRes = R.drawable.ic_delete,
                        duration = Snackbar.LENGTH_LONG,
                        actionLabelRes = R.string.action_undo,
                        onAction = { viewModel.restoreTask(event.deletedTask) }
                    )
                }
                is TaskListEvent.ShowUndoArchive -> {
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        messageRes = if (event.archived) {
                            R.string.task_archived_msg
                        } else {
                            R.string.task_unarchived_msg
                        },
                        iconRes = if (event.archived) R.drawable.ic_folder else R.drawable.ic_restore,
                        duration = Snackbar.LENGTH_LONG,
                        actionLabelRes = R.string.action_undo,
                        onAction = {
                            viewModel.setTaskArchived(event.task, !event.archived, emitUndo = false)
                        }
                    )
                }
                is TaskListEvent.ShowError -> {
                    NotivoSnackbarHelper.show(
                        view = binding.root,
                        message = event.message,
                        isError = true
                    )
                }
            }
        }
    }

    private fun updateTabSwitcherUi(selectedTab: TaskViewTab) {
        val tabChanged = hasRenderedInitialTab && currentTab != selectedTab
        currentTab = selectedTab
        hasRenderedInitialTab = true

        val selectedColor = ContextCompat.getColor(requireContext(), R.color.notivo_on_primary_container)
        val unselectedColor = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        tabViews().forEach { (tab, textView) ->
            textView.setTextColor(if (tab == selectedTab) selectedColor else unselectedColor)
        }
        positionTabIndicator(selectedTab, animate = tabChanged)
    }

    private fun positionTabIndicator(tab: TaskViewTab, animate: Boolean) {
        val targetView = tabViews().first { it.first == tab }.second
        val targetWidth = targetView.width
        if (targetWidth <= 0) return

        val lp = binding.viewTaskTabIndicator.layoutParams
        if (lp.width != targetWidth) {
            lp.width = targetWidth
            binding.viewTaskTabIndicator.layoutParams = lp
        }
        val targetX = targetView.left.toFloat()
        val indicator = binding.viewTaskTabIndicator
        indicator.animate().cancel()
        if (animate && requireContext().areAnimationsEnabled()) {
            indicator.animate()
                .translationX(targetX)
                .setDuration(220L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } else {
            indicator.translationX = targetX
        }
    }

    private fun tabViews() = listOf(
        TaskViewTab.ACTIVE to binding.tabTasksActive,
        TaskViewTab.ARCHIVED to binding.tabTasksArchived
    )

    private fun renderEmptyState(state: TaskListUiState) {
        val showEmpty = !state.isLoading && state.items.isEmpty()
        binding.emptyTasksContainer.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.rvTasks.visibility = if (showEmpty) View.GONE else View.VISIBLE
        if (!showEmpty) return

        when (state.selectedTab) {
            TaskViewTab.ACTIVE -> {
                binding.tvEmptyTasksTitle.setText(R.string.tasks_empty_title)
                binding.tvEmptyTasksSubtitle.setText(R.string.tasks_empty_subtitle)
                binding.btnEmptyAddTask.visibility =
                    if (state.searchQuery.isBlank()) View.VISIBLE else View.GONE
            }
            TaskViewTab.ARCHIVED -> {
                binding.tvEmptyTasksTitle.setText(R.string.tasks_archived_empty_title)
                binding.tvEmptyTasksSubtitle.setText(R.string.tasks_archived_empty_subtitle)
                binding.btnEmptyAddTask.visibility = View.GONE
            }
        }
    }

    private fun showTaskOptionsSheet(task: Task) {
        val isInArchive = task.isCompleted || task.isArchived
        val statusTitle = getString(
            if (isInArchive) R.string.task_action_reopen else R.string.task_action_complete
        )
        val statusIcon = if (isInArchive) R.drawable.ic_restore else R.drawable.ic_check

        NotivoOptionSheet.show(
            context = requireContext(),
            title = task.title.ifBlank { getString(R.string.task_options_title) },
            subtitle = task.dueDate?.let { DateUtils.formatTaskDueDate(it) },
            options = listOf(
                NotivoOptionSheet.OptionItem(
                    id = OPTION_EDIT,
                    title = getString(R.string.task_edit_title),
                    iconRes = R.drawable.ic_notes
                ),
                NotivoOptionSheet.OptionItem(
                    id = OPTION_TOGGLE_DONE,
                    title = statusTitle,
                    iconRes = statusIcon
                ),
                NotivoOptionSheet.OptionItem(
                    id = OPTION_DELETE,
                    title = getString(R.string.action_delete),
                    iconRes = R.drawable.ic_delete,
                    isDestructive = true
                )
            )
        ) { selected ->
            when (selected.id) {
                OPTION_EDIT -> TaskEditorBottomSheet.newInstance(task)
                    .show(parentFragmentManager, TaskEditorBottomSheet.TAG)
                OPTION_TOGGLE_DONE -> if (isInArchive) {
                    viewModel.restoreToActive(task)
                } else {
                    viewModel.setTaskCompletion(task, true)
                }
                OPTION_DELETE -> viewModel.deleteTask(task)
            }
        }
    }

    private fun openNewTaskSheet() {
        TaskEditorBottomSheet.newInstance(null)
            .show(parentFragmentManager, TaskEditorBottomSheet.TAG)
    }

    companion object {
        private const val OPTION_EDIT = 1
        private const val OPTION_TOGGLE_DONE = 2
        private const val OPTION_DELETE = 3
    }
}
