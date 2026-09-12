#!/usr/bin/env bash
set -euo pipefail
umask 077
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
auth_dir="$repo_root/.local-auth"
mkdir -p "$auth_dir"
if [[ ! -f "$auth_dir/private.pem" ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$auth_dir/private.pem"
fi
openssl pkey -in "$auth_dir/private.pem" -pubout -out "$auth_dir/public.pem"
if [[ ! -f "$auth_dir/auth.env" ]]; then
  python3 - "$auth_dir" <<'PY'
import pathlib, secrets, shlex, sys
folder = pathlib.Path(sys.argv[1])
values = {
    'JWT_PUBLIC_KEY': 'file:' + str(folder / 'public.pem'),
    'JWT_PRIVATE_KEY': 'file:' + str(folder / 'private.pem'),
    'AUTH_ADMIN_PASSWORD': secrets.token_urlsafe(24),
    'AUTH_PROCESSOR_PASSWORD': secrets.token_urlsafe(24),
}
(folder / 'auth.env').write_text(''.join('export ' + key + '=' + shlex.quote(value) + '\n' for key, value in values.items()))
PY
fi
printf 'Local authentication configured. Source %s/auth.env before starting services.\n' "$auth_dir"
