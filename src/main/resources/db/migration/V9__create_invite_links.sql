-- Rows are cleaned up by a scheduled job in InviteService:
--   used_at IS NOT NULL → deleted after 7-day grace period
--   expires_at < now() - 7d → deleted after 7-day grace period past expiry
CREATE TABLE invite_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(64) NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at TIMESTAMPTZ,
    used_by UUID REFERENCES users(id) ON DELETE SET NULL,
    expires_at TIMESTAMPTZ
);

CREATE INDEX idx_invite_links_token ON invite_links(token);
