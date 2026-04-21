create table messages (
    id uuid primary key,
    room_id uuid references rooms (id) on delete cascade,
    direct_dialog_id uuid references direct_dialogs (id) on delete cascade,
    author_user_id uuid not null references users (id),
    body_text text not null,
    state varchar(16) not null,
    created_at timestamptz not null,
    constraint chk_messages_target_exclusive check (num_nonnulls(room_id, direct_dialog_id) = 1),
    constraint chk_messages_state check (state in ('ACTIVE', 'EDITED', 'DELETED')),
    constraint chk_messages_body_octets check (octet_length(body_text) <= 3072)
);

create index idx_messages_room_history on messages (room_id, created_at desc, id desc) where room_id is not null;
create index idx_messages_direct_dialog_history on messages (direct_dialog_id, created_at desc, id desc) where direct_dialog_id is not null;
