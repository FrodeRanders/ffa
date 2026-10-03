#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

# Fast version och checksumma gör körningen reproducerbar. JAR-filen är inte ett Maven-körberoende.
tools_version='1.7.4'
tools_sha256='936a262061c914694dfd669a543be24573c45d5aa0ff20a8b96b23d01e050e88'
tools_dir="$project_root/target/tla/tools"
mkdir -p "$tools_dir"
tools_jar="${TLA_TOOLS_JAR:-$tools_dir/tla2tools-$tools_version.jar}"
workers="${TLC_WORKERS:-2}"
heap="${TLC_HEAP:-1g}"

command -v java >/dev/null || { printf 'Java saknas. Använd projektets JDK 25.\n' >&2; exit 1; }
if command -v shasum >/dev/null; then
    checksum() { shasum -a 256 "$1" | awk '{print $1}'; }
elif command -v sha256sum >/dev/null; then
    checksum() { sha256sum "$1" | awk '{print $1}'; }
else
    printf 'shasum eller sha256sum krävs för att kontrollera verktygets checksumma.\n' >&2
    exit 1
fi

if [[ ! -f "$tools_jar" ]]; then
    if [[ -n "${TLA_TOOLS_JAR:-}" ]]; then
        printf 'Angiven TLA_TOOLS_JAR finns inte: %s\n' "$tools_jar" >&2
        exit 1
    fi
    command -v curl >/dev/null || { printf 'curl behövs för första hämtningen.\n' >&2; exit 1; }
    temporary="$(mktemp "$tools_dir/download.XXXXXX")"
    trap 'rm -f -- "$temporary"' EXIT
    curl -fsSL --connect-timeout 15 --max-time 120 \
        "https://github.com/tlaplus/tlaplus/releases/download/v$tools_version/tla2tools.jar" -o "$temporary"
    if [[ "$(checksum "$temporary")" != "$tools_sha256" ]]; then
        printf 'Fel checksumma för hämtad TLA+-JAR.\n' >&2
        exit 1
    fi
    mv -- "$temporary" "$tools_jar"
    trap - EXIT
fi
if [[ "$(checksum "$tools_jar")" != "$tools_sha256" ]]; then
    printf 'Fel checksumma för TLA+-JAR: %s\n' "$tools_jar" >&2
    exit 1
fi

run_dir="$(mktemp -d "$project_root/target/tla/run.XXXXXX")"
if [[ $# -eq 0 ]]; then
    set -- strict resilient strict-recovery strict-live resilient-live broken-lock exactly-once strict-ordering kafka-before-cache false-graph-confirmation graph-regression offset-without-forward \
        backend-index-first backend-object-first backend-object-confirm \
        backend-index-live backend-object-live backend-repair-live backend-duplicates backend-version-read \
        backend-index-proof backend-missing-repair backend-overwrite backend-arrival-latest backend-mixed-orders backend-mixed-valid \
        receipts receipts-live receipts-two-deliveries receipts-corrupt receipts-offset-first receipts-graph-first receipts-missing-reconcile receipts-wrong-identity
fi
for model in "$@"; do
    case "$model" in
        strict|resilient|strict-recovery|strict-live|resilient-live|broken-lock|exactly-once|strict-ordering|kafka-before-cache|false-graph-confirmation|graph-regression|offset-without-forward)
            spec_dir='spec/delivery'; module='Delivery' ;;
        receipts|receipts-live|receipts-two-deliveries|receipts-corrupt|receipts-offset-first|receipts-graph-first|receipts-missing-reconcile|receipts-wrong-identity)
            spec_dir='spec/delivery'; module='Receipts' ;;
        backend-index-first|backend-object-first|backend-object-confirm|backend-index-live|backend-object-live|backend-repair-live|backend-duplicates|backend-version-read|backend-index-proof|backend-missing-repair|backend-overwrite|backend-arrival-latest|backend-mixed-orders|backend-mixed-valid)
            spec_dir='spec/backend'; module='Backend' ;;
        *) printf 'Okänd modell: %s\n' "$model" >&2; exit 1 ;;
    esac
    log="$run_dir/$model.log"
    printf 'Kontrollerar %s ...\n' "$model"
    status=0
    java -XX:+UseParallelGC "-Xmx$heap" -cp "$tools_jar" tlc2.TLC \
        -workers "$workers" -metadir "$run_dir/$model" \
        -config "$spec_dir/$model.cfg" "$spec_dir/$module.tla" >"$log" 2>&1 || status=$?
    expected=''
    case "$model" in
        receipts-offset-first) expected='Invariant OffsetHasInbox is violated' ;;
        receipts-graph-first) expected='Invariant ReceiptEvidenceSound is violated' ;;
        receipts-missing-reconcile) expected='Invariant RestoredGraphEvidence is violated' ;;
        receipts-wrong-identity) expected='Invariant ConfirmedIdentity is violated' ;;
        broken-lock) expected='Invariant NoConcurrentProcessWorkers is violated' ;;
        exactly-once) expected='Invariant NoDuplicateAcceptance is violated' ;;
        kafka-before-cache) expected='Invariant KafkaHasLocalCopy is violated' ;;
        offset-without-forward) expected='Invariant OffsetHasForwarded is violated' ;;
        graph-regression) expected='Invariant GraphNeverRegresses is violated' ;;
        false-graph-confirmation) expected='Invariant GraphEvidenceSound is violated' ;;
        backend-index-proof) expected='Invariant ConfirmedHasObject is violated' ;;
        backend-missing-repair) expected='Invariant ConfirmedHasIndexOrRepair is violated' ;;
        backend-overwrite|backend-mixed-orders) expected='Invariant IdentityImmutable is violated' ;;
        backend-arrival-latest) expected='Invariant ReadDoesNotRegress is violated' ;;
    esac
    if [[ -n "$expected" ]]; then
        # Ett godtyckligt verktygsfel får inte räknas som en lyckad negativ kontroll.
        if [[ "$status" -eq 0 ]] || ! grep -Fq "$expected" "$log"; then
            cat "$log" >&2
            printf 'Den trasiga modellen gav inte det förväntade motexemplet.\n' >&2
            exit 1
        fi
        printf 'Förväntat motexempel: %s\n' "$expected"
    elif [[ "$status" -ne 0 ]]; then
        cat "$log" >&2
        exit "$status"
    else
        tail -n 7 "$log"
    fi
done
printf 'Modellkontroller klara. Fullständiga loggar och motexempel: %s\n' "$run_dir"
