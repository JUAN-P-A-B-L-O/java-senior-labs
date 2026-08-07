create table outbox_event (
    id uuid primary key,
    event_id uuid not null,
    aggregate_id uuid not null,
    event_type varchar(100) not null,
    payload text not null,
    status varchar(30) not null,
    created_at timestamp with time zone not null default current_timestamp,
    published_at timestamp with time zone
);

alter table outbox_event
    add constraint uk_outbox_event_event_id unique (event_id);

create index idx_outbox_event_aggregate_id_event_type
    on outbox_event (aggregate_id, event_type);
