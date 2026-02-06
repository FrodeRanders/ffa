#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: run-transform.sh <raw-json> [--context path] [--frame path] [--mapping path] [--out path] [--import neo4j|cypher|none] [--cypher-out path] [--neo4j-opts key=value,...]" >&2
  echo "Requires RMLMapper-Java jar at tools/rmlmapper.jar or via RMLMAPPER_JAR env var." >&2
  exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

raw_json="$1"
shift

mapping=""
out=""
pass_args=("$raw_json")

while [[ $# -gt 0 ]]; do
  case "$1" in
    --context|--frame|--mapping|--out|--import|--cypher-out|--neo4j-opts)
      pass_args+=("$1" "$2")
      if [[ "$1" == "--mapping" ]]; then
        mapping="$2"
      elif [[ "$1" == "--out" ]]; then
        out="$2"
      fi
      shift 2
      ;;
    *)
      pass_args+=("$1")
      shift
      ;;
  esac
done

mapping="${mapping:-src/main/resources/mapping/ffa.rml.ttl}"
out="${out:-target/ffa-out.ttl}"

if [[ "$mapping" != /* ]]; then
  mapping="$ROOT_DIR/$mapping"
fi
if [[ "$out" != /* ]]; then
  out="$ROOT_DIR/$out"
fi

out_dir="$(dirname "$out")"
mkdir -p "$out_dir"

mvn -q -Dexec.args="${pass_args[*]}" exec:java@jsonld-transform

RMLMAPPER_JAR="${RMLMAPPER_JAR:-$ROOT_DIR/tools/rmlmapper.jar}"
if [[ "$RMLMAPPER_JAR" != /* ]]; then
  RMLMAPPER_JAR="$ROOT_DIR/$RMLMAPPER_JAR"
fi
if [[ ! -f "$RMLMAPPER_JAR" ]]; then
  RMLMAPPER_VERSION="${RMLMAPPER_VERSION:-7.3.3}"
  RMLMAPPER_CLASSIFIER="${RMLMAPPER_CLASSIFIER:-r374-all}"
  if [[ -n "$RMLMAPPER_CLASSIFIER" ]]; then
    RMLMAPPER_COORD="be.ugent.rml:rmlmapper:${RMLMAPPER_VERSION}:jar:${RMLMAPPER_CLASSIFIER}"
  else
    RMLMAPPER_COORD="be.ugent.rml:rmlmapper:${RMLMAPPER_VERSION}"
  fi
  mvn -q dependency:copy \
    -Dartifact="$RMLMAPPER_COORD" \
    -DoutputDirectory="$ROOT_DIR/tools" \
    -DdestFileName="$(basename "$RMLMAPPER_JAR")"
  if [[ -n "$RMLMAPPER_CLASSIFIER" ]]; then
    alt="$ROOT_DIR/tools/rmlmapper-${RMLMAPPER_VERSION}-${RMLMAPPER_CLASSIFIER}.jar"
    if [[ -f "$alt" && ! -f "$RMLMAPPER_JAR" ]]; then
      mv "$alt" "$RMLMAPPER_JAR"
    fi
  fi
fi
if [[ ! -f "$RMLMAPPER_JAR" ]]; then
  echo "Missing RMLMapper-Java jar after download: $RMLMAPPER_JAR" >&2
  exit 2
fi
if [[ ! -f "$mapping" ]]; then
  echo "Missing mapping file: $mapping" >&2
  exit 2
fi

mapping_copy="$out_dir/ffa.rml.ttl"
printf '@base <urn:ffa:> .\n' > "$mapping_copy"
cat "$mapping" >> "$mapping_copy"

(
  cd "$out_dir"
  java -jar "$RMLMAPPER_JAR" -m "$(basename "$mapping_copy")" -o "$out" -s turtle
)
