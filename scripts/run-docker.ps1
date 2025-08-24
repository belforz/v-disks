#!/usr/bin/env pwsh
# Build and run Docker image using .env for environment variables
param(
    [string]$ImageName = 'vdisk/app:latest',
    [int]$Port = 8080
)

if (-Not (Test-Path -Path .\.env)) {
    Write-Host "Warning: .env not found in project root. Create one from .env.example and fill values." -ForegroundColor Yellow
}

Write-Host "Building Docker image $ImageName..."
docker build -t $ImageName -f Dockerfile.yaml .

Write-Host "Running container from $ImageName (publishing port $Port) using .env as --env-file..."
docker run --rm -p $Port`:8080 --env-file .\.env $ImageName

Write-Host "Container exited."
