create table users (
    id uuid primary key,
    email varchar(320) not null,
    username varchar(64) not null,
    display_name varchar(128) not null,
    password_hash varchar(255),
    deleted_at timestamptz,
    created_at timestamptz not null
);

create unique index uq_users_email_lower on users (lower(email));
create unique index uq_users_username_lower on users (lower(username));
