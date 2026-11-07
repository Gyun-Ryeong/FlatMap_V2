#!/bin/sh
# 최초 볼륨 초기화 때만 실행된다. 통합 테스트용 DB(<DB_NAME>_test)를 만든다.
# 테스트는 이 DB 를 Flyway clean 후 다시 마이그레이션하므로 개발 DB 와 반드시 분리한다.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE "${POSTGRES_DB}_test";
EOSQL
