alter table messages
    add column parent_message_id uuid references messages (id) on delete set null,
    add column edited_at timestamptz,
    add column deleted_at timestamptz;
