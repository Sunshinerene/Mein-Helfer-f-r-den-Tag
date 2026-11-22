package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Einheiten, Mengen je Einnahme und Packungsinhalt bei Medikamenten (Tabletten, Saft, Insulin …). */
class MedUnitsTest {
    @Test
    fun amountsReadAndWrite() {
        assertEquals(1.5, Amounts.parse("1,5")!!, 1e-9)
        assertEquals(0.5, Amounts.parse("½")!!, 1e-9)
        assertEquals(1.5, Amounts.parse("1½")!!, 1e-9)
        assertEquals(7.5, Amounts.parse(" 7.5 ")!!, 1e-9)
        assertNull(Amounts.parse("abc"))
        assertNull(Amounts.parse(""))
        assertEquals("14", Amounts.fmt(14.0))
        assertEquals("2,5", Amounts.fmt(2.5))
        assertEquals("0,25", Amounts.fmt(0.25))
        assertEquals("77,5 ml", Amounts.withUnit(100.0 - 3 * 7.5, "ml"))
    }

    @Test
    fun dosesPerTime() {
        val map = Doses.parse("08:00=8,18:00=6")
        assertEquals(8.0, Doses.at(map, "8:00")!!, 1e-9)
        assertEquals(14.0, Doses.daily(listOf("08:00", "18:00"), map)!!, 1e-9)
        assertNull(Doses.daily(listOf("08:00", "12:00", "18:00"), map))
        assertEquals("08:00=8,18:00=6", Doses.format(mapOf("18:00" to 6.0, "8:00" to 8.0)))
        assertEquals("08:00=0.5", Doses.format(mapOf("08:00" to 0.5)))
        assertEquals("08:00 8 IE, 18:00 6 IE", Doses.summary(listOf("08:00", "18:00"), map, "IE"))
        assertEquals("je 5 ml", Doses.summary(listOf("08:00", "20:00"), mapOf("08:00" to 5.0, "20:00" to 5.0), "ml"))
        assertEquals(emptyMap<String, Double>(), Doses.parse("Unsinn"))
    }

    @Test
    fun unitsFromPlanText() {
        assertEquals(MedUnit.IE, MedUnit.fromText("I.E."))
        assertEquals(MedUnit.Tropfen, MedUnit.fromText("Trpf."))
        assertEquals(MedUnit.Hub, MedUnit.fromText("Hub"))
        assertEquals(MedUnit.Stueck, MedUnit.fromText("Stück"))
        assertEquals(MedUnit.Ml, MedUnit.of("ml"))
        assertEquals(MedUnit.Stueck, MedUnit.of(""))
    }

    @Test
    fun amountFromDosage() {
        assertEquals(5.0 to MedUnit.Ml, DosageText.parse("5 ml"))
        assertEquals(0.5 to MedUnit.Stueck, DosageText.parse("½ Tablette"))
        assertEquals(10.0 to MedUnit.Tropfen, DosageText.parse("10 Tropfen"))
        assertEquals(8.0 to MedUnit.IE, DosageText.parse("8 IE abends"))
        assertEquals(1.0 to MedUnit.Hub, DosageText.parse("1 Hub"))
        assertNull(DosageText.parse("1 Spritze"))
        assertNull(DosageText.parse("5 mg · 1-0-1-0"))
    }

    @Test
    fun packContentFromLabel() {
        val insulin = PackText.parse("Insulin glargin 100 Einheiten/ml Injektionslösung in einem Fertigpen 5 x 3 ml")!!
        assertEquals(MedUnit.IE, insulin.unit)
        assertEquals(1500.0, insulin.amount, 1e-9)
        val juice = PackText.parse("Ibuprofen Saft 20 mg/ml Suspension 100 ml")!!
        assertEquals(MedUnit.Ml, juice.unit)
        assertEquals(100.0, juice.amount, 1e-9)
        assertEquals(PackInfo(20.0, MedUnit.Stueck), PackText.parse("Ramipril 5 mg 20 Tabletten"))
        assertEquals(PackInfo(200.0, MedUnit.Hub), PackText.parse("Dosieraerosol 200 Sprühstöße"))
        val syrup = PackText.parse("Paracetamol Saft 200 mg/5 ml 100 ml")!!
        assertEquals(100.0, syrup.amount, 1e-9)
        assertNull(PackText.parse("Ramipril 5 mg"))
    }
}
