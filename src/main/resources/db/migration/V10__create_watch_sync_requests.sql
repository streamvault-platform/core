CREATE TYPE watch_sync_status AS ENUM ('PENDING', 'SYNCING', 'READY', 'FAILED');

CREATE TABLE watch_sync_requests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id   VARCHAR(255) NOT NULL,
    status      watch_sync_status NOT NULL DEFAULT 'PENDING',
    track_ids   TEXT NOT NULL,          -- JSON array of UUID strings
    manifest    TEXT,                   -- JSON from watch.sync-ready; null until READY
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_watch_sync_requests_user ON watch_sync_requests(user_id);
CREATE INDEX idx_watch_sync_requests_user_device ON watch_sync_requests(user_id, device_id);
