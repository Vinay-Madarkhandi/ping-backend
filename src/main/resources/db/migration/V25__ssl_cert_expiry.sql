-- Captured opportunistically from the TLS session during a normal HTTPS check (no extra probe).
ALTER TABLE monitor_status ADD COLUMN ssl_cert_expires_at TIMESTAMP;
