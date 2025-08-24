#!/usr/bin/env pwsh
# Run the application using the `dev` profile so `application-dev.yml` is loaded.
# Usage: Open PowerShell in project root and run: .\scripts\run-dev.ps1

Write-Host "Starting application with Spring profile 'dev'..."

# set environment variable for the current process
$env:SPRING_PROFILES_ACTIVE = 'dev'

Write-Host "SPRING_PROFILES_ACTIVE=$($env:SPRING_PROFILES_ACTIVE)"

# use mvnw if present
if (Test-Path -Path .\mvnw.cmd) {
    .\mvnw.cmd spring-boot:run
} else {
    Write-Host "mvnw not found. Run with: mvn -Dspring-boot.run.profiles=dev spring-boot:run" -ForegroundColor Yellow
}
