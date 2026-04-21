create table chat_unread_markers (
    id uuid primary key,
    user_id uuid not null references users (id) on delete cascade,
    room_id uuid references rooms (id) on delete cascade,
    direct_dialog_id uuid references direct_dialogs (id) on delete cascade,
    last_read_message_id uuid not null references messages (id) on delete cascade,
    updated_at timestamptz not null,
    constraint chk_chat_unread_markers_target_exclusive check (num_nonnulls(room_id, direct_dialog_id) = 1)
);

create unique index uq_chat_unread_markers_room on chat_unread_markers (user_id, room_id) where room_id is not null;
create unique index uq_chat_unread_markers_direct_dialog on chat_unread_markers (user_id, direct_dialog_id) where direct_dialog_id is not null;
