#!/usr/bin/env bash
# Build and run Docker image using .env for environment variables
IMAGE_NAME=${1:-vdisk/app:latest}
PORT=${2:-8080}

if [ ! -f .env ]; then
  echo "Warning: .env not found. Copy .env.example -> .env and fill values."
fi

echo "Building Docker image $IMAGE_NAME..."
docker build -t $IMAGE_NAME -f Dockerfile.yaml .

echo "Running container from $IMAGE_NAME (publishing port $PORT) using .env as --env-file..."
docker run --rm -p ${PORT}:8080 --env-file .env $IMAGE_NAME
