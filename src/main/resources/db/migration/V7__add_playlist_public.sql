ALTER TABLE playlists ADD COLUMN is_public   BOOLEAN      NOT NULL DEFAULT false;
ALTER TABLE playlists ADD COLUMN owner_name  VARCHAR(50)  NOT NULL DEFAULT '';

UPDATE playlists p SET owner_name = u.username FROM users u WHERE u.id = p.user_id;

CREATE INDEX idx_playlists_public ON playlists(is_public) WHERE is_public = true;
