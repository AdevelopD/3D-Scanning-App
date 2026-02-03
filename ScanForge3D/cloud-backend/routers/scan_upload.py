"""Router for mesh and image upload endpoints."""

import os
import uuid
import shutil
from fastapi import APIRouter, UploadFile, File, BackgroundTasks

from models.job import Job, JobStatusResponse

router = APIRouter(prefix="/api/v1", tags=["upload"])

# Shared job storage (in production: Redis/database)
jobs: dict[str, Job] = {}

UPLOAD_DIR = "/tmp/scanforge/uploads"
os.makedirs(UPLOAD_DIR, exist_ok=True)


def get_jobs() -> dict[str, Job]:
    return jobs


@router.post("/upload-mesh", response_model=JobStatusResponse)
async def upload_mesh(
    file: UploadFile = File(...),
    background_tasks: BackgroundTasks = None
):
    """Upload a mesh file (STL, OBJ, PLY) for STEP conversion."""
    job_id = str(uuid.uuid4())
    upload_path = os.path.join(UPLOAD_DIR, f"{job_id}_{file.filename}")

    with open(upload_path, "wb") as f:
        shutil.copyfileobj(file.file, f)

    job = Job(job_id=job_id, input_path=upload_path)
    jobs[job_id] = job

    # Import processing task and schedule
    from routers.processing import process_mesh_job
    background_tasks.add_task(process_mesh_job, job_id)

    return JobStatusResponse(job_id=job_id, status="pending", progress=0.0)


@router.post("/upload-images", response_model=JobStatusResponse)
async def upload_images(
    files: list[UploadFile] = File(...),
    background_tasks: BackgroundTasks = None
):
    """Upload images for photogrammetry reconstruction."""
    job_id = str(uuid.uuid4())
    image_dir = os.path.join(UPLOAD_DIR, job_id)
    os.makedirs(image_dir, exist_ok=True)

    for img_file in files:
        img_path = os.path.join(image_dir, img_file.filename)
        with open(img_path, "wb") as f:
            shutil.copyfileobj(img_file.file, f)

    job = Job(job_id=job_id, input_path=image_dir)
    jobs[job_id] = job

    from routers.processing import process_photogrammetry_job
    background_tasks.add_task(process_photogrammetry_job, job_id)

    return JobStatusResponse(job_id=job_id, status="pending", progress=0.0)
