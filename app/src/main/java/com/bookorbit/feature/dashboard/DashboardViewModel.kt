package com.bookorbit.feature.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bookorbit.core.model.BookCard
import com.bookorbit.core.model.ReadingGoalWidget
import com.bookorbit.core.model.ReadingStreakWidget
import com.bookorbit.core.model.ScrollerType
import com.bookorbit.core.network.Connectivity
import com.bookorbit.feature.browse.BrowseRepository
import com.bookorbit.feature.downloads.OnDeviceBooks
import com.bookorbit.feature.reader.ReaderProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: BrowseRepository,
    readerProgress: ReaderProgressRepository,
    onDeviceBooks: OnDeviceBooks,
    private val connectivity: Connectivity,
) : ViewModel() {

    data class UiState(
        val continueReading: List<BookCard> = emptyList(),
        val continueListening: List<BookCard> = emptyList(),
        val recentlyAdded: List<BookCard> = emptyList(),
        val loading: Boolean = true,
        /** True when every scroller request failed while online — distinct from a genuinely empty library. */
        val error: Boolean = false,
        /** The server can't be reached: Home leads with the books on the device, then what it last loaded. */
        val offline: Boolean = false,
        /** Downloaded books, most recently read first. */
        val onDevice: List<BookCard> = emptyList(),
        /** Null when the server predates the widget endpoints (or the call failed): the strip is simply hidden. */
        val streak: ReadingStreakWidget? = null,
        val goal: ReadingGoalWidget? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    /** Reading positions saved so far; the screen hands it back to [onProgressSaved] while on screen. */
    val progressSaves = readerProgress.changes
    private var seenSaves = readerProgress.changes.value

    init {
        refresh()
        viewModelScope.launch { onDeviceBooks.books.collect { books -> _ui.update { it.copy(onDevice = books) } } }
        viewModelScope.launch {
            connectivity.offline.drop(1).collect { offline ->
                _ui.update { it.copy(offline = offline) }
                // Back in touch with the server: swap what was kept for what's current.
                if (!offline) load(quiet = true)
            }
        }
    }

    /** A pull to refresh: reload with the spinner showing. */
    fun refresh() = load(quiet = false)

    /** A reader saved a position since the screen last looked: reload, quietly, to show it. */
    fun onProgressSaved(saves: Int) {
        if (saves == seenSaves) return
        seenSaves = saves
        load(quiet = true)
    }

    private fun load(quiet: Boolean) {
        if (!quiet) _ui.update { it.copy(loading = true) }
        viewModelScope.launch {
            var failures = 0
            suspend fun load(type: String): List<BookCard> =
                runCatching { repo.scroller(type) }
                    .onFailure {
                        failures++
                        Log.w("Dashboard", "scroller $type failed", it)
                    }
                    .getOrDefault(emptyList())

            val streak = async { runCatching { repo.readingStreak() }.getOrNull() }
            val goal = async { runCatching { repo.readingGoal() }.getOrNull() }
            val continueReading = async { load(ScrollerType.CONTINUE_READING) }
            val continueListening = async { load(ScrollerType.CONTINUE_LISTENING) }
            val recentlyAdded = async { load(ScrollerType.RECENTLY_ADDED) }
            val reading = continueReading.await()
            val listening = continueListening.await()
            val recent = recentlyAdded.await()
            val offline = connectivity.offline.value
            _ui.update {
                it.copy(
                    continueReading = reading,
                    continueListening = listening,
                    recentlyAdded = recent,
                    loading = false,
                    error = failures == 3 && !offline,
                    offline = offline,
                    streak = streak.await(),
                    goal = goal.await(),
                )
            }
        }
    }
}
