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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    progressRepository: ProgressRepository,
    sessionRepository: SessionRepository,
    entitlementRepository: EntitlementRepository,
) : ViewModel() {

    private fun today(): Long =
        Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    /**
     * The day the hub is read against, kept current by [followToday].
     *
     * This ViewModel is scoped to the home back-stack entry, so it outlives midnight. The day used to
     * be read only when Room emitted, and nothing is written overnight: a phone left on the hub in
     * the evening opened the next morning on yesterday's count as 오늘, with no nudge, and a return
     * after days away showed the streak unbroken.
     */
    private val day = MutableStateFlow(today())

    val state: StateFlow<HomeUiState> = combine(
        progressRepository.progress,
        // Subscribed again when the day turns: the totals' window is fixed when it is collected.
        day.flatMapLatest { epochDay ->
            sessionRepository.dailyTotals(days = 2).map { totals ->
                totals.firstOrNull { it.epochDay == epochDay } ?: DailyTotal(epochDay, reps = 0, activeMs = 0L)
            }
        },
        entitlementRepository.entitlement,
    ) { progress, todayTotal, entitlement ->
        HomeUiState(
            progress = progress,
            todayReps = todayTotal.reps,
            todayActiveMs = todayTotal.activeMs,
            entitlement = entitlement,
            loading = false,
            today = todayTotal.epochDay,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /**
     * Keeps [day] current for as long as it runs: read now, and again at each midnight. The hub runs
     * it while it is in view, so coming back to it from the background reads the day again as well.
     */
    suspend fun followToday() {
        while (true) {
            day.value = today()
            val now = ZonedDateTime.now()
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            delay(Duration.between(now, midnight).toMillis())
        }
    }

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
