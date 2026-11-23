package de.meinhelfer.tag.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfCheckTest {
    @Test
    fun bandsFollowAnlage2() {
        assertEquals(0.0, SelfCheck.weighted(1, 1), 0.0)
        assertEquals(2.5, SelfCheck.weighted(1, 2), 0.0)
        assertEquals(10.0, SelfCheck.weighted(1, 15), 0.0)
        assertEquals(3.75, SelfCheck.weighted(3, 1), 0.0)
        assertEquals(15.0, SelfCheck.weighted(3, 7), 0.0)
        assertEquals(20.0, SelfCheck.weighted(4, 18), 0.0)
        assertEquals(30.0, SelfCheck.weighted(4, 19), 0.0)
        assertEquals(5.0, SelfCheck.weighted(5, 1), 0.0)
        assertEquals(10.0, SelfCheck.weighted(5, 3), 0.0)
        assertEquals(11.25, SelfCheck.weighted(6, 11), 0.0)
    }

    @Test
    fun evaluateTakesHigherOfTwoAndThree() {
        // Bereich 1: Treppe unselbständig (3), Umsetzen überwiegend selbständig (1) → 4 → 5 gewichtet
        // Bereich 3: Ängste täglich (5) → 11,25; Bereich 2: Erinnern in geringem Maße (2) → 3,75
        // Bereich 4: Duschen unselbständig (3), Essen überwiegend selbständig (3) → 6 → 10
        val a = mapOf("1.5" to 3, "1.3" to 1, "3.10" to 3, "2.4" to 2, "4.4" to 3, "4.8" to 1, "5a" to 1, "6.3" to 1)
        val r = SelfCheck.evaluate(a)
        assertEquals(4, r.sums[1])
        assertEquals(6, r.sums[4])
        // 5 + 11,25 + 10 + 5 + 3,75
        assertEquals(35.0, r.total, 0.001)
        assertEquals(2, SelfCheck.grade(r.total))
        assertEquals(2, SelfCheck.gradePlanned(r.total))
        assertEquals(1, SelfCheck.gradePlanned(28.0))
        assertEquals(2, SelfCheck.grade(28.0))
        assertEquals(a, SelfCheck.decode(SelfCheck.encode(a)))
        assertTrue(SelfCheck.report("Mama", a).contains("etwa Pflegegrad 2"))
    }

    @Test
    fun maximumIsHundred() {
        val all = SelfCheck.AREAS.flatMap { it.questions }.associate { q -> q.key to q.options.indices.maxBy { q.options[it].points } }
        assertEquals(100.0, SelfCheck.evaluate(all).total, 0.001)
        assertEquals(54, SelfCheck.evaluate(all).sums[4])
    }
}
