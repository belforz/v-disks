#!/usr/bin/env bash
# Run the application using the `dev` profile so `application-dev.yml` is loaded.
echo "Starting application with Spring profile 'dev'..."
export SPRING_PROFILES_ACTIVE=dev
if [ -f ./mvnw ]; then
  ./mvnw spring-boot:run
else
  echo "mvnw not found. Run with: mvn -Dspring.profiles.active=dev spring-boot:run"
fi
