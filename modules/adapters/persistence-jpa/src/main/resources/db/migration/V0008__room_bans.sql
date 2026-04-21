create table room_bans (
    room_id uuid not null references rooms (id) on delete cascade,
    user_id uuid not null references users (id),
    banned_by_user_id uuid not null references users (id),
    reason text,
    created_at timestamptz not null,
    primary key (room_id, user_id)
);

create index idx_room_bans_room_created_at on room_bans (room_id, created_at desc);
create index idx_room_bans_user on room_bans (user_id, room_id);
