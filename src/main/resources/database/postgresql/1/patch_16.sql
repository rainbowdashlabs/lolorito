CREATE INDEX IF NOT EXISTS listing_episode_reason_ended_idx
    ON lolorito.listing_episode (end_reason, ended_at)
    WHERE ended_at IS NOT NULL;
