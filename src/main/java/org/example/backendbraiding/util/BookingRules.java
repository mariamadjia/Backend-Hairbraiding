package org.example.backendbraiding.util;

import org.example.backendbraiding.model.BlockedTimeSlot;

import java.time.LocalDateTime;
import java.time.LocalTime;

public final class BookingRules {
    private BookingRules() {}

    public static boolean recurringBlockOverlaps(BlockedTimeSlot block, LocalDateTime start, LocalDateTime end) {
        if (!Boolean.TRUE.equals(block.getIsRecurring()) || block.getRecurrencePattern() == null
                || start.isBefore(block.getStartDateTime())) return false;

        String pattern = block.getRecurrencePattern().toUpperCase();
        if ("WEEKLY".equals(pattern) && block.getStartDateTime().getDayOfWeek() != start.getDayOfWeek()) return false;
        if ("MONTHLY".equals(pattern) && block.getStartDateTime().getDayOfMonth() != start.getDayOfMonth()) return false;
        if (!"DAILY".equals(pattern) && !"WEEKLY".equals(pattern) && !"MONTHLY".equals(pattern)) return false;

        return recurringBlockContains(block, start)
                || recurringBlockContains(block, end.minusNanos(1))
                || intervalContainsTime(start, end, block.getStartDateTime().toLocalTime());
    }

    public static boolean recurringBlockContains(BlockedTimeSlot block, LocalDateTime start) {
        if (!Boolean.TRUE.equals(block.getIsRecurring()) || block.getRecurrencePattern() == null
                || start.isBefore(block.getStartDateTime())) return false;

        String pattern = block.getRecurrencePattern().toUpperCase();
        if ("WEEKLY".equals(pattern) && block.getStartDateTime().getDayOfWeek() != start.getDayOfWeek()) return false;
        if ("MONTHLY".equals(pattern) && block.getStartDateTime().getDayOfMonth() != start.getDayOfMonth()) return false;
        if (!"DAILY".equals(pattern) && !"WEEKLY".equals(pattern) && !"MONTHLY".equals(pattern)) return false;

        LocalTime blockStart = block.getStartDateTime().toLocalTime();
        LocalTime blockEnd = block.getEndDateTime().toLocalTime();
        LocalTime candidate = start.toLocalTime();
        if (blockEnd.isAfter(blockStart)) {
            return !candidate.isBefore(blockStart) && candidate.isBefore(blockEnd);
        }
        return !candidate.isBefore(blockStart) || candidate.isBefore(blockEnd);
    }

    private static boolean intervalContainsTime(LocalDateTime start, LocalDateTime end, LocalTime candidate) {
        LocalTime intervalStart = start.toLocalTime();
        LocalTime intervalEnd = end.toLocalTime();
        if (end.toLocalDate().isAfter(start.toLocalDate()) || !intervalEnd.isAfter(intervalStart)) {
            return !candidate.isBefore(intervalStart) || candidate.isBefore(intervalEnd);
        }
        return !candidate.isBefore(intervalStart) && candidate.isBefore(intervalEnd);
    }
}
