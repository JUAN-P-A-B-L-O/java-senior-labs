create table payment_result_processed_event (
    id uuid primary key,
    event_id uuid not null,
    processed_at timestamp with time zone not null,
    constraint uk_payment_result_processed_event_event_id unique (event_id)
);
