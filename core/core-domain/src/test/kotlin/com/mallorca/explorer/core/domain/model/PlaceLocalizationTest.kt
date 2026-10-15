package com.mallorca.explorer.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceLocalizationTest {

    private fun place(
        name: String = "Base Name",
        nameEs: String = "Nombre ES",
        nameDe: String = "Name DE",
        nameRu: String = "",
        nameZh: String = "",
        description: String = "Base description",
        descriptionEs: String = "Descripción ES",
        descriptionDe: String = "Beschreibung DE",
        tips: List<String> = listOf("Base tip"),
        tipsEs: List<String> = listOf("Consejo ES"),
        tipsDe: List<String> = listOf("Tipp DE"),
    ) = Place(
        id = "p1",
        name = name,
        nameEs = nameEs,
        nameDe = nameDe,
        nameRu = nameRu,
        nameZh = nameZh,
        description = description,
        descriptionEs = descriptionEs,
        descriptionDe = descriptionDe,
        tips = tips,
        tipsEs = tipsEs,
        tipsDe = tipsDe,
        category = Category.BEACH,
        location = LatLng(39.0, 3.0),
        municipality = "Santanyí",
    )

    @Test
    fun `localizedName returns the field for each language`() {
        val p = place()
        assertEquals("Nombre ES", p.localizedName("es"))
        assertEquals("Name DE", p.localizedName("de"))
        assertEquals("Base Name", p.localizedName("en"))
    }

    @Test
    fun `localizedName falls back to base when the language field is empty`() {
        val p = place(nameEs = "", nameDe = "")
        assertEquals("Base Name", p.localizedName("es"))
        assertEquals("Base Name", p.localizedName("de"))
    }

    @Test
    fun `localizedName uses base name for unknown or ru-zh without data`() {
        val p = place(nameRu = "", nameZh = "")
        assertEquals("Base Name", p.localizedName("ru"))
        assertEquals("Base Name", p.localizedName("zh"))
        assertEquals("Base Name", p.localizedName("fr"))
    }

    @Test
    fun `localizedDescription returns field and falls back`() {
        assertEquals("Descripción ES", place().localizedDescription("es"))
        assertEquals("Beschreibung DE", place().localizedDescription("de"))
        assertEquals("Base description", place().localizedDescription("en"))
        assertEquals("Base description", place(descriptionEs = "").localizedDescription("es"))
    }

    @Test
    fun `localizedTips returns field and falls back`() {
        assertEquals(listOf("Consejo ES"), place().localizedTips("es"))
        assertEquals(listOf("Tipp DE"), place().localizedTips("de"))
        assertEquals(listOf("Base tip"), place().localizedTips("en"))
        assertEquals(listOf("Base tip"), place(tipsEs = emptyList()).localizedTips("es"))
    }

    @Test
    fun `normalizeDefault maps system language to es-en-de`() {
        assertEquals("es", com.mallorca.explorer.core.common.LocaleSource.normalizeDefault("es"))
        assertEquals("de", com.mallorca.explorer.core.common.LocaleSource.normalizeDefault("de"))
        assertEquals("en", com.mallorca.explorer.core.common.LocaleSource.normalizeDefault("en"))
        assertEquals("en", com.mallorca.explorer.core.common.LocaleSource.normalizeDefault("fr"))
        assertEquals("en", com.mallorca.explorer.core.common.LocaleSource.normalizeDefault(null))
    }
}
