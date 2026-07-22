package org.example.backendbraiding.util;

import org.example.backendbraiding.model.BlockedTimeSlot;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BookingRules {
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private BookingRules() {}

    public static int durationMinutes(String duration, int fallbackMinutes) {
        if (duration == null || duration.isBlank()) return fallbackMinutes;
        Matcher matcher = NUMBER.matcher(duration.toLowerCase());
        double largest = 0;
        while (matcher.find()) largest = Math.max(largest, Double.parseDouble(matcher.group(1)));
        if (largest <= 0) return fallbackMinutes;
        return (int) Math.ceil(duration.toLowerCase().contains("hour") ? largest * 60 : largest);
    }

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
}
