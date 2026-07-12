#!/bin/bash
set -euo pipefail

# Contract-Drift Sentinel Load Test
# Simulates 50 service pairs, 10,000 requests/minute

readonly TOTAL_PAIRS=50
readonly REQUESTS_PER_PAIR=200
readonly DURATION_SECONDS=60
readonly TARGET_URL="http://localhost:8090/api/order"
readonly START_TIME=$(date +%s)

echo "=========================================="
echo " Contract-Drift Sentinel Load Test"
echo "=========================================="
echo "Service pairs:     $TOTAL_PAIRS"
echo "Requests/pair:     $REQUESTS_PER_PAIR"
echo "Target duration:   ${DURATION_SECONDS}s"
echo "Target URL:        $TARGET_URL"
echo "=========================================="

# Track timing data for stats
latencies=()
error_count=0
total_requests=0

generate_payload() {
  local pair_id=$1
  local req_id=$2
  cat <<EOF
{
  "order_id": "load_${pair_id}_${req_id}",
  "amount": $(( RANDOM % 1000 + 1 )),
  "currency": "USD",
  "items": ["item_a", "item_b", "item_c"],
  "user": {
    "name": "load-user-${pair_id}",
    "email": "user${pair_id}@test.com"
  },
  "metadata": {
    "pair_id": ${pair_id},
    "request_id": ${req_id},
    "source": "load-test"
  }
}
EOF
}

echo "Starting load generation..."
echo ""

# Run requests in batches, tracking timing
for pair in $(seq 1 $TOTAL_PAIRS); do
  for req in $(seq 1 $REQUESTS_PER_PAIR); do
    REQ_START=$(date +%s%N)

    payload=$(generate_payload "$pair" "$req")

    response_code=$(curl -s -o /dev/null -w "%{http_code}" \
      -X POST "$TARGET_URL" \
      -H "Content-Type: application/json" \
      -d "$payload" \
      --connect-timeout 5 \
      --max-time 30)

    REQ_END=$(date +%s%N)
    elapsed_ms=$(( (REQ_END - REQ_START) / 1000000 ))

    if [ "$response_code" = "200" ]; then
      latencies+=("$elapsed_ms")
    else
      error_count=$((error_count + 1))
    fi

    total_requests=$((total_requests + 1))
  done

  # Progress indicator
  if [ $((pair % 10)) -eq 0 ]; then
    elapsed=$(( $(date +%s) - START_TIME ))
    echo "  Progress: $pair / $TOTAL_PAIRS pairs complete (${elapsed}s elapsed)"
  fi
done

END_TIME=$(date +%s)
TOTAL_TIME=$((END_TIME - START_TIME))

echo ""
echo "=========================================="
echo " Results Summary"
echo "=========================================="
echo "Total requests:    $total_requests"
echo "Successful:        ${#latencies[@]}"
echo "Errors:            $error_count"
echo "Total time:        ${TOTAL_TIME}s"
echo "Throughput:        $(( total_requests / TOTAL_TIME )) req/s"
echo ""

# Compute latency stats (requires latencies array to be non-empty)
if [ ${#latencies[@]} -gt 0 ]; then
  # Sort latencies
  IFS=$'\n' sorted=($(sort -n <<<"${latencies[*]}")); unset IFS

  count=${#sorted[@]}
  sum=0
  for ms in "${sorted[@]}"; do
    sum=$((sum + ms))
  done
  avg=$(( sum / count ))

  min_ms=${sorted[0]}
  max_ms=${sorted[$((count - 1))]}
  p50_idx=$((count * 50 / 100))
  p95_idx=$((count * 95 / 100))
  p99_idx=$((count * 99 / 100))

  p50=${sorted[$p50_idx]}
  p95=${sorted[$p95_idx]}
  p99=${sorted[$p99_idx]}

  echo "Latency Statistics (ms):"
  echo "  Min:    ${min_ms}ms"
  echo "  Avg:    ${avg}ms"
  echo "  P50:    ${p50}ms"
  echo "  P95:    ${p95}ms"
  echo "  P99:    ${p99}ms"
  echo "  Max:    ${max_ms}ms"
else
  echo "No successful requests to compute latency stats."
fi

echo ""
echo "=========================================="
echo " Checking inference metrics..."
echo "=========================================="

# Query inference engine metrics endpoint
metrics=$(curl -s http://localhost:8080/metrics 2>/dev/null || echo "ERROR: Could not reach inference engine metrics endpoint")

if [ "$metrics" != "ERROR: Could not reach inference engine metrics endpoint" ]; then
  total_inferences=$(echo "$metrics" | grep "^sentinel_inferences_total" | awk '{print $2}' | head -1 || echo "N/A")
  echo "Total inferences recorded: $total_inferences"

  # Check Prometheus for drift scores
  drift_scores=$(curl -s "http://localhost:9090/api/v1/query?query=sentinel_drift_score_gauge" 2>/dev/null | jq '.data.result | length' 2>/dev/null || echo "N/A")
  echo "Contracts with drift scores: $drift_scores"
else
  echo "$metrics"
fi

echo ""
echo "=========================================="
echo " Load test complete."
echo "=========================================="
