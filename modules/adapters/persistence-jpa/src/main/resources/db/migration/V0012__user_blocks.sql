create table user_blocks (
    id uuid primary key,
    blocker_user_id uuid not null references users (id) on delete cascade,
    blocked_user_id uuid not null references users (id) on delete cascade,
    created_at timestamptz not null,
    constraint chk_user_blocks_distinct_users check (blocker_user_id <> blocked_user_id)
);

create unique index uq_user_blocks_direction on user_blocks (blocker_user_id, blocked_user_id);
create index idx_user_blocks_blocker_created_at on user_blocks (blocker_user_id, created_at desc);
create index idx_user_blocks_blocked_created_at on user_blocks (blocked_user_id, created_at desc);
