-- Full-text search: precomputed tsvector columns (auto-maintained) + GIN indexes
-- Trigram indexes for fuzzy / partial-word matching (pg_trgm)

CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE artists
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(name, ''))) STORED;

ALTER TABLE albums
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(title, ''))) STORED;

ALTER TABLE tracks
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (to_tsvector('simple', coalesce(title, ''))) STORED;

-- GIN indexes for @@ full-text queries: O(log n) regardless of table size
CREATE INDEX idx_artists_search_vector ON artists USING GIN (search_vector);
CREATE INDEX idx_albums_search_vector  ON albums  USING GIN (search_vector);
CREATE INDEX idx_tracks_search_vector  ON tracks  USING GIN (search_vector);

-- Trigram indexes: enable fuzzy matching and indexed ILIKE
CREATE INDEX idx_artists_name_trgm  ON artists USING GIN (name  gin_trgm_ops);
CREATE INDEX idx_albums_title_trgm  ON albums  USING GIN (title gin_trgm_ops);
CREATE INDEX idx_tracks_title_trgm  ON tracks  USING GIN (title gin_trgm_ops);
