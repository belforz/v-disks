# Builder stage: build the Spring Boot fat jar using Maven + Eclipse Temurin JDK21
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

# copy maven wrapper and pom first for dependency caching
COPY mvnw pom.xml ./
COPY .mvn .mvn/
RUN chmod +x ./mvnw || true

# download dependencies
RUN ./mvnw -B -f pom.xml -DskipTests dependency:go-offline

# copy source and build
COPY src ./src
RUN ./mvnw -B -f pom.xml -DskipTests package

# Runtime stage: small JRE image
FROM eclipse-temurin:21-jre

# non-root user
RUN addgroup --system app && adduser --system --ingroup app app

WORKDIR /app

# copy the built jar from the builder stage (matches the artifact produced in target)
COPY --from=build /workspace/target/*.jar ./app.jar
RUN chown app:app /app/app.jar || true


USER app

EXPOSE 8080

# sensible default JVM options

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh","-c","java $JAVA_TOOL_OPTIONS -jar /app/app.jar --server.port=${PORT:-8080}"]
