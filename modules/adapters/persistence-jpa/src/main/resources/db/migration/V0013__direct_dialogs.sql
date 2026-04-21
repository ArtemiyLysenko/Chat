create table direct_dialogs (
    id uuid primary key,
    user_low_id uuid not null references users (id),
    user_high_id uuid not null references users (id),
    created_at timestamptz not null,
    constraint chk_direct_dialogs_ordered_pair check (user_low_id < user_high_id)
);

create unique index uq_direct_dialogs_pair on direct_dialogs (user_low_id, user_high_id);
create index idx_direct_dialogs_user_low_created_at on direct_dialogs (user_low_id, created_at desc);
create index idx_direct_dialogs_user_high_created_at on direct_dialogs (user_high_id, created_at desc);
