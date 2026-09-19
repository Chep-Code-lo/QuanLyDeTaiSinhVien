#!/bin/sh
set -eu

MYSQL_APP_USER="${MYSQL_APP_USER:-ql_de_tai_app}"
MYSQL_APP_PASSWORD="${MYSQL_APP_PASSWORD:-change-me-local}"

if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
  echo "MYSQL_ROOT_PASSWORD must be set."
  exit 1
fi

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE USER IF NOT EXISTS '${MYSQL_APP_USER}'@'%' IDENTIFIED BY '${MYSQL_APP_PASSWORD}';
GRANT ALL PRIVILEGES ON \`ql_de_tai_sinh_vien\`.* TO '${MYSQL_APP_USER}'@'%';
FLUSH PRIVILEGES;
EOSQL
