package com.mallorca.explorer.core.domain.usecase.place

import app.cash.turbine.test
import com.mallorca.explorer.core.domain.model.Category
import com.mallorca.explorer.core.domain.model.LatLng
import com.mallorca.explorer.core.domain.model.Place
import com.mallorca.explorer.core.domain.repository.PlaceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPlacesTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val placeRepository: PlaceRepository = mockk()
    private val searchPlaces = SearchPlaces(placeRepository, dispatcher)

    private fun place(name: String) = Place(
        id = name,
        name = name,
        nameEs = name,
        description = "",
        category = Category.BEACH,
        location = LatLng(39.7, 2.9),
        municipality = "Palma",
    )

    @Test
    fun `delegates trimmed query to repository`() = runTest {
        val expected = listOf(place("Cala Bona"))
        every { placeRepository.searchPlaces("cala") } returns flowOf(expected)

        searchPlaces("  cala  ").test {
            assertEquals(expected, awaitItem())
            awaitComplete()
        }

        verify(exactly = 1) { placeRepository.searchPlaces("cala") }
    }

    @Test
    fun `whitespace-only query is trimmed to empty string`() = runTest {
        every { placeRepository.searchPlaces("") } returns flowOf(emptyList())

        searchPlaces("   ").test {
            assertEquals(emptyList<Place>(), awaitItem())
            awaitComplete()
        }

        verify(exactly = 1) { placeRepository.searchPlaces("") }
    }

    @Test
    fun `returns multiple results from repository`() = runTest {
        val results = listOf(place("Cala Millor"), place("Cala Ratjada"), place("Cala Agulla"))
        every { placeRepository.searchPlaces("cala") } returns flowOf(results)

        searchPlaces("cala").test {
            val items = awaitItem()
            assertEquals(3, items.size)
            assertEquals("Cala Millor", items[0].name)
            awaitComplete()
        }
    }

    @Test
    fun `empty repository result is forwarded`() = runTest {
        every { placeRepository.searchPlaces("xyz") } returns flowOf(emptyList())

        searchPlaces("xyz").test {
            assertEquals(emptyList<Place>(), awaitItem())
            awaitComplete()
        }
    }
}
