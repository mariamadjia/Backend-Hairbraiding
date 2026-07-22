package org.example.backendbraiding.util;

import org.example.backendbraiding.model.BlockedTimeSlot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BookingRulesTests {
    @Test
    void parsesConservativeEndOfDurationRange() {
        assertEquals(240, BookingRules.durationMinutes("2-4 hours", 60));
        assertEquals(90, BookingRules.durationMinutes("90 minutes", 60));
        assertEquals(60, BookingRules.durationMinutes(null, 60));
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
