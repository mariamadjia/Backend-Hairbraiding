-- V12 briefly introduced this status. Normalize any row written while that
-- release was active; the application now uses approved_at as its
-- capture-in-progress marker and only existing appointment status values.
UPDATE appointments
SET status = 'PENDING'
WHERE status = 'APPROVAL_PENDING_CAPTURE';
