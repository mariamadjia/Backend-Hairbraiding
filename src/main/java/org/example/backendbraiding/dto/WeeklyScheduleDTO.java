package org.example.backendbraiding.dto;

import java.util.List;

public class WeeklyScheduleDTO {
    private List<DayScheduleDTO> days;

    public List<DayScheduleDTO> getDays() {
        return days;
    }

    public void setDays(List<DayScheduleDTO> days) {
        this.days = days;
    }
}
