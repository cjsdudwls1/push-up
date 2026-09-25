package com.pushuprpg.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pushuprpg.app.AppContainer
import com.pushuprpg.app.domain.DailyTotal
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

    // Resolved per emission rather than once at construction: this ViewModel is scoped to the home
    // back-stack entry, so it outlives midnight, and a captured date would leave the hub showing
    // yesterday's count on the day a streak is most at risk.
    private fun today(): Long =
        Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    val state: StateFlow<HomeUiState> = combine(
        progressRepository.progress,
        sessionRepository.dailyTotals(days = 2).map { totals ->
            val day = today()
            totals.firstOrNull { it.epochDay == day } ?: DailyTotal(day, reps = 0, activeMs = 0L)
        },
        entitlementRepository.entitlement,
    ) { progress, todayTotal, entitlement ->
        HomeUiState(
            progress = progress,
            todayReps = todayTotal.reps,
            todayActiveMs = todayTotal.activeMs,
            entitlement = entitlement,
            loading = false,
        )
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
