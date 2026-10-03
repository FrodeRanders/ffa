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

compose_profiles=(--profile graph-tests)
services=(postgres kafka neo4j)
maven_flags=(-Pgraph -Dffa.integration=true)
if [[ "${1:-}" == '--pipeline' ]]; then
    shift
    compose_profiles+=(--profile pipeline-tests)
    services+=(rustfs)
    maven_flags=(-Pgraph,pipeline -Dffa.integration=true -Dffa.pipeline=true)
fi

printf 'Startar testmiljön och väntar på hälsokontrollerna: %s\n' "${services[*]}"
docker compose "${compose_profiles[@]}" up -d --wait --wait-timeout 180 "${services[@]}"
docker compose run --rm topic

# Skriptet förbereder den lokala Compose-miljön; testerna ska använda samma tjänster.
export FFA_JDBC='jdbc:postgresql://localhost:15432/ffa'
export FFA_DB_USER='ffa'
export FFA_DB_PASSWORD='ffa-demo'
export FFA_KAFKA='localhost:19092'
export FFA_NEO4J='bolt://localhost:17687'
export FFA_NEO4J_USER='neo4j'
export FFA_NEO4J_PASSWORD='ffa-demo-password'
export FFA_S3='http://localhost:19000'
export FFA_S3_ACCESS_KEY='ffa-demo'
export FFA_S3_SECRET_KEY='ffa-demo-object-store'

printf 'Kör enhets-, integrations- och graftester.\n'
mvn -q "${maven_flags[@]}" "$@" test

# Tjänster och volymer lämnas kvar för nästa körning och för inspektion av demon.
printf 'Tester klara. Stoppa tjänsterna vid behov med: docker compose stop\n'
