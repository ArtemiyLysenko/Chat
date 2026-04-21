create table friendships (
    id uuid primary key,
    user_low_id uuid not null references users (id),
    user_high_id uuid not null references users (id),
    created_at timestamptz not null,
    constraint chk_friendships_ordered_pair check (user_low_id < user_high_id)
);

create unique index uq_friendships_pair on friendships (user_low_id, user_high_id);
create index idx_friendships_user_low_created_at on friendships (user_low_id, created_at desc);
create index idx_friendships_user_high_created_at on friendships (user_high_id, created_at desc);
