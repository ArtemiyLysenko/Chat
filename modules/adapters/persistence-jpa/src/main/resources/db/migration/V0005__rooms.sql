create table rooms (
    id uuid primary key,
    owner_user_id uuid not null references users (id),
    name varchar(160) not null,
    description text,
    visibility varchar(16) not null,
    created_at timestamptz not null,
    constraint chk_rooms_visibility check (visibility in ('PUBLIC', 'PRIVATE'))
);

create unique index uq_rooms_name_lower on rooms (lower(name));
create index idx_rooms_visibility_name on rooms (visibility, name);
