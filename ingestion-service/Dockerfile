FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -B -DskipTests

FROM eclipse-temurin:21-alpine AS runtime
WORKDIR /app
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.6.0/opentelemetry-javaagent.jar /opentelemetry-javaagent.jar
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /build/target/*.jar app.jar
USER appuser
EXPOSE 8085
ENTRYPOINT ["java", "-javaagent:/opentelemetry-javaagent.jar", "--enable-preview", "-Djava.util.concurrent.ForkJoinPool.common.parallelism=4", "-jar", "app.jar"]
