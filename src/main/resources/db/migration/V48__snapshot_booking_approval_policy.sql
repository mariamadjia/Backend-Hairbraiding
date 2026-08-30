-- Historical booking-time settings are unknown: retain manual review for legacy
-- requests. Existing approved_at capture requests remain recoverable.
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS require_approval boolean NOT NULL DEFAULT true;
