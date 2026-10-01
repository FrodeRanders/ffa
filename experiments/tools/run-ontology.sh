#!/usr/bin/env bash
set -euo pipefail

include_includes=""
if [[ "${1:-}" == "--include-includes" ]]; then
  include_includes="--include-includes"
  shift
fi

schema=${1:-src/main/resources/schema/ffa.graphqls}
out=${2:-src/main/resources/ontology/ffa-1.0.ttl}

python3 tools/generate_ontology.py $include_includes "$schema" "$out"

echo "Wrote ontology to $out"
