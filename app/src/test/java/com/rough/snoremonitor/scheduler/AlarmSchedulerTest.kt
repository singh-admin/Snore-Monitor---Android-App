package com.rough.snoremonitor.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AlarmSchedulerTest {

    @Test
    fun testCalculatesFutureTimeToday() {
        val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(istTimeZone).apply {
            set(Calendar.HOUR_OF_DAY, 20) // 8:00 PM IST
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        val triggerMillis = AlarmScheduler.calculateNextTriggerMillis(23, 0, now)

        val triggerCal = Calendar.getInstance(istTimeZone).apply {
            timeInMillis = triggerMillis
        }

        assertEquals(23, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
        assertTrue("Trigger should be in the future", triggerMillis > now)
    }

    @Test
    fun testCalculatesNextDayIfTimeHasPassed() {
        val istTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
        val now = Calendar.getInstance(istTimeZone).apply {
            set(Calendar.HOUR_OF_DAY, 23) // 11:30 PM IST (past 11:00 PM)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        val triggerMillis = AlarmScheduler.calculateNextTriggerMillis(23, 0, now)

        val triggerCal = Calendar.getInstance(istTimeZone).apply {
            timeInMillis = triggerMillis
        }

        assertEquals(23, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
        assertTrue("Trigger should be scheduled for the next day", triggerMillis > now)
    }
}
