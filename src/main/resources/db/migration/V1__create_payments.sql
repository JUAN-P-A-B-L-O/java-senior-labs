create table payments (
    id uuid primary key,
    amount numeric(19, 2) not null,
    currency varchar(10) not null,
    description varchar(255),
    status varchar(30) not null,
    created_at timestamp with time zone not null default current_timestamp
);
