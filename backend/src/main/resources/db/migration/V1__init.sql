-- One row per connected Discord server. Every other table is scoped by guild_id,
-- which is what keeps servers isolated from each other.
CREATE TABLE guilds (
    guild_id             TEXT PRIMARY KEY,
    name                 TEXT        NOT NULL,
    post_channel_id      TEXT,
    report_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    status_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    ai_enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    ephemeral_replies    BOOLEAN     NOT NULL DEFAULT FALSE,
    default_priority     TEXT        NOT NULL DEFAULT 'LOW',
    mirror_min_priority  TEXT        NOT NULL DEFAULT 'LOW',
    mirror_webhook_enc   TEXT,        -- AES-GCM ciphertext; never sent to the browser
    mirror_kind          TEXT,        -- SLACK | DISCORD
    connected_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE keyword_rules (
    id        BIGSERIAL PRIMARY KEY,
    guild_id  TEXT NOT NULL REFERENCES guilds (guild_id) ON DELETE CASCADE,
    keyword   TEXT NOT NULL,
    priority  TEXT NOT NULL,
    position  INT  NOT NULL,
    UNIQUE (guild_id, keyword)
);

-- Every signed interaction Discord delivers. The primary key IS the dedup guard:
-- a second delivery of the same interaction id fails the insert and does nothing.
CREATE TABLE interactions (
    interaction_id    TEXT PRIMARY KEY,
    guild_id          TEXT,
    interaction_type  SMALLINT    NOT NULL,
    command           TEXT,
    user_id           TEXT,
    username          TEXT,
    outcome           TEXT        NOT NULL DEFAULT 'RECEIVED',
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX interactions_guild_time_idx ON interactions (guild_id, received_at DESC);

CREATE TABLE reports (
    id                 BIGSERIAL PRIMARY KEY,
    guild_id           TEXT        NOT NULL REFERENCES guilds (guild_id) ON DELETE CASCADE,
    interaction_id     TEXT        NOT NULL UNIQUE REFERENCES interactions (interaction_id),
    channel_id         TEXT,
    user_id            TEXT,
    username           TEXT,
    text               TEXT        NOT NULL,
    priority           TEXT,
    priority_source    TEXT,       -- RULE | AI | DEFAULT
    matched_keyword    TEXT,
    ai_status          TEXT        NOT NULL DEFAULT 'PENDING',  -- PENDING | DONE | SKIPPED | FAILED
    ai_summary         TEXT,
    ai_category        TEXT,
    status             TEXT        NOT NULL DEFAULT 'OPEN',     -- OPEN | ESCALATED | ACKNOWLEDGED
    acted_by           TEXT,
    post_channel_id    TEXT,
    channel_message_id TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX reports_guild_time_idx ON reports (guild_id, created_at DESC);

-- Durable work queue (outbox). Slow or failure-prone work (AI, Discord follow-ups,
-- mirror webhooks) runs from here with retries, so nothing is lost on a crash.
CREATE TABLE jobs (
    id              BIGSERIAL PRIMARY KEY,
    type            TEXT        NOT NULL,
    guild_id        TEXT,
    interaction_id  TEXT,
    report_id       BIGINT,
    payload         TEXT        NOT NULL,  -- may hold a 15-minute interaction token; never exposed via the API
    status          TEXT        NOT NULL DEFAULT 'PENDING',  -- PENDING | RUNNING | DONE | FAILED
    attempts        INT         NOT NULL DEFAULT 0,
    max_attempts    INT         NOT NULL,
    next_run_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at       TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX jobs_due_idx ON jobs (next_run_at) WHERE status = 'PENDING';
CREATE INDEX jobs_status_idx ON jobs (status, updated_at DESC);

-- Human-readable timeline shown live on the dashboard.
CREATE TABLE activity_log (
    id              BIGSERIAL PRIMARY KEY,
    guild_id        TEXT,
    interaction_id  TEXT,
    report_id       BIGINT,
    job_id          BIGINT,
    level           TEXT        NOT NULL,  -- INFO | WARN | ERROR
    event           TEXT        NOT NULL,
    message         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX activity_time_idx ON activity_log (created_at DESC);
CREATE INDEX activity_guild_time_idx ON activity_log (guild_id, created_at DESC);
