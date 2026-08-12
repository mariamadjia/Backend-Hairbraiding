CREATE TABLE IF NOT EXISTS booking_add_ons (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    pricing_mode VARCHAR(30) NOT NULL DEFAULT 'FIXED',
    price_cents BIGINT NOT NULL DEFAULT 0,
    deposit_behavior VARCHAR(30) NOT NULL DEFAULT 'NO_CHANGE',
    deposit_adjustment_cents BIGINT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_booking_add_on_price CHECK (price_cents >= 0),
    CONSTRAINT chk_booking_add_on_deposit_adjustment CHECK (deposit_adjustment_cents >= 0)
);

CREATE TABLE IF NOT EXISTS subcategory_add_ons (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    subcategory_id BIGINT NOT NULL REFERENCES subcategories(id) ON DELETE CASCADE,
    add_on_id BIGINT NOT NULL REFERENCES booking_add_ons(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    price_override_cents BIGINT,
    all_sizes BOOLEAN NOT NULL DEFAULT TRUE,
    all_lengths BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_subcategory_add_on UNIQUE (subcategory_id, add_on_id),
    CONSTRAINT chk_subcategory_add_on_price_override CHECK (price_override_cents IS NULL OR price_override_cents >= 0)
);

CREATE TABLE IF NOT EXISTS subcategory_add_on_service_items (
    assignment_id BIGINT NOT NULL REFERENCES subcategory_add_ons(id) ON DELETE CASCADE,
    service_item_id BIGINT NOT NULL REFERENCES service_items(id) ON DELETE CASCADE,
    PRIMARY KEY (assignment_id, service_item_id)
);

CREATE TABLE IF NOT EXISTS subcategory_add_on_length_options (
    assignment_id BIGINT NOT NULL REFERENCES subcategory_add_ons(id) ON DELETE CASCADE,
    length_option_id BIGINT NOT NULL REFERENCES length_options(id) ON DELETE CASCADE,
    PRIMARY KEY (assignment_id, length_option_id)
);

CREATE TABLE IF NOT EXISTS appointment_add_ons (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    add_on_id BIGINT REFERENCES booking_add_ons(id) ON DELETE SET NULL,
    add_on_name VARCHAR(120) NOT NULL,
    pricing_mode VARCHAR(30) NOT NULL,
    advertised_price_cents BIGINT NOT NULL DEFAULT 0,
    charged_price_cents BIGINT NOT NULL DEFAULT 0,
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_subcategory_add_ons_subcategory
    ON subcategory_add_ons(subcategory_id, display_order, id);
CREATE INDEX IF NOT EXISTS idx_appointment_add_ons_appointment
    ON appointment_add_ons(appointment_id, display_order, id);
