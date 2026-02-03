"""Router for job status and result download endpoints."""

import os
from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse

from models.job import JobStatusResponse

router = APIRouter(prefix="/api/v1", tags=["export"])


@router.get("/job/{job_id}", response_model=JobStatusResponse)
async def get_job_status(job_id: str):
    """Query processing job status."""
    from routers.scan_upload import get_jobs
    jobs = get_jobs()

    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job not found")

    job = jobs[job_id]
    result_url = f"/api/v1/download/{job_id}" if job.result_path else None

    return JobStatusResponse(
        job_id=job_id,
        status=job.status.value,
        progress=job.progress,
        result_url=result_url,
        error=job.error,
    )


@router.get("/download/{job_id}")
async def download_result(job_id: str):
    """Download completed STEP file."""
    from routers.scan_upload import get_jobs
    jobs = get_jobs()

    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job not found")

    job = jobs[job_id]
    if job.status.value != "completed" or not job.result_path:
        raise HTTPException(status_code=400, detail="Result not ready")

    if not os.path.exists(job.result_path):
        raise HTTPException(status_code=404, detail="Result file missing")

    return FileResponse(
        job.result_path,
        media_type="application/step",
        filename=os.path.basename(job.result_path),
    )
