create table idempotency_keys (
    idempotency_key varchar(255) primary key,
    request_body_hash varchar(128) not null,
    status varchar(30) not null,
    expires_at timestamp with time zone not null
);
