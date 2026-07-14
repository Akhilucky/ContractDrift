FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.6.0/opentelemetry-javaagent.jar /opentelemetry-javaagent.jar
COPY --from=builder /app/target/contract-registry-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/opentelemetry-javaagent.jar", "-jar", "app.jar"]
