package com.mallorca.explorer.core.data.repository

import app.cash.turbine.test
import com.mallorca.explorer.core.data.database.dao.EventDao
import com.mallorca.explorer.core.data.database.entity.EventEntity
import com.mallorca.explorer.core.data.datastore.UserPreferencesDataStore
import com.mallorca.explorer.core.data.firebase.EventRemoteDataSource
import com.mallorca.explorer.core.data.sync.NetworkMonitor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EventRepositoryImplTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val eventDao: EventDao = mockk(relaxUnitFun = true)
    private val eventRemoteDataSource: EventRemoteDataSource = mockk()
    private val networkMonitor: NetworkMonitor = mockk()
    private val prefsDataStore: UserPreferencesDataStore = mockk(relaxUnitFun = true)

    private val repo = EventRepositoryImpl(eventDao, eventRemoteDataSource, networkMonitor, prefsDataStore, dispatcher)

    private val now get() = System.currentTimeMillis()

    private fun futureEvent(id: String = "evt-1") = EventEntity(
        id = id, title = "Title", titleEs = "Título", titleDe = "", titleRu = "", titleZh = "",
        description = "", descriptionEs = "", descriptionDe = "", descriptionRu = "", descriptionZh = "",
        category = "FESTIVAL",
        startDateEpoch = now + 86_400_000L, endDateEpoch = null,
        municipality = "Palma", address = null,
        isFree = true, price = null, imageUrl = null,
        isRecurring = false, recurringDayOfWeek = null,
    )

    // --- TTL fresh: no Firestore call ---

    @Test
    fun `fresh TTL skips Firestore fetch and emits Room data`() = runTest {
        every { prefsDataStore.eventsLastSyncedEpoch } returns flowOf(now)
        every { eventDao.getAll() } returns flowOf(listOf(futureEvent()))

        repo.getUpcomingEvents().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            awaitComplete()
        }

        coVerify(exactly = 0) { eventRemoteDataSource.fetchAll() }
    }

    // --- TTL expired but offline: no Firestore call ---

    @Test
    fun `expired TTL but offline skips Firestore fetch`() = runTest {
        every { prefsDataStore.eventsLastSyncedEpoch } returns flowOf(0L)
        every { networkMonitor.isOnline } returns flowOf(false)
        every { eventDao.getAll() } returns flowOf(listOf(futureEvent()))

        repo.getUpcomingEvents().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            awaitComplete()
        }

        coVerify(exactly = 0) { eventRemoteDataSource.fetchAll() }
    }

    // --- TTL expired, online, fetch succeeds with data: replace Room + update TTL ---

    @Test
    fun `expired TTL online and non-empty fetch replaces Room and updates TTL`() = runTest {
        every { prefsDataStore.eventsLastSyncedEpoch } returns flowOf(0L)
        every { networkMonitor.isOnline } returns flowOf(true)
        val remote = listOf(futureEvent("evt-remote"))
        coEvery { eventRemoteDataSource.fetchAll() } returns remote
        every { eventDao.getAll() } returns flowOf(remote)

        repo.getUpcomingEvents().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("evt-remote", result.first().id)
            awaitComplete()
        }

        coVerify(exactly = 1) { eventDao.replaceAll(remote) }
        coVerify(exactly = 1) { prefsDataStore.setEventsLastSyncedEpoch(any()) }
    }

    // --- TTL expired, online, fetch succeeds but empty: guard skips replace ---

    @Test
    fun `empty Firestore result does not wipe local Room data`() = runTest {
        every { prefsDataStore.eventsLastSyncedEpoch } returns flowOf(0L)
        every { networkMonitor.isOnline } returns flowOf(true)
        coEvery { eventRemoteDataSource.fetchAll() } returns emptyList()
        every { eventDao.getAll() } returns flowOf(listOf(futureEvent()))

        repo.getUpcomingEvents().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            awaitComplete()
        }

        coVerify(exactly = 0) { eventDao.replaceAll(any()) }
        coVerify(exactly = 0) { prefsDataStore.setEventsLastSyncedEpoch(any()) }
    }

    // --- TTL expired, online, fetch throws: Room untouched, TTL not updated ---

    @Test
    fun `Firestore fetch failure keeps cached Room data and does not update TTL`() = runTest {
        every { prefsDataStore.eventsLastSyncedEpoch } returns flowOf(0L)
        every { networkMonitor.isOnline } returns flowOf(true)
        coEvery { eventRemoteDataSource.fetchAll() } throws RuntimeException("network error")
        every { eventDao.getAll() } returns flowOf(listOf(futureEvent()))

        repo.getUpcomingEvents().test {
            val result = awaitItem()
            assertEquals(1, result.size)
            awaitComplete()
        }

        coVerify(exactly = 0) { eventDao.replaceAll(any()) }
        coVerify(exactly = 0) { prefsDataStore.setEventsLastSyncedEpoch(any()) }
    }

    // --- Date filter still applies regardless of data origin ---

    @Test
    fun `past non-recurring events are filtered out`() = runTest {
        every { prefsDataStore.eventsLastSyncedEpoch } returns flowOf(now)
        val past = futureEvent("evt-past").copy(startDateEpoch = now - 10 * 86_400_000L, endDateEpoch = now - 9 * 86_400_000L)
        val recurringPast = futureEvent("evt-recurring").copy(startDateEpoch = now - 10 * 86_400_000L, isRecurring = true)
        every { eventDao.getAll() } returns flowOf(listOf(past, recurringPast, futureEvent("evt-future")))

        repo.getUpcomingEvents().test {
            val result = awaitItem()
            assertEquals(setOf("evt-recurring", "evt-future"), result.map { it.id }.toSet())
            awaitComplete()
        }
    }
}
