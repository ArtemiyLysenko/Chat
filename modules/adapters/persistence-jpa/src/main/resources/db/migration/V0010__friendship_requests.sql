create table friendship_requests (
    id uuid primary key,
    requester_user_id uuid not null references users (id),
    recipient_user_id uuid not null references users (id),
    message_text text,
    status varchar(16) not null,
    created_at timestamptz not null,
    responded_at timestamptz,
    constraint chk_friendship_requests_distinct_users check (requester_user_id <> recipient_user_id),
    constraint chk_friendship_requests_status check (status in ('PENDING', 'ACCEPTED', 'REJECTED')),
    constraint chk_friendship_requests_response_state check (
        (status = 'PENDING' and responded_at is null)
        or (status in ('ACCEPTED', 'REJECTED') and responded_at is not null)
    )
);

create unique index uq_friendship_requests_pending_direction
    on friendship_requests (requester_user_id, recipient_user_id)
    where status = 'PENDING';

create index idx_friendship_requests_requester_status_created_at
    on friendship_requests (requester_user_id, status, created_at desc);

create index idx_friendship_requests_recipient_status_created_at
    on friendship_requests (recipient_user_id, status, created_at desc);
