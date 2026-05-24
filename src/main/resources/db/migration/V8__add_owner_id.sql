ALTER TABLE tracks ADD COLUMN owner_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE albums ADD COLUMN owner_id UUID REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX idx_tracks_owner_id ON tracks(owner_id);
CREATE INDEX idx_albums_owner_id ON albums(owner_id);
