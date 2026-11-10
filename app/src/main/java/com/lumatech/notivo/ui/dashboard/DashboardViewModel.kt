package com.lumatech.notivo.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumatech.notivo.domain.model.DashboardSummary
import com.lumatech.notivo.domain.model.FontSizeOption
import com.lumatech.notivo.domain.model.Task
import com.lumatech.notivo.domain.model.TaskPriority
import com.lumatech.notivo.domain.repository.PreferencesRepository
import com.lumatech.notivo.domain.usecase.dashboard.GetDashboardSummaryUseCase
import com.lumatech.notivo.domain.usecase.task.SaveTaskUseCase
import com.lumatech.notivo.domain.usecase.task.ToggleTaskUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val fontSizeOption: FontSizeOption = FontSizeOption.MEDIUM,
    val summary: DashboardSummary = DashboardSummary(
        totalNotes = 0,
        pendingTasks = 0,
        lockedNotes = 0,
        upcomingTasks = emptyList(),
        recentNotes = emptyList(),
        folders = emptyList()
    ),
    val isLoading: Boolean = true
)

class DashboardViewModel(
    getDashboardSummaryUseCase: GetDashboardSummaryUseCase,
    private val toggleTaskUseCase: ToggleTaskUseCase,
    private val saveTaskUseCase: SaveTaskUseCase,
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        getDashboardSummaryUseCase(),
        preferencesRepository.observePreferences()
    ) { summary, prefs ->
        DashboardUiState(
            fontSizeOption = prefs.fontSizeOption,
            summary = summary,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            toggleTaskUseCase(task.id, !task.isCompleted)
        }
    }

    fun createTask(title: String, description: String?, dueDate: Long?, priority: TaskPriority) {
        viewModelScope.launch {
            saveTaskUseCase(
                Task(
                    title = title,
                    description = description,
                    dueDate = dueDate,
                    priority = priority
                )
            )
        }
    }
}
