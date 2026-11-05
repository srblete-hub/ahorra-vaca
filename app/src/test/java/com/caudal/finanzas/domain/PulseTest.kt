package com.caudal.finanzas.domain

import com.caudal.finanzas.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PulseTest {
    private val fmt: (Double) -> String = { "%.2f €".format(java.util.Locale.ROOT, it) }

    @Test fun zonesFollowTheWhoopRanges() {
        assertEquals(Zone.GREEN, zoneOf(67.0))
        assertEquals(Zone.YELLOW, zoneOf(66.9))
        assertEquals(Zone.YELLOW, zoneOf(34.0))
        assertEquals(Zone.RED, zoneOf(33.9))
    }

    @Test fun marginIsFullWhenSpendingLittleAndZeroWhenFarAbove() {
        // Mes de 30 días, 600 € para gastar: el día 10 tocaban 240 € (con 2 días de cortesía).
        assertEquals(100.0, PulseCalculator.marginFor(80.0, 600.0, 10, 30), 0.01)
        assertEquals(50.0, PulseCalculator.marginFor(240.0, 600.0, 10, 30), 0.01)
        assertEquals(0.0, PulseCalculator.marginFor(384.0, 600.0, 10, 30), 0.01)
    }

    @Test fun spendScoreNeverPasses100() {
        assertEquals(60.2, PulseCalculator.spendScore(100.0, 100.0), 0.1)
        assertEquals(84.5, PulseCalculator.spendScore(200.0, 100.0), 0.1)
        assertEquals(100.0, PulseCalculator.spendScore(300.0, 100.0), 0.01)
        assertEquals(100.0, PulseCalculator.spendScore(2400.0, 100.0), 0.01)
        assertEquals(0.0, PulseCalculator.spendScore(0.0, 100.0), 0.01)
    }

    @Test fun limitAlertsAt80And100() {
        val today = LocalDate.of(2026, 11, 20)
        val snap = LimitSnapshot(perDay = 30.0, weekOptimal = 150.0, tagMonthly = mapOf(SpendTag.TRANSPORT to 130.0))
        assertEquals(snap, LimitSnapshot.decode(snap.encode()))
        val e = listOf(
            Expense(1, 1, 26.0, "Mercadona", today.toEpochDay()),
            Expense(2, 1, 110.0, "Repsol", today.minusDays(3).toEpochDay()),
        )
        val alerts = LimitAlerts.check(today, e, emptyList(), emptySet(), snap, fmt)
        assertNotNull(alerts.firstOrNull { it.key == "day80" })
        assertTrue(alerts.any { it.key == "tag80-TRANSPORT" })
        val over = LimitAlerts.check(today, e + Expense(3, 1, 30.0, "Repsol", today.toEpochDay()), emptyList(), emptySet(), snap, fmt)
        assertTrue(over.any { it.key == "day100" && it.severe })
        assertTrue(over.any { it.key == "tag100-TRANSPORT" && it.severe })
        assertTrue(over.any { it.key == "week100" })
    }

    @Test fun pressureIsAboutToday() {
        val p = Pulse(100.0, 50.0, 0.0, 100.0, 50.0, 30.0, 5, 5, 45.0 / 30.0, emptyList(), "", "", spentToday = 45.0)
        assertEquals(Zone.RED, p.pressureZone)
        val calm = p.copy(pressure = 20.0 / 30.0, spentToday = 20.0)
        assertEquals(Zone.GREEN, calm.pressureZone)
    }
}
