CREATE TABLE appointment_checkouts (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    appointment_id BIGINT NOT NULL UNIQUE REFERENCES appointments(id) ON DELETE CASCADE,
    service_total_cents BIGINT NOT NULL,
    deposit_credit_cents BIGINT NOT NULL DEFAULT 0,
    tip_cents BIGINT NOT NULL DEFAULT 0,
    amount_paid_cents BIGINT NOT NULL DEFAULT 0,
    amount_refunded_cents BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    completed_at TIMESTAMP,
    completed_by_admin_id BIGINT REFERENCES admin(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_appointment_checkout_amounts CHECK (
        service_total_cents > 0 AND deposit_credit_cents >= 0 AND tip_cents >= 0
        AND amount_paid_cents >= 0 AND amount_refunded_cents >= 0
        AND amount_refunded_cents <= amount_paid_cents
    ),
    CONSTRAINT ck_appointment_checkout_status CHECK (
        status IN ('OPEN', 'PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')
    )
);

CREATE INDEX idx_appointment_checkouts_status
    ON appointment_checkouts(status, updated_at DESC);

CREATE TABLE checkout_transactions (
    id BIGSERIAL PRIMARY KEY,
    checkout_id BIGINT NOT NULL REFERENCES appointment_checkouts(id) ON DELETE CASCADE,
    transaction_type VARCHAR(20) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount_cents BIGINT NOT NULL,
    external_reference VARCHAR(255),
    reason VARCHAR(500),
    stripe_payment_intent_id VARCHAR(255),
    stripe_refund_id VARCHAR(255),
    recorded_by_admin_id BIGINT REFERENCES admin(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_checkout_transaction_type CHECK (transaction_type IN ('PAYMENT', 'REFUND')),
    CONSTRAINT ck_checkout_transaction_method CHECK (
        payment_method IN ('CASH', 'ZELLE', 'CASH_APP', 'OTHER', 'STRIPE_TERMINAL')
    ),
    CONSTRAINT ck_checkout_transaction_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_checkout_transaction_amount CHECK (amount_cents > 0)
);

CREATE INDEX idx_checkout_transactions_checkout
    ON checkout_transactions(checkout_id, created_at DESC);

CREATE UNIQUE INDEX uq_checkout_transactions_stripe_payment
    ON checkout_transactions(stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;

CREATE UNIQUE INDEX uq_checkout_transactions_stripe_refund
    ON checkout_transactions(stripe_refund_id)
    WHERE stripe_refund_id IS NOT NULL;
