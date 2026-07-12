# Contract-Drift Sentinel — Specification

## Overview

**Contract-Drift Sentinel** is a real-time, inference-based contract testing and drift detection platform for microservices. It observes live HTTP traffic between service pairs, infers the implicit schema contract from actual request/response shapes, and flags any deviation as a potential breaking change — without requiring pre-written tests or maintained fixtures.

The system acts as a **promotion gate**: it decides, based on observed drift severity and configurable policy, whether a new service version is safe to promote to a given environment.

---

## Architecture

```
┌─────────────────────┐       ┌──────────────────────────────────────┐
│   Service Pair      │       │         Contract-Drift Sentinel       │
│                     │       │                                      │
│  ┌──────┐   ┌──────┐│       │  ┌──────────┐    ┌───────────────┐  │
│  │Producer├───►Consumer││  HTTP │  │ Inference │    │  Contract     │  │
│  └──────┘   └──────┘│  ──┼──►│  Engine    │───►│  Repository   │  │
│         │            │       │  │ (Sketch)   │    │  (Postgres)   │  │
│         │            │       │  └──────────┘    └──────┬────────┘  │
│         │            │       │         │                │           │
│         │            │       │         ▼                ▼           │
│         │            │       │  ┌──────────┐    ┌───────────────┐  │
│         │            │       │  │ Drift    │    │  Violation     │  │
│         │            │       │  │ Detector │───►│  Store         │  │
│         │            │       │  └──────────┘    └──────┬────────┘  │
│         │            │       │         │                │           │
│         └────────────┘       │         ▼                ▼           │
│                              │  ┌──────────┐    ┌───────────────┐  │
│                              │  │Promotion │    │   Metrics     │  │
│                              │  │  Gate    │    │  (Prometheus) │  │
│                              │  └──────────┘    └───────────────┘  │
│                              └──────────────────────────────────────┘
```

---

## Core Services

### 1. Inference Engine (Go, :8080)
- Intercepts or receives mirrored HTTP traffic for each producer-consumer pair
- For each request/response, extracts a **schema sketch** (type, structure, nullable fields, value ranges)
- Produces an aggregated **windowed sketch** per time window
- Output format: JSON with field names, types, cardinality estimates, null ratios

### 2. Contract Repository (Python/FastAPI, :8083)
- Stores "golden" inferred contracts and current window sketches
- Supports CRUD operations and historical queries
- Backed by PostgreSQL

### 3. Drift Detector (Go, :8081)
- Compares two sketches (golden vs current)
- Computes a **drift score** per field: 0 (identical), 1–99 (non-breaking change), 100+ (breaking)
- Emits a **Violation** record when drift severity exceeds configurable thresholds

### 4. Violation Store (Python/FastAPI, :8082)
- Persists violation records with metadata: provider, consumer, endpoint, severity, field diff
- Exposes query API filtering by time, severity, service

### 5. Promotion Gate (Python/FastAPI, :8084)
- Consults latest drift scores for a given service version
- Queries configurable **policy rules** (e.g., "deny if any BREAKING violations in last 24h")
- Returns `allow` / `deny` / `warn`
- Supports admin overrides

### 6. Metrics & Alerting (Prometheus + Grafana)
- Each service exposes a `/metrics` endpoint with custom metrics
- Prometheus scrapes all services
- Grafana dashboards for drift overview, violation timeline, gate decisions, inference latency

---

## Data Model

### Contract
```json
{
  "id": "uuid",
  "provider": "order-service",
  "consumer": "payment-service",
  "endpoint": "POST /api/order",
  "golden_sketch": { ... },
  "current_sketch": { ... },
  "drift_score": 42,
  "updated_at": "2025-01-01T00:00:00Z"
}
```

### Violation
```json
{
  "id": "uuid",
  "contract_id": "uuid",
  "severity": "BREAKING | WARNING | ADDITIVE",
  "field": "currency",
  "change_type": "field_removed | type_changed | nullability_changed",
  "old_value": "...",
  "new_value": "...",
  "drift_score": 100,
  "detected_at": "2025-01-01T00:00:00Z"
}
```

### Gate Decision
```json
{
  "service": "order-service",
  "version": "abc123",
  "environment": "staging",
  "decision": "deny",
  "reason": "BREAKING violation detected on field 'currency'",
  "overridden": false
}
```

---

## Metrics

| Metric Name                              | Type      | Labels                                            |
|-------------------------------------------|-----------|---------------------------------------------------|
| sentinel_inferences_total                | Counter   | provider, consumer, endpoint                      |
| sentinel_inference_latency_seconds       | Histogram | provider, consumer, endpoint                      |
| sentinel_drift_score_gauge               | Gauge     | provider, consumer, endpoint                      |
| sentinel_violations_total                | Counter   | severity, provider, consumer                      |
| sentinel_gate_decisions_total            | Counter   | decision, service, environment                    |
| sentinel_gate_promotion_latency_seconds  | Histogram | service, environment                              |

---

## API Endpoints

### Contract Repository (:8083)
- `POST /api/v1/contracts/import` — import a contract
- `GET /api/v1/contracts` — list all contracts
- `GET /api/v1/contracts/{id}` — get a specific contract
- `PUT /api/v1/contracts/{id}` — update a contract

### Violation Store (:8082)
- `GET /api/v1/violations` — list violations (query params: severity, provider, consumer, since, until, limit, offset)
- `GET /api/v1/violations/{id}` — get a specific violation

### Promotion Gate (:8084)
- `GET /api/v1/gate/promote?service={service}&version={version}&env={env}` — check promotion eligibility
- `POST /api/v1/gate/override` — admin override a gate decision

---

## Policy Configuration

Policies are defined in a YAML file mounted into the Promotion Gate container:

```yaml
policies:
  - name: "block-breaking"
    description: "Deny promotion if any BREAKING violation exists"
    conditions:
      - severity: "BREAKING"
        min_count: 1
        window: "24h"
    action: "deny"
  - name: "warn-on-warnings"
    description: "Warn if WARNING violations exceed threshold"
    conditions:
      - severity: "WARNING"
        min_count: 5
        window: "1h"
    action: "warn"
```

---

## Drift Score Algorithm

For each field in the sketch:

1. **Field presence**: missing field = +100 (BREAKING), new field = +10 (ADDITIVE)
2. **Type change**: incompatible type change = +100 (BREAKING)
3. **Nullability**: field became always null or never null = +30 (WARNING)
4. **Cardinality**: significant cardinality change = +10 (WARNING)
5. **Value range**: numeric range shrinkage = +20 (WARNING)

Total drift score = sum of all field drift scores. Thresholds:
- 0: no drift
- 1–49: ADDITIVE / non-breaking
- 50–99: WARNING (may indicate subtle breakage)
- 100+: BREAKING (likely customer-facing breakage)
