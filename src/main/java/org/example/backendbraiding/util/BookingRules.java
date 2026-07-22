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

        LocalTime blockStart = block.getStartDateTime().toLocalTime();
        LocalTime blockEnd = block.getEndDateTime().toLocalTime();
        LocalTime slotStart = start.toLocalTime();
        LocalTime slotEnd = end.toLocalTime();
        return slotStart.isBefore(blockEnd) && slotEnd.isAfter(blockStart);
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
}
