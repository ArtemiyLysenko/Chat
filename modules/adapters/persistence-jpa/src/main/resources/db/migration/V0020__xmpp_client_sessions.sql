create table xmpp_client_sessions (
    id varchar(128) primary key,
    user_id uuid references users (id) on delete set null,
    jid varchar(255),
    resource varchar(128),
    status varchar(32) not null,
    connected_at timestamptz not null,
    disconnected_at timestamptz,
    remote_address varchar(255),
    server_node varchar(128) not null
);

create index idx_xmpp_client_sessions_user_status_connected_at
    on xmpp_client_sessions (user_id, status, connected_at desc);
create index idx_xmpp_client_sessions_connected_at
    on xmpp_client_sessions (connected_at desc);
