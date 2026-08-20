-- Leader-lock row for the cluster-singleton token cleanup job (mirrors 'retention'/'quota'/'subscription').
INSERT INTO scheduler_lock (name, locked_until, locked_by)
VALUES ('token-cleanup', TIMESTAMP '1970-01-01 00:00:00', 'none');
