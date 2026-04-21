create table moderation_audit_events (
    id uuid primary key,
    room_id uuid references rooms (id) on delete cascade,
    actor_user_id uuid not null references users (id),
    target_user_id uuid references users (id),
    message_id uuid,
    action varchar(32) not null,
    reason text,
    metadata_json text,
    created_at timestamptz not null,
    constraint chk_moderation_audit_events_action check (
        action in (
            'MEMBER_REMOVED',
            'MEMBER_BANNED',
            'MEMBER_UNBANNED',
            'ADMIN_GRANTED',
            'ADMIN_REVOKED',
            'MESSAGE_DELETED',
            'ROOM_DELETED'
        )
    )
);

create index idx_moderation_audit_events_room_created_at on moderation_audit_events (room_id, created_at desc);
