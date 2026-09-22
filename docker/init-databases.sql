-- Works with both a fresh PostgreSQL volume and an existing payment_lab database.
SELECT 'CREATE DATABASE payment_processor_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'payment_processor_db')
\gexec
