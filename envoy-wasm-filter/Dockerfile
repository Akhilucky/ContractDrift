FROM golang:1.22-alpine AS tailer-build
WORKDIR /app
COPY tailer/ ./tailer/
RUN cd tailer && go mod download && CGO_ENABLED=0 go build -o /tailer ./main.go

FROM envoyproxy/envoy:v1.29-latest AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=tailer-build /tailer /usr/local/bin/traffic-tailer
COPY envoy.yaml /etc/envoy/envoy.yaml
RUN touch /tmp/traffic_samples.ndjson

ENTRYPOINT ["sh", "-c", "traffic-tailer & envoy -c /etc/envoy/envoy.yaml"]
