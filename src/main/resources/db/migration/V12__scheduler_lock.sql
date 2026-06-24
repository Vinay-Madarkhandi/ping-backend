-- Lightweight leader lock so cluster-singleton jobs (e.g. retention) run on one instance only.
-- Acquisition is a conditional UPDATE guarded by the database clock; the lock auto-expires
-- (lock_until) so a crashed holder never blocks the job permanently.
CREATE TABLE scheduler_lock
(
    name         VARCHAR(64)                 NOT NULL,
    locked_until TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    locked_by    VARCHAR(255)                NOT NULL,
    CONSTRAINT pk_scheduler_lock PRIMARY KEY (name)
);

INSERT INTO scheduler_lock (name, locked_until, locked_by)
VALUES ('retention', TIMESTAMP '1970-01-01 00:00:00', 'none');
