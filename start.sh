#!/usr/bin/env bash

# ==============================================================================
# WhatsApp Family Document Vault - Unified Dual Dev Runner
# Backend:  http://localhost:8790 (Spring Boot + REST APIs + Webhooks)
# Frontend: http://localhost:8791 (React 18 + Vite Hot Reload)
# ==============================================================================

set -e

# Trap Ctrl+C (SIGINT/SIGTERM) to kill both processes cleanly
cleanup() {
    echo ""
    echo "🛑 Shutting down both servers..."
    kill $(jobs -p) 2>/dev/null || true
    exit 0
}
trap cleanup SIGINT SIGTERM EXIT

echo "=========================================================="
echo "🚀 Starting WhatsApp Family Document Vault"
echo "=========================================================="
echo "📦 Backend:  http://localhost:8790"
echo "⚛️  Frontend: http://localhost:8791"
echo "⚡ Swagger:  http://localhost:8790/swagger-ui.html"
echo "=========================================================="

# 1. Start Spring Boot backend in background
mvn spring-boot:run &

# 2. Wait 2 seconds and start Vite frontend in background
sleep 2
npm --prefix frontend run dev &

# Wait for all background jobs
wait
