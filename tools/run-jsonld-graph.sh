#!/usr/bin/env bash
set -euo pipefail

raw_json=${1:-qwerty.json}
context=${2:-src/main/resources/context/ffa-1.0.jsonld}
sdl=${3:-src/main/resources/schema/ffa.graphqls}

mvn -q -Dexec.args="${raw_json} ${context} ${sdl}" exec:java@jsonld-graph
