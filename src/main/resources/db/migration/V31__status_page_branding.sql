-- Status page branding: an optional logo image and an optional access password.
ALTER TABLE status_page ADD COLUMN logo_url VARCHAR(2048);
ALTER TABLE status_page ADD COLUMN password_hash VARCHAR(255);
