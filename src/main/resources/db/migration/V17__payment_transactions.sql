-- Durable audit trail of every payment attempt; one row per order.
CREATE TABLE payment_transactions
(
    id                  UUID                        NOT NULL,
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    user_id             UUID                        NOT NULL,
    plan_id             UUID                        NOT NULL,
    amount              BIGINT                      NOT NULL,
    currency            VARCHAR(3)                  NOT NULL,
    razorpay_order_id   VARCHAR(255)                NOT NULL,
    razorpay_payment_id VARCHAR(255),
    payment_status      VARCHAR(16)                 NOT NULL,
    signature           VARCHAR(512),
    CONSTRAINT pk_payment_transactions PRIMARY KEY (id),
    CONSTRAINT uk_payment_txn_order UNIQUE (razorpay_order_id)
);

ALTER TABLE payment_transactions
    ADD CONSTRAINT fk_payment_txn_user FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE payment_transactions
    ADD CONSTRAINT fk_payment_txn_plan FOREIGN KEY (plan_id) REFERENCES plan (id);

CREATE INDEX idx_payment_txn_user ON payment_transactions (user_id);

-- Leader-lock row for the cluster-singleton subscription expiry job (mirrors 'retention'/'quota').
INSERT INTO scheduler_lock (name, locked_until, locked_by)
VALUES ('subscription', TIMESTAMP '1970-01-01 00:00:00', 'none');
