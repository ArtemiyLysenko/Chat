create table federation_peers (
    id uuid primary key,
    peer_domain varchar(255) not null,
    status varchar(32) not null,
    last_connected_at timestamptz,
    last_error_at timestamptz,
    config_json text not null default '{}'
);

create unique index uq_federation_peers_peer_domain on federation_peers (peer_domain);
