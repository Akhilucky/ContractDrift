# Contract-Drift Sentinel — Envoy WASM Filter

A WebAssembly HTTP filter for Envoy that replaces the Java sidecar agent for
traffic interception. It captures request/response metadata and bodies, applies
PII scrubbing, performs reservoir sampling, and writes traffic samples as
newline-delimited JSON to a shared volume. A companion Go tailer agent tails
the file and publishes samples to Kafka.

## Components

| Component | Description |
|-----------|-------------|
| `filter.go` | proxy-wasm-go-sdk HTTP filter compiled to `.wasm` |
| `tailer/main.go` | Go agent that tails NDJSON and publishes to Kafka |
| `envoy.yaml` | Example Envoy configuration with the WASM filter |

## Build

```bash
# Build WASM filter
tinygo build -o filter.wasm -scheduler wasi -target wasi ./filter.go

# Build tailer
cd tailer && go build -o tailer ./main.go
```

## How It Works

1. Envoy loads the WASM filter via `envoy.yaml`.
2. The filter intercepts every HTTP request/response.
3. On stream completion, it serializes a `TrafficSample` as JSON and appends
   it to `/tmp/traffic_samples.ndjson` (shared volume).
4. The tailer agent watches the file, parses lines, and publishes to Kafka
   topic `raw-traffic`.
5. PII fields (email, SSN, credit card, auth headers) are scrubbed before
   writing.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BROKERS` | `kafka:9092` | Comma-separated Kafka broker list |
| `TRAFFIC_TOPIC` | `raw-traffic` | Kafka topic for traffic samples |
| `TRAFFIC_FILE_PATH` | `/tmp/traffic_samples.ndjson` | Path to the NDJSON file |
| `SAMPLE_RATE` | `1000` | Reservoir samples per minute per endpoint |
| `ENVIRONMENT` | `development` | Environment tag for samples |
