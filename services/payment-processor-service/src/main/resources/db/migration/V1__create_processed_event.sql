create table processed_event (
    id uuid primary key,
    event_id uuid not null,
    processed_at timestamp with time zone not null,
    constraint uk_processed_event_event_id unique (event_id)
);
