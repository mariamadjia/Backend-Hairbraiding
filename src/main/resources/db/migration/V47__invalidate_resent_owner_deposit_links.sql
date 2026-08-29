ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS owner_deposit_token_hash VARCHAR(64);
