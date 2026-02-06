#!/usr/bin/env bash
set -euo pipefail

raw_json=${1:-src/test/resources/fixtures/qwerty.json}
context=${2:-src/main/resources/context/ffa-1.0.jsonld}

mvn -q -Dexec.args="${raw_json} ${context}" exec:java@jsonld-expansion
