#!/usr/bin/env bash
# Start PostgreSQL (Docker), Spring Boot API, and Next.js dev server.
# Press Ctrl+C to stop the API and frontend; Postgres keeps running.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BACKEND_PID=""

cleanup() {
  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
    echo ""
    echo "Stopping Spring Boot (PID ${BACKEND_PID})..."
    kill "${BACKEND_PID}" 2>/dev/null || true
    wait "${BACKEND_PID}" 2>/dev/null || true
  fi
}

trap cleanup INT TERM EXIT

if ! command -v docker &>/dev/null; then
  echo "Docker is required. Install Docker Desktop and try again."
  exit 1
fi

echo "==> Starting PostgreSQL (docker compose)..."
docker compose up -d

echo "==> Waiting for Postgres to accept connections..."
attempts=0
until docker compose exec -T postgres pg_isready -U jobtracker -d jobtracker >/dev/null 2>&1; do
  attempts=$((attempts + 1))
  if [[ "${attempts}" -gt 60 ]]; then
    echo "Postgres did not become ready in time."
    exit 1
  fi
  sleep 1
done
echo "    Database is ready."

if ! command -v java &>/dev/null; then
  echo "Java is required (JDK 21 recommended)."
  exit 1
fi

if ! command -v npm &>/dev/null; then
  echo "npm is required (install Node.js LTS)."
  exit 1
fi

if [[ -x "$ROOT_DIR/backend/mvnw" ]]; then
  :
elif command -v mvn &>/dev/null; then
  :
else
  echo "Neither ./backend/mvnw nor the 'mvn' command is available."
  echo "Ensure backend/mvnw exists and is executable (chmod +x backend/mvnw), or install Maven."
  exit 1
fi

if [[ ! -f "$ROOT_DIR/frontend/.env.local" ]]; then
  if [[ -f "$ROOT_DIR/frontend/.env.example" ]]; then
    echo "==> Creating frontend/.env.local from .env.example"
    cp "$ROOT_DIR/frontend/.env.example" "$ROOT_DIR/frontend/.env.local"
  fi
fi

api_ok=0
# If something already answers on 8080, do not start a second Spring Boot (it will crash: port in use).
if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
  echo "==> API already responding on http://localhost:8080 — skipping Spring Boot start."
  echo "    (Stop the old process first if you want a fresh backend: lsof -ti:8080 | xargs kill)"
  api_ok=1
else
  echo "==> Starting Spring Boot API (background)..."
  (
    cd "$ROOT_DIR/backend"
    if [[ -x ./mvnw ]]; then
      ./mvnw spring-boot:run
    elif command -v mvn &>/dev/null; then
      mvn spring-boot:run
    else
      echo "Install Maven or add ./backend/mvnw (https://maven.apache.org/)" >&2
      exit 1
    fi
  ) &
  BACKEND_PID=$!

  echo "    Waiting for API (http://localhost:8080) — first run can take 1–2 minutes..."
  for ((i = 1; i <= 120; i++)); do
    if ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
      echo ""
      echo "ERROR: Spring Boot exited before the API came up (often: port 8080 already in use)."
      echo "       Free the port:  lsof -ti:8080 | xargs kill"
      echo "       Or see logs:    cd backend && ./mvnw spring-boot:run"
      exit 1
    fi
    if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
      api_ok=1
      break
    fi
    sleep 1
  done

  if [[ "${api_ok}" -ne 1 ]]; then
    echo ""
    echo "WARN: API did not respond on http://localhost:8080/actuator/health within 120s."
    echo "      If another app uses 8080, stop it or set SERVER_PORT, e.g.:"
    echo "        cd backend && SERVER_PORT=8081 ./mvnw spring-boot:run"
    echo "      Continuing with Next.js — point NEXT_PUBLIC_API_BASE_URL at the API if not :8080."
  else
    echo "    API is up."
  fi
fi

echo "    Swagger: http://localhost:8080/swagger-ui.html"

# Next.js defaults to 3000; use PORT if something else is listening (common confusion).
export PORT=3000
if command -v lsof &>/dev/null && lsof -ti:3000 >/dev/null 2>&1; then
  export PORT=3001
  echo "==> Port 3000 is already in use — starting Next.js on PORT=${PORT}"
  echo "    Open: http://localhost:${PORT}  (not 3000)"
else
  echo "==> Starting Next.js (foreground) at http://localhost:${PORT}"
fi

cd "$ROOT_DIR/frontend"
if [[ ! -d node_modules ]]; then
  echo "    Running npm install..."
  npm install
fi
npm run dev
