ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS customer_cancellation_reason VARCHAR(500);

-- Preserve customer reasons previously stored in the shared admin-notes field.
UPDATE appointments
SET customer_cancellation_reason = NULLIF(
        regexp_replace(admin_notes, '^Cancelled by customer:\s*', '', 'i'),
        ''
    )
WHERE cancelled_by_customer = TRUE
  AND customer_cancellation_reason IS NULL
  AND admin_notes ~* '^Cancelled by customer:';
