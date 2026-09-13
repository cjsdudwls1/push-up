package com.pushuprpg.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pushuprpg.app.AppContainer
import com.pushuprpg.app.domain.EntitlementRepository
import com.pushuprpg.app.domain.ProgressRepository
import com.pushuprpg.app.domain.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId

class HomeViewModel(
    progressRepository: ProgressRepository,
    sessionRepository: SessionRepository,
    entitlementRepository: EntitlementRepository,
) : ViewModel() {

    private val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    val state: StateFlow<HomeUiState> = combine(
        progressRepository.progress,
        sessionRepository.dailyTotals(days = 2).map { totals ->
            totals.firstOrNull { it.epochDay == today }?.reps ?: 0
        },
        entitlementRepository.entitlement,
    ) { progress, todayReps, entitlement ->
        HomeUiState(progress, todayReps, entitlement, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    container.progressRepository,
                    container.sessionRepository,
                    container.entitlementRepository,
                )
            }
        }
    }
}
