create table attachments (
    id uuid primary key,
    storage_key text not null unique,
    original_name text not null,
    media_type text not null,
    size_bytes bigint not null,
    sha256 char(64) not null,
    uploaded_by_user_id uuid not null references users (id),
    chat_target_type varchar(16) not null,
    room_id uuid references rooms (id) on delete cascade,
    direct_dialog_id uuid references direct_dialogs (id) on delete cascade,
    created_at timestamptz not null,
    constraint chk_attachments_target_exclusive check (num_nonnulls(room_id, direct_dialog_id) = 1),
    constraint chk_attachments_chat_target_type check (
        (chat_target_type = 'ROOM' and room_id is not null and direct_dialog_id is null)
        or (chat_target_type = 'DIRECT' and direct_dialog_id is not null and room_id is null)
    ),
    constraint chk_attachments_size_positive check (size_bytes > 0),
    constraint chk_attachments_sha256_hex check (sha256 ~ '^[0-9a-f]{64}$')
);

create index idx_attachments_room_created_at on attachments (room_id, created_at desc) where room_id is not null;
create index idx_attachments_direct_dialog_created_at on attachments (direct_dialog_id, created_at desc) where direct_dialog_id is not null;
