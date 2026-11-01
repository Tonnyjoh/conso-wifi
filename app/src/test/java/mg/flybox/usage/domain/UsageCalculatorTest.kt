package mg.flybox.usage.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageCalculatorTest {
    private val zone = ZoneId.of("Indian/Antananarivo")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    private fun s(ts: Long, down: Long, up: Long = 0) = Sample(ts = ts, down = down, up = up)

    @Test fun deltaIsPlainDifference() = assertEquals(50, UsageCalculator.delta(100, 150))

    @Test fun deltaTreatsDecreaseAsResetAndCountsNewValue() = assertEquals(30, UsageCalculator.delta(1000, 30))

    @Test fun usageBetweenSumsDeltasAndHandlesReset() {
        val rows = listOf(s(1, 100), s(2, 150), s(3, 10), s(4, 40))
        assertEquals(50L + 10L + 30L, UsageCalculator.usageBetween(rows))
    }

    @Test fun usageSumsDownAndUp() {
        val rows = listOf(s(1, 100, 100), s(2, 150, 120))
        assertEquals(70L, UsageCalculator.usageBetween(rows))
    }

    @Test fun todayCountsOnlyDeltasAfterLocalMidnight() {
        val rows = listOf(
            s(at(2026, 10, 27, 22, 0), 1000),
            s(at(2026, 10, 27, 23, 50), 1500), // 500 hier
            s(at(2026, 10, 28, 0, 10), 1800), // 300 aujourd'hui
            s(at(2026, 10, 28, 9, 0), 2000), // 200 aujourd'hui
        )
        val now = at(2026, 10, 28, 10, 0)
        assertEquals(500L, UsageCalculator.todayUsage(rows, now, zone))
        val daily = UsageCalculator.dailyUsage(rows, zone)
        assertEquals(500L, daily[LocalDate.of(2026, 10, 27)])
    }

    @Test fun counterResetAfterMidnightCountsNewValue() {
        val rows = listOf(
            s(at(2026, 10, 27, 23, 50), 9_000),
            s(at(2026, 10, 28, 0, 20), 9_500), // 500
            s(at(2026, 10, 28, 6, 0), 120), // remise à zéro : 120
            s(at(2026, 10, 28, 7, 0), 220), // 100
        )
        assertEquals(720L, UsageCalculator.todayUsage(rows, at(2026, 10, 28, 8, 0), zone))
    }

    @Test fun noDeltaWithSingleSample() {
        assertEquals(0L, UsageCalculator.todayUsage(listOf(s(at(2026, 10, 28, 8, 0), 5)), at(2026, 10, 28, 9, 0), zone))
    }

    @Test fun dayBoundariesFollowTheGivenZone() {
        val utc = ZoneId.of("UTC")
        // 21:30 UTC le 27 est 00:30 le 28 à Madagascar (UTC+3) mais encore le 27 en UTC.
        val ts1 = ZonedDateTime.of(2026, 10, 27, 20, 0, 0, 0, utc).toInstant().toEpochMilli()
        val ts2 = ZonedDateTime.of(2026, 10, 27, 21, 30, 0, 0, utc).toInstant().toEpochMilli()
        val rows = listOf(s(ts1, 0), s(ts2, 400))
        assertEquals(LocalDate.of(2026, 10, 28), UsageCalculator.dailyUsage(rows, zone).keys.single())
        assertEquals(LocalDate.of(2026, 10, 27), UsageCalculator.dailyUsage(rows, utc).keys.single())
    }

    @Test fun seriesFillsMissingDaysWithZero() {
        val today = LocalDate.of(2026, 10, 28)
        val out = UsageCalculator.series(mapOf(today to 5L, today.minusDays(2) to 7L), today, 3)
        assertEquals(listOf(7L, 0L, 5L), out.map { it.second })
        assertEquals(today.minusDays(2), out.first().first)
    }

    @Test fun startOfDay() {
        assertEquals(at(2026, 10, 28, 0, 0), UsageCalculator.startOfDayMillis(at(2026, 10, 28, 15, 30), zone))
    }
}
