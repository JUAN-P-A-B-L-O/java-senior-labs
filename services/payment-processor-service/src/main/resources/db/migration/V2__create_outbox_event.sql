create table outbox_event (
    id uuid primary key,
    event_id uuid not null,
    aggregate_id uuid not null,
    event_type varchar(100) not null,
    payload text not null,
    status varchar(30) not null,
    created_at timestamp with time zone not null default current_timestamp,
    published_at timestamp with time zone,
    constraint uk_outbox_event_event_id unique (event_id)
);

create index idx_outbox_event_status_created_at
    on outbox_event (status, created_at);
