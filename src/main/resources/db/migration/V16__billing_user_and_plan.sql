-- Plan pricing metadata so order amounts are resolved server-side from the plan, never the client.
ALTER TABLE plan
    ADD COLUMN price_amount BIGINT NOT NULL DEFAULT 0;
ALTER TABLE plan
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'INR';
ALTER TABLE plan
    ADD COLUMN duration_days INTEGER NOT NULL DEFAULT 0;

-- PRO pricing (editable via SQL). FREE stays price 0 / duration 0.
UPDATE plan
SET price_amount = 49900, currency = 'INR', duration_days = 30
WHERE name = 'PRO';

-- Subscription lifecycle on users. Existing users default to FREE.
ALTER TABLE users
    ADD COLUMN subscription_status VARCHAR(16) NOT NULL DEFAULT 'FREE';
ALTER TABLE users
    ADD COLUMN subscription_start_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE users
    ADD COLUMN subscription_end_at TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE users
    ADD COLUMN billing_customer_id VARCHAR(255);
