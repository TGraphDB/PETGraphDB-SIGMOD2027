# TimescaleDB + Apache AGE Docker Image

This image is based on `timescale/timescaledb-ha:pg16` and installs Apache AGE from the `PG16` branch.

## Build

```bash
docker build -t timescaledb-age:pg16 .
```

## Run

```bash
docker run -d \
  --name timescaledb-age \
  -p 5432:5432 \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=postgres \
  timescaledb-age:pg16
```

## Verify

```bash
docker exec -it timescaledb-age psql -U postgres -d postgres
```

```sql
\dx
SHOW session_preload_libraries;
SHOW search_path;

SELECT create_graph('demo');
SELECT *
FROM cypher('demo', $$
  CREATE (p:Person {name: 'Alice'})
  RETURN p
$$) AS (p agtype);

SELECT *
FROM cypher('demo', $$
  MATCH (p:Person)
  RETURN p
$$) AS (p agtype);
```

For a persistent volume with the TimescaleDB HA image, mount PostgreSQL data at `/home/postgres/pgdata/data`.
