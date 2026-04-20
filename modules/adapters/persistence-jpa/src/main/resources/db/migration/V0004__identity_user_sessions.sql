create table user_sessions (
    id uuid primary key,
    user_id uuid not null references users (id),
    created_at timestamptz not null,
    last_seen_at timestamptz not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    user_agent varchar(512) not null default '',
    ip_address varchar(64) not null default ''
);

create index idx_user_sessions_user_state on user_sessions (user_id, revoked_at, expires_at);
