create table room_invites (
    room_id uuid not null references rooms (id) on delete cascade,
    invited_user_id uuid not null references users (id),
    invited_by_user_id uuid not null references users (id),
    status varchar(16) not null,
    created_at timestamptz not null,
    accepted_at timestamptz,
    primary key (room_id, invited_user_id),
    constraint chk_room_invites_status check (status in ('PENDING', 'ACCEPTED'))
);

create index idx_room_invites_invited_by on room_invites (invited_by_user_id, room_id);
