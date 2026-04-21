create table room_memberships (
    room_id uuid not null references rooms (id) on delete cascade,
    user_id uuid not null references users (id),
    role varchar(16) not null,
    joined_at timestamptz not null,
    primary key (room_id, user_id),
    constraint chk_room_memberships_role check (role in ('OWNER', 'ADMIN', 'MEMBER'))
);

create index idx_room_memberships_user on room_memberships (user_id, room_id);
