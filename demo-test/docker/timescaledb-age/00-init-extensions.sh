#!/usr/bin/env bash
set -euo pipefail

postgres_user="${POSTGRES_USER:-postgres}"
db_name="${POSTGRES_DB:-${postgres_user}}"

psql -v ON_ERROR_STOP=1 \
    --username "${postgres_user}" \
    --dbname "${db_name}" \
    -v dbname="${db_name}" <<'SQL'
CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS age;

ALTER DATABASE :"dbname" SET session_preload_libraries = 'age';
ALTER DATABASE :"dbname" SET search_path = ag_catalog, "$user", public;
SQL
