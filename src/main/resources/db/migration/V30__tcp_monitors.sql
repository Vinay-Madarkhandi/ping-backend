-- TCP port monitors: a raw TCP connect check instead of an HTTP request. Reuses the "url" column
-- for the bare hostname (no scheme) and adds the port to connect to.
ALTER TABLE monitor ADD COLUMN port INTEGER;
