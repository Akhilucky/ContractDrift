FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY protos/ protos/
RUN python -m grpc_tools.protoc \
    -I protos \
    --python_out=. \
    --grpc_python_out=. \
    protos/inference.proto

COPY *.py .

ENV GRPC_PORT=50051
ENV REDIS_HOST=redis
ENV REDIS_PORT=6379
ENV REDIS_TTL_SECONDS=600

EXPOSE 50051

CMD ["python", "server.py"]
