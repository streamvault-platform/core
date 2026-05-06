ALTER TABLE tracks
    ADD COLUMN transcoded_path      TEXT,
    ADD COLUMN transcoded_mime_type VARCHAR(50);
