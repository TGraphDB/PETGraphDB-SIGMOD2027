FROM timescale/timescaledb-ha:pg16

ARG PG_MAJOR=16
ARG AGE_BRANCH=PG16
ARG PG_CONFIG=/usr/lib/postgresql/16/bin/pg_config

USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        flex \
        bison \
        git \
        postgresql-server-dev-${PG_MAJOR} \
    && git clone --depth 1 --branch "${AGE_BRANCH}" https://github.com/apache/age.git /tmp/age \
    && make -C /tmp/age PG_CONFIG="${PG_CONFIG}" install \
    && rm -rf /tmp/age \
    && apt-get purge -y --auto-remove \
        build-essential \
        flex \
        bison \
        git \
        postgresql-server-dev-${PG_MAJOR} \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY docker-entrypoint-initdb.d/00-init-extensions.sh /docker-entrypoint-initdb.d/00-init-extensions.sh
RUN chmod 0755 /docker-entrypoint-initdb.d/00-init-extensions.sh
