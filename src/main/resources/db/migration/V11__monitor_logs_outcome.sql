-- Probe outcome (UP / DOWN / INCONCLUSIVE) so infrastructure failures are recorded distinctly
-- from real target-down events. Nullable for any pre-existing rows.
ALTER TABLE monitor_logs ADD COLUMN outcome VARCHAR(16);
