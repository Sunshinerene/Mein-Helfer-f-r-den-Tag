package de.meinhelfer.tag.summary

import de.meinhelfer.tag.data.VitalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Werte aus Health Connect: Doppelte erkennen, Unplausibles aussortieren, Quellen benennen. */
class HealthValuesTest {
    private val t = 1_790_000_000_000L

    @Test
    fun skipsValuesAlreadyThere() {
        val existing = listOf(VitalEntry(personId = 1, atMillis = t + 30_000, day = 0, kind = "blutdruck", value = 135.0, value2 = 80.0))
        val values = listOf(
            HealthValue("blutdruck", 135.0, 80.0, "mmHg", t, "jp.co.omron.healthcare.omron_connect"),
            HealthValue("blutdruck", 128.0, 78.0, "mmHg", t + 3_600_000, "jp.co.omron.healthcare.omron_connect"),
            HealthValue("blutdruck", 128.0, 78.0, "mmHg", t + 3_600_000, "jp.co.omron.healthcare.omron_connect"),
            HealthValue("puls", 72.0, null, "/min", t, "jp.co.omron.healthcare.omron_connect"),
        )
        val fresh = HealthValues.newOnes(values, existing)
        assertEquals(2, fresh.size)
        assertEquals(128.0, fresh[0].value, 0.0)
        assertEquals("puls", fresh[1].kind)
    }

    @Test
    fun plausibilityAndLabels() {
        assertTrue(HealthValues.plausible(HealthValue("gewicht", 72.4, null, "kg", t, "x")))
        assertFalse(HealthValues.plausible(HealthValue("gewicht", 0.0, null, "kg", t, "x")))
        assertTrue(HealthValues.plausible(HealthValue("blutzucker", 6.1, null, "mmol/l", t, "x")))
        assertFalse(HealthValues.plausible(HealthValue("blutzucker", 6.1, null, "mg/dl", t, "x")))
        assertFalse(HealthValues.plausible(HealthValue("schritte", 1000.0, null, "", t, "x")))
        assertEquals("Garmin Connect", HealthValues.label("com.garmin.android.apps.connectmobile"))
        assertEquals("Meine Waage", HealthValues.label("com.example.waage", "Meine Waage"))
        assertEquals("Waage", HealthValues.label("com.example.waage"))
    }
}
