package org.example.backendbraiding.util;

import org.example.backendbraiding.model.BlockedTimeSlot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BookingRulesTests {
    @Test
    void recurringBlocksUseHalfOpenBoundariesForAppointmentStarts() {
        BlockedTimeSlot daily = block("2026-07-20T12:00:00", "2026-07-20T13:00:00", "DAILY");
        assertTrue(BookingRules.recurringBlockContains(daily, LocalDateTime.parse("2026-07-21T12:00:00")));
        assertTrue(BookingRules.recurringBlockContains(daily, LocalDateTime.parse("2026-07-21T12:59:00")));
        assertFalse(BookingRules.recurringBlockContains(daily, LocalDateTime.parse("2026-07-21T13:00:00")));
    }

    @Test
    void appliesWeeklyAndMonthlyRecurrenceAfterStartDate() {
        BlockedTimeSlot weekly = block("2026-07-20T12:00:00", "2026-07-20T13:00:00", "WEEKLY");
        assertTrue(BookingRules.recurringBlockOverlaps(weekly,
                LocalDateTime.parse("2026-07-27T12:30:00"), LocalDateTime.parse("2026-07-27T13:30:00")));
        assertFalse(BookingRules.recurringBlockOverlaps(weekly,
                LocalDateTime.parse("2026-07-28T12:30:00"), LocalDateTime.parse("2026-07-28T13:30:00")));

        BlockedTimeSlot monthly = block("2026-07-20T12:00:00", "2026-07-20T13:00:00", "MONTHLY");
        assertTrue(BookingRules.recurringBlockOverlaps(monthly,
                LocalDateTime.parse("2026-08-20T12:30:00"), LocalDateTime.parse("2026-08-20T12:45:00")));
    }

    private BlockedTimeSlot block(String start, String end, String pattern) {
        BlockedTimeSlot block = new BlockedTimeSlot();
        block.setStartDateTime(LocalDateTime.parse(start));
        block.setEndDateTime(LocalDateTime.parse(end));
        block.setIsRecurring(true);
        block.setRecurrencePattern(pattern);
        return block;
    }
}
