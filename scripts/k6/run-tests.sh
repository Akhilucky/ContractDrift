#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K6_DIR="${SCRIPT_DIR}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

check_k6() {
    if ! command -v k6 &> /dev/null; then
        log_error "k6 is not installed. Install it from https://grafana.com/docs/k6/latest/set-up/install-k6/"
        exit 1
    fi
    log_info "k6 version: $(k6 version 2>&1 | head -1)"
}

check_target() {
    local url="${1:-http://localhost:8090}"
    if curl -sf "${url}" > /dev/null 2>&1; then
        log_info "Target ${url} is reachable"
        return 0
    else
        log_warn "Target ${url} is not reachable — tests may fail"
        return 1
    fi
}

run_smoke() {
    log_info "Running smoke test (1 VU, 1m)..."
    k6 run \
        --summary-trend-stats='avg,min,med,max,p(90),p(95),p(99)' \
        "${K6_DIR}/smoke-test.js" \
        || {
            log_error "Smoke test failed"
            return 1
        }
    log_info "Smoke test completed successfully"
}

run_load() {
    log_info "Running load test (ramp to 200 VUs, 9m)..."
    k6 run \
        --summary-trend-stats='avg,min,med,max,p(90),p(95),p(99)' \
        "${K6_DIR}/load-test.js" \
        || {
            log_error "Load test failed"
            return 1
        }
    log_info "Load test completed successfully"
}

usage() {
    echo "Usage: $0 [smoke|load|all]"
    echo ""
    echo "  smoke  - Run smoke test only (1 VU, 1 minute)"
    echo "  load   - Run load test only (100-200 VUs, 9 minutes)"
    echo "  all    - Run smoke test then load test"
    echo ""
    echo "Environment variables:"
    echo "  TARGET_URL  - Base URL of the target service (default: http://localhost:8090)"
    echo ""
    echo "Examples:"
    echo "  $0 smoke"
    echo "  $0 all"
    echo "  TARGET_URL=http://staging.example.com:8090 $0 load"
}

main() {
    local mode="${1:-all}"
    local target_url="${TARGET_URL:-http://localhost:8090}"

    log_info "Contract-Drift Sentinel k6 Performance Tests"
    log_info "Target URL: ${target_url}"

    check_k6

    case "${mode}" in
        smoke)
            check_target "${target_url}" || true
            run_smoke
            ;;
        load)
            check_target "${target_url}" || true
            run_load
            ;;
        all)
            check_target "${target_url}" || true
            run_smoke
            echo ""
            log_info "Smoke test passed. Starting load test..."
            run_load
            ;;
        -h|--help|help)
            usage
            ;;
        *)
            log_error "Unknown mode: ${mode}"
            usage
            exit 1
            ;;
    esac

    log_info "All tests completed."
}

main "$@"
