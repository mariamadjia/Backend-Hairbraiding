package org.example.backendbraiding.dto;

public class TimeSlotDTO {
    private Long id;
    private String dayOfWeek;
    private String startTime; // HH:mm format
    private String endTime;   // HH:mm format
    private Integer capacity;
    private Integer slotOrder;

    // Constructors
    public TimeSlotDTO() {}

    public TimeSlotDTO(String dayOfWeek, String startTime, String endTime, Integer capacity, Integer slotOrder) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.capacity = capacity;
        this.slotOrder = slotOrder;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getSlotOrder() {
        return slotOrder;
    }

    public void setSlotOrder(Integer slotOrder) {
        this.slotOrder = slotOrder;
    }
}
