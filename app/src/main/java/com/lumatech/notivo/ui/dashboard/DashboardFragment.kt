package com.lumatech.notivo.ui.dashboard

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.lumatech.notivo.R
import com.lumatech.notivo.databinding.FragmentDashboardBinding
import com.lumatech.notivo.databinding.ItemTaskRowBinding
import com.lumatech.notivo.domain.model.Note
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.model.TaskPriority
import com.lumatech.notivo.ui.MainActivity
import com.lumatech.notivo.ui.common.BindingFragment
import com.lumatech.notivo.ui.common.ExpandableFabView
import com.lumatech.notivo.ui.common.NotivoViewModelFactory
import com.lumatech.notivo.ui.common.collectWithLifecycle
import com.lumatech.notivo.ui.common.colorRes
import com.lumatech.notivo.ui.common.navigateSafely
import com.lumatech.notivo.ui.common.performConfirmHaptic
import com.lumatech.notivo.ui.common.serviceLocator
import com.lumatech.notivo.ui.notes.list.NoteAdapter
import com.lumatech.notivo.ui.security.AuthGateHelper
import com.lumatech.notivo.ui.tasks.TaskEditorBottomSheet
import com.lumatech.notivo.util.DateUtils

/**
 * Dashboard home tab displaying personal greeting, live counts, upcoming tasks, and recent notes.
 */
class DashboardFragment : BindingFragment<FragmentDashboardBinding>(
    FragmentDashboardBinding::inflate
) {
    private val viewModel: DashboardViewModel by viewModels {
        NotivoViewModelFactory(serviceLocator)
    }

    private lateinit var recentNotesAdapter: NoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFragmentResultListener(REQ_OPEN_DASHBOARD_LOCKED_NOTE) { _, bundle ->
            if (bundle.getBoolean(AuthGateHelper.EXTRA_AUTHORIZED, false)) {
                val payload = bundle.getBundle(AuthGateHelper.EXTRA_PAYLOAD)
                val noteId = payload?.getLong(EXTRA_NOTE_ID, 0L) ?: 0L
                if (noteId > 0L) {
                    openEditor(noteId, authorized = true)
                }
            }
        }

        setFragmentResultListener(TaskEditorBottomSheet.REQ_TASK_SAVED) { _, bundle ->
            viewModel.createTask(
                title = bundle.getString(TaskEditorBottomSheet.EXTRA_TASK_TITLE).orEmpty(),
                description = bundle.getString(TaskEditorBottomSheet.EXTRA_TASK_DESC),
                dueDate = bundle.getLong(TaskEditorBottomSheet.EXTRA_TASK_DUE_DATE, -1L)
                    .takeIf { it > 0L },
                priority = TaskPriority.fromLevel(
                    bundle.getInt(TaskEditorBottomSheet.EXTRA_TASK_PRIORITY, TaskPriority.MEDIUM.level)
                )
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecentNotesRecycler()
        setupActions()
        setupFab()
        observeViewModel()
    }

    private fun setupRecentNotesRecycler() {
        recentNotesAdapter = NoteAdapter(
            compactHorizontal = false,
            onNoteClick = { note -> handleRecentNoteClick(note) }
        )
        val spanCount = resources.getInteger(R.integer.notes_grid_span_count)
        binding.rvRecentNotes.apply {
            layoutManager = StaggeredGridLayoutManager(
                spanCount,
                StaggeredGridLayoutManager.VERTICAL
            ).apply {
                gapStrategy = StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS
            }
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            itemAnimator = null
            adapter = recentNotesAdapter
        }
    }

    private fun setupActions() {
        binding.btnDashboardSettings.setOnClickListener {
            navigateSafely(R.id.settingsFragment)
        }
        binding.statNotesBox.setOnClickListener {
            (activity as? MainActivity)?.switchBottomTab(R.id.noteListFragment)
        }
        binding.statTasksBox.setOnClickListener {
            (activity as? MainActivity)?.switchBottomTab(R.id.taskListFragment)
        }
        binding.statLockedBox.setOnClickListener {
            (activity as? MainActivity)?.switchBottomTab(R.id.noteListFragment)
        }
        binding.btnSeeAllTasks.setOnClickListener {
            (activity as? MainActivity)?.switchBottomTab(R.id.taskListFragment)
        }
        binding.btnSeeAllNotes.setOnClickListener {
            (activity as? MainActivity)?.switchBottomTab(R.id.noteListFragment)
        }
    }

    private fun setupFab() {
        binding.expandableFabDashboard.setActions(
            listOf(
                ExpandableFabView.FabAction(
                    labelRes = R.string.fab_new_note,
                    iconRes = R.drawable.ic_note_add,
                    onClick = { openEditor(noteId = 0L, authorized = false) }
                ),
                ExpandableFabView.FabAction(
                    labelRes = R.string.fab_new_task,
                    iconRes = R.drawable.ic_tasks,
                    onClick = {
                        TaskEditorBottomSheet.newInstance(null)
                            .show(parentFragmentManager, TaskEditorBottomSheet.TAG)
                    }
                )
            )
        )
    }

    private fun observeViewModel() {
        collectWithLifecycle(viewModel.uiState) { state ->
            val summary = state.summary
            binding.tvDashboardGreeting.setText(R.string.nav_dashboard)
            binding.tvDashboardSubtitle.text = if (summary.pendingTasks > 0) {
                getString(R.string.dashboard_subtitle_pending, summary.pendingTasks)
            } else {
                getString(R.string.dashboard_subtitle_clear)
            }

            binding.tvStatNotesCount.text = summary.totalNotes.toString()
            binding.tvStatTasksCount.text = summary.pendingTasks.toString()
            binding.tvStatLockedCount.text = summary.lockedNotes.toString()

            renderUpcomingTasks(summary.upcomingTasks, state.fontSizeOption.scaleFactor)

            val privateFolderIds = summary.folders.filter { it.isPrivate }.map { it.id }.toSet()
            val publicRecentNotes = summary.recentNotes.filter { note ->
                note.folderId == null || note.folderId !in privateFolderIds
            }
            recentNotesAdapter.updateMetadata(summary.folders, state.fontSizeOption)
            recentNotesAdapter.submitList(publicRecentNotes)

            val hasRecentNotes = publicRecentNotes.isNotEmpty()
            binding.rvRecentNotes.visibility = if (hasRecentNotes) View.VISIBLE else View.GONE
            binding.tvDashboardNoNotes.visibility = if (hasRecentNotes) View.GONE else View.VISIBLE
        }
    }

    private fun renderUpcomingTasks(tasks: List<Task>, fontScale: Float) {
        val container = binding.dashboardUpcomingTasksList
        container.removeAllViews()

        if (tasks.isEmpty()) {
            binding.tvDashboardNoTasks.visibility = View.VISIBLE
            return
        }
        binding.tvDashboardNoTasks.visibility = View.GONE

        val inflater = LayoutInflater.from(requireContext())
        tasks.forEach { task ->
            val rowBinding = ItemTaskRowBinding.inflate(inflater, container, false)
            rowBinding.cbTaskDone.isChecked = task.isCompleted
            rowBinding.tvTaskTitle.text = task.title
            rowBinding.tvTaskTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * fontScale)
            rowBinding.tvTaskMeta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f * fontScale)
            task.dueDate?.let { due ->
                rowBinding.tvTaskMeta.visibility = View.VISIBLE
                rowBinding.tvTaskMeta.text = DateUtils.formatTaskDueDate(due)
            }
            rowBinding.viewPriorityDot.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), task.priority.colorRes())
            )
            rowBinding.cbTaskDone.setOnCheckedChangeListener { buttonView, _ ->
                buttonView.performConfirmHaptic()
                viewModel.toggleTaskCompletion(task)
            }
            rowBinding.taskCardRoot.setOnClickListener {
                (activity as? MainActivity)?.switchBottomTab(R.id.taskListFragment)
            }
            container.addView(rowBinding.root)
        }
    }

    private fun handleRecentNoteClick(note: Note) {
        if (!note.isLocked) {
            openEditor(note.id, authorized = false)
            return
        }

        AuthGateHelper.requestUnlock(
            fragment = this,
            requestKey = REQ_OPEN_DASHBOARD_LOCKED_NOTE,
            subtitle = note.title.ifBlank { getString(R.string.note_locked_preview) },
            payload = Bundle().apply { putLong(EXTRA_NOTE_ID, note.id) },
            onBiometricSuccess = { openEditor(note.id, authorized = true) }
        )
    }

    private fun openEditor(noteId: Long, authorized: Boolean) {
        navigateSafely(
            R.id.noteEditorFragment,
            Bundle().apply {
                putLong("noteId", noteId)
                putLong("folderId", -1L)
                putBoolean("authorized", authorized)
            }
        )
    }

    companion object {
        private const val REQ_OPEN_DASHBOARD_LOCKED_NOTE = "req_open_dashboard_locked_note"
        private const val EXTRA_NOTE_ID = "extra_note_id"
    }
}
