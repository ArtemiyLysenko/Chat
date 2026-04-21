create table message_attachments (
    attachment_id uuid primary key references attachments (id) on delete cascade,
    message_id uuid not null references messages (id) on delete cascade,
    comment_text text,
    sort_order integer not null,
    constraint chk_message_attachments_sort_order_nonnegative check (sort_order >= 0),
    constraint chk_message_attachments_comment_octets check (comment_text is null or octet_length(comment_text) <= 3072)
);

create index idx_message_attachments_message_sort_order on message_attachments (message_id, sort_order asc);
