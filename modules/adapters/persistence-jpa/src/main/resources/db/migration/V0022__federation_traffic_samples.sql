create table federation_traffic_samples (
    id uuid primary key,
    peer_id uuid not null references federation_peers (id) on delete cascade,
    sampled_at timestamptz not null,
    inbound_messages bigint not null,
    outbound_messages bigint not null,
    inbound_stanzas bigint not null,
    outbound_stanzas bigint not null,
    error_count bigint not null
);

create index idx_federation_traffic_samples_peer_sampled_at
    on federation_traffic_samples (peer_id, sampled_at desc);
