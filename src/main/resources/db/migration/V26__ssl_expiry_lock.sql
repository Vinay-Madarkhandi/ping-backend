-- Leader-lock row for the cluster-singleton SSL expiry warning job (mirrors 'retention'/'token-cleanup').
INSERT INTO scheduler_lock (name, locked_until, locked_by)
VALUES ('ssl-expiry', TIMESTAMP '1970-01-01 00:00:00', 'none');
