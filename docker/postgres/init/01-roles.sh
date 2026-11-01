#!/bin/sh
# Solo durante la primera inicialización del volumen PostgreSQL.
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres   -v db_name="$POS_DB_NAME" -v owner_password="$POS_OWNER_PASSWORD" -v app_password="$POS_APP_PASSWORD" <<'EOSQL'
CREATE ROLE pos_owner LOGIN PASSWORD :'owner_password';
CREATE ROLE pos_app LOGIN PASSWORD :'app_password';
CREATE DATABASE :"db_name" OWNER pos_owner ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db_name" TO pos_owner, pos_app;
EOSQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POS_DB_NAME" <<'EOSQL'
ALTER SCHEMA public OWNER TO pos_owner;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
EOSQL
