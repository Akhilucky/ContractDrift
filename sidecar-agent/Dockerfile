FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S sidecar && adduser -S sidecar -G sidecar
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
USER sidecar
EXPOSE 8090
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:ZCollectionInterval=30", "-XX:ZAllocationSpikeTolerance=5", "-jar", "app.jar"]
