#!/bin/sh

set -e

SERVICE="${INPUT_SERVICE}"
VERSION="${INPUT_VERSION}"
ENVIRONMENT="${INPUT_ENVIRONMENT}"
GATE_URL="${INPUT_GATE_URL}"

if [ -z "$SERVICE" ] || [ -z "$VERSION" ] || [ -z "$ENVIRONMENT" ] || [ -z "$GATE_URL" ]; then
    echo "Missing required inputs"
    exit 1
fi

RESPONSE=$(curl -s -w "\n%{http_code}" "${GATE_URL}/api/v1/gate/promote?service=${SERVICE}&version=${VERSION}&env=${ENVIRONMENT}")

HTTP_BODY=$(echo "$RESPONSE" | sed '$d')
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)

if [ "$HTTP_CODE" != "200" ]; then
    echo "Gate API returned HTTP ${HTTP_CODE}"
    exit 1
fi

ALLOWED=$(echo "$HTTP_BODY" | jq -r '.allowed')

echo "Gate check result: allowed=${ALLOWED}"

if [ "$ALLOWED" = "false" ]; then
    REASON=$(echo "$HTTP_BODY" | jq -r '.reason')
    echo "Promotion blocked: ${REASON}"
    exit 1
fi

echo "Promotion allowed"
exit 0
