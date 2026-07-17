# LocalDateTime to ZonedDateTime Migration Guide

## Overview
The current system uses `LocalDateTime` for appointment storage, which lacks timezone information. This causes potential issues with:
- Timezone mismatches between customer and server
- Daylight saving time shifts
- Incorrect time display in different timezones

## Migration Steps

### 1. Database Migration

**File**: `src/main/resources/db/migration/V__add_timezone_to_appointments.sql`

```sql
-- Add new column with timezone support
ALTER TABLE appointments ADD COLUMN appointment_date_time_zoned TIMESTAMP WITH TIME ZONE;

-- Migrate existing data (assuming server timezone is America/Los_Angeles)
UPDATE appointments 
SET appointment_date_time_zoned = appointment_date_time AT TIME ZONE 'America/Los_Angeles'
WHERE appointment_date_time_zoned IS NULL;

-- Drop old column after verification
-- ALTER TABLE appointments DROP COLUMN appointment_date_time;

-- Rename new column
-- ALTER TABLE appointments RENAME COLUMN appointment_date_time_zoned TO appointment_date_time;
```

### 2. Entity Update

**File**: `src/main/java/org/example/backendbraiding/model/Appointment.java`

```java
// Change from:
@Column(nullable = false)
private LocalDateTime appointmentDateTime;

// To:
@Column(nullable = false)
private ZonedDateTime appointmentDateTime;
```

### 3. DTO Update

**File**: `src/main/java/org/example/backendbraiding/dto/AppointmentRequestDTO.java`

```java
// Change from:
@NotNull(message = "Appointment date and time is required")
@Future(message = "Appointment must be in the future")
private LocalDateTime appointmentDateTime;

// To:
@NotNull(message = "Appointment date and time is required")
@Future(message = "Appointment must be in the future")
private ZonedDateTime appointmentDateTime;
```

### 4. Service Layer Updates

**File**: `src/main/java/org/example/backendbraiding/service/AppointmentService.java`

Update all methods that handle `appointmentDateTime` to use `ZonedDateTime`:
- `createAppointment()`
- `validateAppointmentDateTime()`
- `validateAppointmentAvailability()`

### 5. Frontend Update

**File**: `components/BookingCalendar.tsx`

```typescript
// Change convertTimeToDateTime to return ISO-8601 with timezone
const convertTimeToDateTime = (date: Date, timeStr: string): string => {
    const [time, period] = timeStr.split(' ');
    const [hourStr, minuteStr] = time.split(':');
    let hour = parseInt(hourStr);
    const minute = parseInt(minuteStr);
    
    if (period === 'PM' && hour !== 12) {
        hour += 12;
    } else if (period === 'AM' && hour === 12) {
        hour = 0;
    }
    
    const dateTime = new Date(date);
    dateTime.setHours(hour, minute, 0, 0);
    
    // Return ISO-8601 with timezone offset
    return dateTime.toISOString();
};
```

### 6. Availability Service Update

**File**: `src/main/java/org/example/backendbraiding/service/AvailabilityService.java`

Update `getAvailableSlots()` to work with `ZonedDateTime` for consistent timezone handling.

## Testing Checklist

- [ ] Test booking from different timezones (EST, PST, UTC)
- [ ] Test across DST boundary (March and November)
- [ ] Verify database stores correct timezone
- [ ] Verify admin panel displays correct time
- [ ] Test availability calculation with timezone
- [ ] Test blocked time with timezone
- [ ] Verify existing appointments display correctly after migration

## Rollback Plan

If issues occur:
1. Keep old `appointment_date_time` column as backup
2. Can revert entity and DTO changes
3. Frontend can fall back to old format
4. Database can restore from backup

## Estimated Time

- Database migration: 1 hour
- Entity/DTO updates: 2 hours
- Service layer updates: 3 hours
- Frontend updates: 2 hours
- Testing: 4 hours
- **Total: ~12 hours**

## Notes

- This is a breaking change for the API
- Requires coordination with frontend deployment
- Should be done during low-traffic period
- Consider feature flag to enable/disable new timezone handling
