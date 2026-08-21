ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS phone_normalized VARCHAR(20);

UPDATE customers
SET email = lower(trim(email)),
    phone_number = trim(phone_number),
    phone_normalized = CASE
        WHEN length(regexp_replace(phone_number, '[^0-9]', '', 'g')) = 10
            THEN '+1' || regexp_replace(phone_number, '[^0-9]', '', 'g')
        WHEN length(regexp_replace(phone_number, '[^0-9]', '', 'g')) = 11
             AND regexp_replace(phone_number, '[^0-9]', '', 'g') LIKE '1%'
            THEN '+' || regexp_replace(phone_number, '[^0-9]', '', 'g')
        ELSE NULL
    END;

-- Do not automatically merge records carrying Stripe identities. Add the
-- database guarantee when existing data is already conflict-free; otherwise
-- retain every record for a reviewed merge.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM customers
        GROUP BY lower(trim(email)) HAVING count(*) > 1
    ) THEN
        CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_email_normalized
            ON customers (lower(trim(email)));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_customers_phone_normalized
    ON customers(phone_normalized);
