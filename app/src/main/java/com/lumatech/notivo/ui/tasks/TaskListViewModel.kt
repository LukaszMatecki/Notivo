package com.lumatech.notivo.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumatech.notivo.R
import com.lumatech.notivo.domain.model.FontSizeOption
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.model.TaskPriority
import com.lumatech.notivo.domain.repository.PreferencesRepository
import com.lumatech.notivo.domain.usecase.task.ArchiveTaskUseCase
import com.lumatech.notivo.domain.usecase.task.DeleteTaskUseCase
import com.lumatech.notivo.domain.usecase.task.GetTasksUseCase
import com.lumatech.notivo.domain.usecase.task.SaveTaskUseCase
import com.lumatech.notivo.domain.usecase.task.ToggleTaskUseCase
import com.lumatech.notivo.util.Constants
import com.lumatech.notivo.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskViewTab {
    ACTIVE,
    ARCHIVED
}

/**
 * Display items for grouped task RecyclerView.
 */
sealed interface TaskListItem {
    data class SectionHeader(val titleRes: Int, val count: Int) : TaskListItem
    data class TaskRow(val task: Task, val highlightHighPriority: Boolean) : TaskListItem
}

data class TaskListUiState(
    val items: List<TaskListItem> = emptyList(),
    val selectedTab: TaskViewTab = TaskViewTab.ACTIVE,
    val searchQuery: String = "",
    val fontSizeOption: FontSizeOption = FontSizeOption.MEDIUM,
    val isLoading: Boolean = true
)

sealed interface TaskListEvent {
    data class ShowUndoDelete(val deletedTask: Task) : TaskListEvent
    data class ShowUndoArchive(val task: Task, val archived: Boolean) : TaskListEvent
    data class ShowError(val message: String) : TaskListEvent
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TaskListViewModel(
    private val getTasksUseCase: GetTasksUseCase,
    private val saveTaskUseCase: SaveTaskUseCase,
    private val toggleTaskUseCase: ToggleTaskUseCase,
    private val archiveTaskUseCase: ArchiveTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val selectedTabFlow = MutableStateFlow(TaskViewTab.ACTIVE)
    private val _events = Channel<TaskListEvent>(Channel.BUFFERED)
    val events: Flow<TaskListEvent> = _events.receiveAsFlow()

    @Volatile
    private var cachedTasksById: Map<Long, Task> = emptyMap()

    private val tasksFlow = searchQueryFlow
        .debounce(Constants.SEARCH_DEBOUNCE_MS)
        .flatMapLatest { query -> getTasksUseCase(query) }

    val uiState: StateFlow<TaskListUiState> = combine(
        tasksFlow,
        selectedTabFlow,
        searchQueryFlow,
        preferencesRepository.observePreferences()
    ) { tasks, selectedTab, rawQuery, prefs ->
        cachedTasksById = tasks.associateBy { it.id }
        val grouped = buildGroupedItems(tasks, selectedTab, prefs.highPriorityNotifications)
        TaskListUiState(
            items = grouped,
            selectedTab = selectedTab,
            searchQuery = rawQuery,
            fontSizeOption = prefs.fontSizeOption,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TaskListUiState()
    )

    fun onSearchQueryChanged(query: String) {
        searchQueryFlow.value = query
    }

    fun onTabSelected(tab: TaskViewTab) {
        selectedTabFlow.value = tab
    }

    fun saveTask(
        existingId: Long,
        title: String,
        description: String?,
        dueDate: Long?,
        priority: TaskPriority,
        noteId: Long? = null
    ) {
        viewModelScope.launch {
            val existing = if (existingId > 0L) cachedTasksById[existingId] else null
            val task = Task(
                id = existingId,
                title = title,
                description = description,
                dueDate = dueDate,
                priority = priority,
                isCompleted = existing?.isCompleted ?: false,
                isArchived = existing?.isArchived ?: false,
                noteId = noteId,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            saveTaskUseCase(task).onFailure {
                _events.send(TaskListEvent.ShowError("Podaj nazwę zadania."))
            }
        }
    }

    fun setTaskCompletion(task: Task, completed: Boolean) {
        viewModelScope.launch {
            toggleTaskUseCase(task.id, completed)
            archiveTaskUseCase(task.id, completed)
        }
    }

    fun restoreToActive(task: Task) {
        viewModelScope.launch {
            toggleTaskUseCase(task.id, false)
            archiveTaskUseCase(task.id, false)
        }
    }

    fun setTaskArchived(task: Task, archived: Boolean, emitUndo: Boolean = true) {
        viewModelScope.launch {
            if (!archived) {
                toggleTaskUseCase(task.id, false)
            }
            archiveTaskUseCase(task.id, archived).onSuccess {
                if (emitUndo) {
                    _events.send(TaskListEvent.ShowUndoArchive(task, archived))
                }
            }
        }
    }

    fun setTaskArchivedById(taskId: Long, archived: Boolean) {
        viewModelScope.launch {
            if (!archived) {
                toggleTaskUseCase(taskId, false)
            }
            archiveTaskUseCase(taskId, archived).onSuccess {
                val existing = cachedTasksById[taskId] ?: Task(id = taskId, title = "")
                _events.send(TaskListEvent.ShowUndoArchive(existing, archived))
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            deleteTaskUseCase(task.id).onSuccess {
                _events.send(TaskListEvent.ShowUndoDelete(task))
            }
        }
    }

    fun restoreTask(task: Task) {
        viewModelScope.launch {
            saveTaskUseCase(task)
        }
    }

    private fun buildGroupedItems(
        tasks: List<Task>,
        selectedTab: TaskViewTab,
        highlightHighPriority: Boolean
    ): List<TaskListItem> {
        if (tasks.isEmpty()) return emptyList()

        return when (selectedTab) {
            TaskViewTab.ACTIVE -> buildActiveSections(
                tasks = tasks.filter { !it.isArchived && !it.isCompleted },
                highlightHighPriority = highlightHighPriority
            )
            TaskViewTab.ARCHIVED -> buildList {
                appendSection(
                    titleRes = R.string.tasks_section_archived,
                    sectionTasks = tasks.filter { it.isArchived || it.isCompleted },
                    highlightHighPriority = highlightHighPriority
                )
            }
        }
    }

    private fun buildActiveSections(
        tasks: List<Task>,
        highlightHighPriority: Boolean
    ): List<TaskListItem> {
        val endOfToday = DateUtils.endOfTodayMillis()
        val todayAndOverdue = mutableListOf<Task>()
        val upcoming = mutableListOf<Task>()
        val noDate = mutableListOf<Task>()

        for (task in tasks) {
            when {
                task.dueDate == null -> noDate.add(task)
                task.dueDate <= endOfToday -> todayAndOverdue.add(task)
                else -> upcoming.add(task)
            }
        }

        return buildList {
            appendSection(R.string.tasks_section_today, todayAndOverdue, highlightHighPriority)
            appendSection(R.string.tasks_section_upcoming, upcoming, highlightHighPriority)
            appendSection(R.string.tasks_section_no_date, noDate, highlightHighPriority)
        }
    }

    private fun MutableList<TaskListItem>.appendSection(
        titleRes: Int,
        sectionTasks: List<Task>,
        highlightHighPriority: Boolean
    ) {
        if (sectionTasks.isEmpty()) return
        add(TaskListItem.SectionHeader(titleRes, sectionTasks.size))
        sectionTasks.forEach { task ->
            add(TaskListItem.TaskRow(task, highlightHighPriority))
        }
    }
}
