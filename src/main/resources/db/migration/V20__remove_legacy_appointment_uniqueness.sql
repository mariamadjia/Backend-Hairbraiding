-- Older production schemas enforced one lifetime appointment per customer and
-- start time. That prevents a customer from retrying after a cancelled, denied,
-- failed, or expired request. Remove every legacy unique constraint/index whose
-- key is exactly (customer_id, appointment_date_time), regardless of its name.

DO $$
DECLARE
    record_to_drop RECORD;
BEGIN
    FOR record_to_drop IN
        SELECT constraint_record.conname AS constraint_name
        FROM pg_constraint constraint_record
        JOIN pg_class table_record ON table_record.oid = constraint_record.conrelid
        JOIN pg_namespace schema_record ON schema_record.oid = table_record.relnamespace
        WHERE schema_record.nspname = current_schema()
          AND table_record.relname = 'appointments'
          AND constraint_record.contype = 'u'
          AND (
              SELECT array_agg(attribute_record.attname ORDER BY key_record.ordinality)
              FROM unnest(constraint_record.conkey)
                   WITH ORDINALITY AS key_record(attnum, ordinality)
              JOIN pg_attribute attribute_record
                ON attribute_record.attrelid = table_record.oid
               AND attribute_record.attnum = key_record.attnum
          ) = ARRAY['customer_id', 'appointment_date_time']::name[]
    LOOP
        EXECUTE format(
            'ALTER TABLE appointments DROP CONSTRAINT %I',
            record_to_drop.constraint_name
        );
    END LOOP;
END
$$;

DO $$
DECLARE
    record_to_drop RECORD;
BEGIN
    FOR record_to_drop IN
        SELECT index_record.relname AS indexname
        FROM pg_index index_metadata
        JOIN pg_class table_record ON table_record.oid = index_metadata.indrelid
        JOIN pg_namespace schema_record ON schema_record.oid = table_record.relnamespace
        JOIN pg_class index_record ON index_record.oid = index_metadata.indexrelid
        WHERE schema_record.nspname = current_schema()
          AND table_record.relname = 'appointments'
          AND index_metadata.indisunique
          AND (
              SELECT array_agg(attribute_record.attname ORDER BY key_record.ordinality)
              FROM unnest(index_metadata.indkey)
                   WITH ORDINALITY AS key_record(attnum, ordinality)
              JOIN pg_attribute attribute_record
                ON attribute_record.attrelid = table_record.oid
               AND attribute_record.attnum = key_record.attnum
          ) = ARRAY['customer_id', 'appointment_date_time']::name[]
    LOOP
        EXECUTE format('DROP INDEX IF EXISTS %I', record_to_drop.indexname);
    END LOOP;
END
$$;

CREATE INDEX IF NOT EXISTS idx_appointment_customer_datetime
    ON appointments (customer_id, appointment_date_time);
