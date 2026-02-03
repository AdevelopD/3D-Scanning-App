"""
ScanForge3D Cloud Backend

Processes uploaded meshes to STEP files via Open CASCADE.

Architecture:
- FastAPI for REST API
- Celery + Redis for async job queue (production)
- Open CASCADE (PythonOCC) for STEP export
- COLMAP for optional photogrammetry
"""

import logging
from fastapi import FastAPI
from routers import upload_router, export_router

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)

app = FastAPI(
    title="ScanForge3D Cloud Processing",
    version="1.0.0",
    description="REST API for converting 3D scan meshes to STEP/IGES CAD formats.",
)

app.include_router(upload_router)
app.include_router(export_router)


@app.get("/health")
async def health_check():
    return {"status": "ok", "version": "1.0.0"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
