#!/bin/bash
set -euo pipefail

# 1. Start Docker Compose
docker compose up -d

# 2. Wait for services to be healthy
echo "Waiting for services..."
sleep 30

# 3. Deploy dummy microservices (order-service producer, payment-service consumer)
#    - curl to create contract via import
echo "Deploying dummy microservices..."
curl -s -X POST http://localhost:8083/api/v1/contracts/import \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "order-service",
    "consumer": "payment-service",
    "endpoint": "POST /api/order",
    "schema": {
      "type": "object",
      "properties": {
        "order_id": {"type": "string"},
        "amount": {"type": "number"},
        "currency": {"type": "string"},
        "items": {
          "type": "array",
          "items": {"type": "string"}
        },
        "user": {
          "type": "object",
          "properties": {
            "name": {"type": "string"},
            "email": {"type": "string"}
          },
          "required": ["name", "email"]
        }
      },
      "required": ["order_id", "amount", "currency", "items", "user"]
    }
  }' > /dev/null

echo "Contract created."

# 4. Send 1000 requests through the pair
echo "Simulating 1000 requests..."
for i in $(seq 1 1000); do
  curl -s -X POST http://localhost:8090/api/order \
    -H "Content-Type: application/json" \
    -d '{"order_id":"ord_'$i'","amount":100,"currency":"USD","items":["item1","item2"],"user":{"name":"John","email":"john@test.com"}}' > /dev/null &
done
wait
echo "1000 requests sent."

# 5. Show inferred contract
echo "=== Inferred Contract ==="
curl -s http://localhost:8083/api/v1/contracts | jq .

# 6. Simulate breaking change (deploy new version that removes currency field)
echo "Simulating breaking change..."
for i in $(seq 1 200); do
  curl -s -X POST http://localhost:8090/api/order \
    -H "Content-Type: application/json" \
    -d '{"order_id":"ord_'$i'","amount":100,"items":["item1"]}' > /dev/null &
done
wait
sleep 10

# 7. Check violations
echo "=== Violations ==="
curl -s http://localhost:8083/api/v1/violations | jq .

# 8. Check promotion gate
echo "=== Gate Check ==="
curl -s "http://localhost:8084/api/v1/gate/promote?service=order-service&version=abc123&env=staging" | jq .

echo "Demo complete."
