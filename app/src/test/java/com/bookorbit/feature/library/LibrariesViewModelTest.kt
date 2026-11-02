package com.bookorbit.feature.library

import com.bookorbit.core.model.Library
import com.bookorbit.core.network.Connectivity
import com.bookorbit.feature.browse.BrowseRepository
import com.bookorbit.feature.downloads.OnDeviceBooks
import com.bookorbit.feature.library.filters.FilterPrefsStore
import com.bookorbit.feature.library.filters.StoredFilterPrefs
import com.bookorbit.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LibrariesViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val library = Library(id = 7, name = "Main", createdAt = "2024-01-01", updatedAt = "2024-01-01")

    private val offline = MutableStateFlow(false)

    private fun viewModel(repo: BrowseRepository): LibrariesViewModel {
        val prefs = mockk<FilterPrefsStore>()
        coEvery { prefs.load(any()) } returns StoredFilterPrefs()
        val onDevice = mockk<OnDeviceBooks> { every { books } returns flowOf(emptyList()) }
        val connectivity = mockk<Connectivity> { every { this@mockk.offline } returns this@LibrariesViewModelTest.offline }
        return LibrariesViewModel(repo, prefs, onDevice, connectivity)
    }

    @Test
    fun `a failed library load is an error, not an endless spinner`() {
        val repo = mockk<BrowseRepository>()
        coEvery { repo.libraries() } throws IOException("offline")
        val vm = viewModel(repo)
        assertEquals(LibrariesViewModel.Status.ERROR, vm.status.value)
    }

    @Test
    fun `offline, a library that never loaded shows the books on the device instead of an error`() {
        offline.value = true
        val repo = mockk<BrowseRepository>()
        coEvery { repo.libraries() } throws IOException("offline")
        assertEquals(LibrariesViewModel.Status.OFFLINE, viewModel(repo).status.value)
    }

    @Test
    fun `coming back online reloads a library that couldn't load`() {
        offline.value = true
        val repo = mockk<BrowseRepository>()
        coEvery { repo.libraries() } throws IOException("offline")
        val vm = viewModel(repo)

        coEvery { repo.libraries() } returns listOf(library)
        offline.value = false
        assertEquals(LibrariesViewModel.Status.READY, vm.status.value)
    }

    @Test
    fun `no libraries is its own state`() {
        val repo = mockk<BrowseRepository>()
        coEvery { repo.libraries() } returns emptyList()
        assertEquals(LibrariesViewModel.Status.EMPTY, viewModel(repo).status.value)
    }

    @Test
    fun `a successful load selects the first library`() {
        val repo = mockk<BrowseRepository>()
        coEvery { repo.libraries() } returns listOf(library)
        val vm = viewModel(repo)
        assertEquals(LibrariesViewModel.Status.READY, vm.status.value)
        assertEquals(7, vm.selectedId.value)
    }

    @Test
    fun `retry recovers once the network is back`() {
        val repo = mockk<BrowseRepository>()
        coEvery { repo.libraries() } throws IOException("offline")
        val vm = viewModel(repo)
        assertEquals(LibrariesViewModel.Status.ERROR, vm.status.value)

        coEvery { repo.libraries() } returns listOf(library)
        vm.loadLibraries()
        assertEquals(LibrariesViewModel.Status.READY, vm.status.value)
        assertEquals(7, vm.selectedId.value)
    }
}
