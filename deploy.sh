#!/usr/bin/env bash
# Pull the latest code and rebuild/restart the production stack.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env.production ]; then
  echo "Missing .env.production (copy .env.production.example and fill it in)." >&2
  exit 1
fi

git pull --ff-only
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build
docker image prune -f
docker compose -f docker-compose.prod.yml --env-file .env.production ps
