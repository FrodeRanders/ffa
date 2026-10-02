#!/usr/bin/env bash
set -euo pipefail

# Kör från projektets rot även om skriptet anropas från en annan katalog.
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

for program in docker mvn; do
    if ! command -v "$program" >/dev/null 2>&1; then
        printf 'Kommandot %s saknas. Installera Docker respektive Maven innan körning.\n' "$program" >&2
        exit 1
    fi
done
if ! docker info >/dev/null 2>&1; then
    printf 'Docker är inte tillgängligt. Starta Docker och kontrollera åtkomsten till daemonen.\n' >&2
    exit 1
fi
docker compose version >/dev/null

printf 'Startar Kafka, PostgreSQL och Neo4j och väntar på hälsokontrollerna.\n'
docker compose --profile graph-tests up -d --wait --wait-timeout 180 postgres kafka neo4j
docker compose run --rm topic

# Skriptet förbereder den lokala Compose-miljön; testerna ska använda samma tjänster.
export FFA_JDBC='jdbc:postgresql://localhost:15432/ffa'
export FFA_DB_USER='ffa'
export FFA_DB_PASSWORD='ffa-demo'
export FFA_KAFKA='localhost:19092'
export FFA_NEO4J='bolt://localhost:17687'
export FFA_NEO4J_USER='neo4j'
export FFA_NEO4J_PASSWORD='ffa-demo-password'

printf 'Kör enhets-, integrations- och graftester.\n'
mvn -q -Pgraph -Dffa.integration=true "$@" test

# Tjänster och volymer lämnas kvar för nästa körning och för inspektion av demon.
printf 'Tester klara. Stoppa tjänsterna vid behov med: docker compose stop\n'
