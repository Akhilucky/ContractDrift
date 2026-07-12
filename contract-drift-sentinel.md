# Contract-Drift Sentinel — agents.md

## 1. Project Overview

**Contract-Drift Sentinel** is a live API contract testing mesh that autonomously monitors inter-service communication in a microservices architecture, infers OpenAPI contracts from real traffic, detects breaking schema drift between producers and consumers, and blocks unsafe canary promotions — without requiring engineers to manually author contract tests.

### Problem Statement

In large microservice ecosystems, API contracts between producers and consumers break silently. Existing tools (Pact, Spring Cloud Contract) require developers to write and maintain contract tests, leading to stale coverage and "works in staging, breaks in production" incidents. There is no OSS tool that:

- Autonomously infers contracts from live traffic
- Continuously validates drift in real-time (not just at CI time)
- Correlates breaking changes to a specific deployment event
- Blocks canary promotion pipelines when a contract violation is detected

**Contract-Drift Sentinel solves all four.**

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                        Microservice Mesh                             │
│                                                                      │
│  [Service A] ──HTTP/gRPC──► [Service B] ──HTTP/gRPC──► [Service C]  │
│       │                          │                          │        │
│   sidecar                    sidecar                    sidecar      │
│   agent                      agent                      agent        │
└──────┬───────────────────────────┬──────────────────────────┬───────┘
       │                           │                          │
       └──────────────── Kafka Topic: raw-traffic ───────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │     Traffic Ingestion        │
                    │     Service (Java 21)        │
                    │   - Kafka Consumer Group     │
                    │   - Payload Normalizer       │
                    │   - PII Scrubber             │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │    Schema Inference Engine   │
                    │       (Python sidecar)       │
                    │   - genson / datamodel-code  │
                    │   - OpenAPI 3.1 builder      │
                    │   - Probabilistic type infer │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │     Contract Registry        │
                    │    (PostgreSQL + Redis)      │
                    │   - Versioned contract store │
                    │   - Provider/consumer index  │
                    │   - Baseline snapshots       │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │      Drift Detector          │
                    │       (Java 21 core)         │
                    │   - Structural diff engine   │
                    │   - Semantic diff (nullable, │
                    │     enum expansion, type     │
                    │     widening/narrowing)      │
                    │   - Breaking change classifier│
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │     Promotion Gate           │
                    │    (Webhook + REST API)      │
                    │   - GitHub Actions hook      │
                    │   - Argo CD gate plugin      │
                    │   - GitLab CI integration    │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │    Alerting & Dashboard      │
                    │  - Prometheus metrics        │
                    │  - Grafana dashboards        │
                    │  - Slack / PagerDuty notify  │
                    │  - React UI (contract diff)  │
                    └─────────────────────────────┘
```

---

## 3. Core Components

### 3.1 Sidecar Traffic Agent

- Deployed as a Java 21 virtual-thread-based agent alongside each microservice (or as an Envoy Lua filter for existing meshes)
- Intercepts HTTP request/response pairs at the network layer — no code changes in the target service
- Strips PII (emails, tokens, card numbers) using regex + NER model before emitting
- Serialises to Avro and publishes to `raw-traffic` Kafka topic with envelope: `{ service_id, endpoint, method, request_schema_sample, response_schema_sample, status_code, ts }`
- Sampling strategy: reservoir sampling (1000 samples/min per endpoint) with priority boost for 4xx/5xx responses

### 3.2 Traffic Ingestion Service

- Java 21 Spring Boot app, Kafka consumer group per environment (dev/staging/prod)
- Deduplicate payloads by content hash within a 5-minute sliding window
- Normalise: strip volatile fields (timestamps, UUIDs, trace IDs) before schema inference
- Route to schema inference via gRPC call to Python sidecar
- Persist raw normalised samples to ClickHouse for replay and debugging

### 3.3 Schema Inference Engine (Python Sidecar)

- gRPC server exposing `InferSchema(samples: List[JsonPayload]) → OpenAPISchema`
- Uses `genson` for JSON schema generation, extended with:
  - Probabilistic type inference: if 95% of samples have field `amount` as integer but 5% float, mark as `number` with `x-observed-types: [integer, float]`
  - Optional field detection: field present in < 80% of samples → `required: false`
  - Enum inference: if cardinality < 20 and stable across 100+ samples, suggest enum
- Outputs OpenAPI 3.1 Schema Object (not full spec — just the payload schema component)
- Caches inference results per (service, endpoint, method) key in Redis with TTL 10 min

### 3.4 Contract Registry

**PostgreSQL schema:**
```sql
CREATE TABLE contracts (
  id            UUID PRIMARY KEY,
  provider_id   TEXT NOT NULL,
  consumer_id   TEXT NOT NULL,
  endpoint      TEXT NOT NULL,
  method        TEXT NOT NULL,
  version       INT NOT NULL,
  schema_json   JSONB NOT NULL,
  inferred_at   TIMESTAMPTZ NOT NULL,
  source        TEXT CHECK (source IN ('inferred','manual','imported')),
  status        TEXT CHECK (status IN ('active','superseded','archived'))
);

CREATE TABLE contract_violations (
  id              UUID PRIMARY KEY,
  contract_id     UUID REFERENCES contracts(id),
  detected_at     TIMESTAMPTZ NOT NULL,
  violation_type  TEXT NOT NULL,  -- breaking | additive | informational
  diff_json       JSONB NOT NULL,
  deployment_id   TEXT,           -- linked deployment if correlated
  resolved        BOOLEAN DEFAULT FALSE
);
```

- Redis caches the active contract per (provider, consumer, endpoint) for sub-millisecond lookup during drift check
- Contract versions are immutable; drift detection compares `version N` vs inferred `version N+1`

### 3.5 Drift Detector

The heart of the system. Runs as a Java 21 structured concurrency task for each (provider, consumer) pair every 60 seconds.

**Breaking change classification rules:**

| Change Type | Classification | Action |
|---|---|---|
| Required field removed | BREAKING | Block promotion, alert P1 |
| Field type narrowed (number → integer) | BREAKING | Block promotion, alert P1 |
| Enum values removed | BREAKING | Block promotion, alert P1 |
| Required field added to request | BREAKING | Block promotion, alert P1 |
| Optional field removed | WARNING | Alert P2, allow promotion |
| Field type widened (integer → number) | ADDITIVE | Log only |
| New optional field added | ADDITIVE | Log only |
| Status code set expanded | WARNING | Alert P2 |
| Response schema now nullable | WARNING | Alert P2 |

**Semantic diff algorithm:**
1. Flatten both schemas into a set of JSON paths: `{ "$.user.address.city": { type: string, required: true } }`
2. Compute symmetric difference of path sets
3. For intersecting paths, compare type, required, enum, format, pattern attributes
4. Apply classification rules above
5. Score overall drift as: `breaking_count * 100 + warning_count * 10 + additive_count * 1`
6. Emit `DriftEvent` to `contract-violations` Kafka topic

### 3.6 Promotion Gate

Exposes a REST endpoint consumed by CI/CD pipelines:

```
GET /api/v1/gate/promote?service={id}&version={sha}&env={target}
```

Response:
```json
{
  "allowed": false,
  "reason": "BREAKING_CONTRACT_DRIFT",
  "violations": [
    {
      "consumer": "payment-service",
      "endpoint": "POST /orders",
      "change": "Required field 'currency' removed from response",
      "severity": "BREAKING"
    }
  ],
  "drift_score": 100,
  "recommendation": "Fix contract before promoting or publish a versioned endpoint"
}
```

- GitHub Actions integration: provided as a reusable workflow (`uses: contract-drift-sentinel/gate-action@v1`)
- Argo CD integration: ResourceHealth plugin that marks Application as `Degraded` on violation
- Override mechanism: human sign-off endpoint `POST /api/v1/gate/override` with reason, audited in violation log

### 3.7 Deployment Correlation Engine

- Polls deployment events via pluggable adapters: GitHub Releases API, Argo CD Application events, plain webhook
- When a `DriftEvent` is detected, correlates it with the most recent deployment of the provider service within a 30-minute window
- Links `deployment_id` to `contract_violations` record
- Enables root cause queries: "Which deployment caused the payment-service contract to break?"

---

## 4. API Reference

### Contract Registry APIs

```
GET    /api/v1/contracts                          List all contracts
GET    /api/v1/contracts/{id}                     Get contract by ID
GET    /api/v1/contracts/pair?provider=&consumer= Get contract for a provider-consumer pair
POST   /api/v1/contracts/import                   Import manual OpenAPI spec as contract
DELETE /api/v1/contracts/{id}                     Archive contract
```

### Violation APIs

```
GET  /api/v1/violations                           List violations (filterable by severity, service, resolved)
GET  /api/v1/violations/{id}                      Get violation with full diff
POST /api/v1/violations/{id}/resolve              Mark violation resolved
GET  /api/v1/violations/summary                   Aggregated stats per service pair
```

### Gate APIs

```
GET  /api/v1/gate/promote                         Promotion check
POST /api/v1/gate/override                        Human override with audit
GET  /api/v1/gate/history?service=                Promotion gate history
```

### Admin APIs

```
POST /api/v1/admin/rebaseline?service=&endpoint=  Force re-infer baseline contract
GET  /api/v1/admin/infer/status                   Inference engine health
POST /api/v1/admin/replay?from=&to=               Replay stored traffic through drift detector
```

---

## 5. Data Flow — End to End

```
1. Service A calls Service B  →  Sidecar intercepts, samples, PII-strips, emits to Kafka
2. Ingestion Service consumes →  Normalises, deduplicates, calls Python sidecar for schema inference
3. Inferred schema returned   →  Compare with current active contract in Contract Registry
4. Drift detected?
     YES →  Classify severity, emit DriftEvent, correlate with deployment, write violation
              →  BREAKING: call Promotion Gate to set block flag, fire alert
              →  WARNING: fire alert, allow gate
     NO  →  Update contract version if schema evolved additively
5. CI/CD pipeline calls /gate/promote →  Gate checks block flag, returns allow/deny
6. Dashboard shows live drift score, violation history, contract diff viewer
```

---

## 6. Kafka Topic Design

| Topic | Partitions | Retention | Schema | Consumer |
|---|---|---|---|---|
| `raw-traffic` | 24 | 1 hour | Avro | Ingestion Service |
| `inferred-schemas` | 12 | 24 hours | Avro | Drift Detector |
| `contract-violations` | 6 | 7 days | Avro | Gate, Alerting |
| `deployment-events` | 4 | 30 days | JSON | Correlation Engine |

---

## 7. Technology Stack

| Layer | Technology | Justification |
|---|---|---|
| Core backend | Java 21 + Spring Boot 3.x | Virtual threads for high-concurrency traffic ingestion; your primary stack |
| NLP/inference sidecar | Python 3.11 + gRPC | genson, schema inference; matches your Python sidecar pattern |
| Message bus | Apache Kafka | High-throughput traffic event streaming |
| Contract store | PostgreSQL 16 | JSONB for schema storage, strong ACID for violation records |
| Cache | Redis 7 | Contract lookup cache, inference result cache |
| OLAP / replay store | ClickHouse | Time-series traffic sample storage for replay |
| Observability | Prometheus + Grafana | Drift score metrics, gate pass/fail rates |
| Dashboard frontend | React + Vite | Contract diff viewer, violation browser |
| Deployment gate | GitHub Actions + Argo CD | CI/CD integration |
| Schema format | OpenAPI 3.1 | Industry standard; importable/exportable |

---

## 8. Observability & Metrics

**Prometheus metrics exposed:**

```
sentinel_drift_score_gauge{provider, consumer, endpoint}    — current drift score per pair
sentinel_violations_total{severity, provider, consumer}      — violation counter
sentinel_gate_decisions_total{decision, service}             — promote allow/deny count
sentinel_inference_latency_seconds{endpoint}                 — inference p50/p95/p99
sentinel_traffic_samples_total{service, method}              — sampled request count
sentinel_contract_version{provider, consumer, endpoint}      — current contract version number
```

**Grafana dashboards:**
- Service mesh health heatmap (drift score per pair, colour-coded by severity)
- Violation timeline (rate of breaking/warning/additive events over time)
- Gate decision history (promote allowed vs denied per service)
- Inference throughput and latency

---

## 9. Novelty & Research Angle

This project fills a documented gap:

- **Pact / Spring Cloud Contract**: Consumer-driven, requires manual test authoring. Does not work on live traffic.
- **Optic**: Open-source API diff tool, but operates on spec files — not live traffic. No CI gate integration.
- **Apigee / Kong**: API gateway-level, not a contract mesh. No cross-service consumer-producer modelling.

**Novel contributions:**
1. Autonomous contract inference from live sampled traffic (no developer effort)
2. Semantic breaking-change classification beyond structural JSON diff
3. Deployment correlation: linking a contract break to the exact git SHA / deployment that caused it
4. Promotion gate as a first-class citizen in CI/CD pipelines

**arXiv angle:** cs.SE or cs.DC — "Autonomous Contract Inference and Drift Detection in Microservice Meshes"

---

## 10. 8-Week Build Plan

### Week 1 — Sidecar Agent + Kafka
- Implement HTTP interceptor sidecar (Spring Boot filter or Envoy Lua)
- PII scrubber (regex rules + spaCy NER for names/emails)
- Kafka producer with Avro serialisation
- Docker Compose: Kafka + Zookeeper + schema registry

### Week 2 — Ingestion Service
- Kafka consumer group, deduplication by content hash
- Payload normaliser (strip UUIDs, timestamps)
- gRPC client to Python sidecar
- ClickHouse table for raw traffic samples

### Week 3 — Schema Inference Engine (Python)
- gRPC server with genson-based inference
- Optional field detection, probabilistic type inference
- Enum inference heuristic
- Redis caching layer
- Unit tests: 20+ fixture payloads covering edge cases

### Week 4 — Contract Registry + Drift Detector
- PostgreSQL schema migrations (Flyway)
- Contract Registry service with REST API
- Semantic diff algorithm implementation
- Breaking change classifier with rule table
- DriftEvent producer to Kafka

### Week 5 — Promotion Gate + CI/CD Integration
- Gate REST endpoint with block-flag logic
- GitHub Actions reusable workflow (Docker-based action)
- Override endpoint with audit log
- Integration test: dummy microservice pair → introduce breaking change → assert gate blocks

### Week 6 — Deployment Correlation Engine + Alerting
- GitHub Releases API adapter
- Deployment-to-violation correlation logic
- Slack webhook alerting with rich violation message format
- Prometheus metrics for all core components

### Week 7 — Dashboard + Grafana
- React dashboard: contract list, diff viewer, violation browser
- Grafana dashboards for drift score heatmap and gate history
- Alertmanager rules for P1 violations

### Week 8 — Polish, Load Test, Documentation
- Load test: simulate 50 service pairs, 10k req/min, measure inference latency
- README with quick-start Docker Compose demo
- Architecture diagram (C4 model)
- Record demo video: introduce breaking change → gate blocks canary → alert fires
- arXiv draft outline

---

## 11. Demo Script (Recruiter / Interview)

1. Start Docker Compose: Kafka, PostgreSQL, Redis, ClickHouse, all services
2. Deploy two dummy microservices: `order-service` (producer) and `payment-service` (consumer)
3. Send 1000 requests through the pair → Sentinel infers contract automatically
4. Show the inferred OpenAPI contract in the React dashboard
5. Deploy a new version of `order-service` that removes the `currency` field from the response
6. Send 200 more requests → Drift Detector fires a BREAKING violation
7. Trigger GitHub Actions CI for the new `order-service` version → Gate returns `allowed: false`
8. Show Slack alert with violation detail and the exact diff
9. Use override endpoint with reason → Gate allows → override appears in audit log

**The pitch:** "It's a self-writing contract test suite that lives in production, not in your CI config."
