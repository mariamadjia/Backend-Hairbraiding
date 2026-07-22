DO $$
BEGIN
    IF to_regclass('public.blocked_time_slots') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_blocked_time_range
            ON blocked_time_slots (start_date_time, end_date_time);
        CREATE INDEX IF NOT EXISTS idx_blocked_time_recurring
            ON blocked_time_slots (is_recurring)
            WHERE is_recurring = true;
    END IF;

    IF to_regclass('public.time_slots') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_time_slots_day_order
            ON time_slots (day_of_week, slot_order);
    END IF;
END $$;
