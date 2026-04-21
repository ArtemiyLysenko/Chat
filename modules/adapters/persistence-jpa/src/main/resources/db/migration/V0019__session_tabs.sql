create table session_tabs (
    id uuid primary key,
    session_id uuid not null references user_sessions (id) on delete cascade,
    tab_key varchar(128) not null,
    connected_at timestamptz not null,
    last_activity_at timestamptz,
    last_ping_at timestamptz not null,
    closed_at timestamptz
);

create unique index uq_session_tabs_session_tab_key on session_tabs (session_id, tab_key);
create index idx_session_tabs_last_ping_at on session_tabs (last_ping_at);
create index idx_session_tabs_session_state on session_tabs (session_id, closed_at);
