create table password_reset_tokens (
    id uuid primary key,
    user_id uuid not null references users (id),
    token_hash varchar(64) not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at timestamptz
);

create unique index uq_password_reset_tokens_hash on password_reset_tokens (token_hash);
create index idx_password_reset_tokens_user_state on password_reset_tokens (user_id, used_at, expires_at);
