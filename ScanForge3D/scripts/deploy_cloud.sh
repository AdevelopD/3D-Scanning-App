#!/bin/bash
# scripts/deploy_cloud.sh
# Deploys the ScanForge3D cloud backend via Docker Compose.
# Prerequisites: Docker and Docker Compose installed.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKEND_DIR="$PROJECT_DIR/cloud-backend"

echo "=== ScanForge3D Cloud Backend Deployment ==="

if ! command -v docker &> /dev/null; then
    echo "ERROR: Docker is not installed."
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "ERROR: Docker Compose is not installed."
    exit 1
fi

cd "$BACKEND_DIR"

case "${1:-up}" in
    up)
        echo "Starting cloud backend..."
        docker compose up -d --build
        echo ""
        echo "Services started. API available at http://localhost:8000"
        echo "Health check: http://localhost:8000/health"
        echo ""
        docker compose ps
        ;;
    down)
        echo "Stopping cloud backend..."
        docker compose down
        echo "Services stopped."
        ;;
    logs)
        docker compose logs -f "${2:-}"
        ;;
    restart)
        echo "Restarting cloud backend..."
        docker compose down
        docker compose up -d --build
        echo "Services restarted."
        docker compose ps
        ;;
    status)
        docker compose ps
        ;;
    *)
        echo "Usage: $0 {up|down|logs|restart|status}"
        echo "  up       - Build and start services (default)"
        echo "  down     - Stop services"
        echo "  logs     - Follow service logs (optional: service name)"
        echo "  restart  - Rebuild and restart services"
        echo "  status   - Show service status"
        exit 1
        ;;
esac
