package mg.flybox.usage.data

import java.time.ZoneId
import java.time.ZonedDateTime
import mg.flybox.usage.data.work.WorkScheduler
import org.junit.Assert.assertEquals
import org.junit.Test

class SchedulerTest {
    private val zone = ZoneId.of("Indian/Antananarivo")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 1, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun delayToLaterToday() = assertEquals(2L * 3_600_000 + 30 * 60_000, WorkScheduler.delayUntilNext(20, at(17, 30), zone))

    @Test fun delayRollsToTomorrowWhenHourPassed() = assertEquals(23L * 3_600_000, WorkScheduler.delayUntilNext(20, at(21, 0), zone))

    @Test fun exactHourSchedulesNextDay() = assertEquals(24L * 3_600_000, WorkScheduler.delayUntilNext(20, at(20, 0), zone))
}
