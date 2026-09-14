#!/bin/sh
set -eu
umask 077

# Keep the signing identity and bootstrap credentials across container rebuilds.
mkdir -p /keys/private /keys/public
if [ ! -s /keys/private/private.pem ]; then
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /keys/private/private.pem
fi
openssl pkey -in /keys/private/private.pem -pubout -out /keys/public/public.pem
for credential in admin-password processor-password; do
    if [ ! -s "/keys/private/$credential" ]; then
        openssl rand -hex 24 > "/keys/private/$credential"
    fi
done
chown -R 10001:10001 /keys/private /keys/public
chmod 700 /keys/private
chmod 755 /keys/public
chmod 600 /keys/private/*
chmod 644 /keys/public/public.pem
