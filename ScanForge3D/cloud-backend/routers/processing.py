"""Router and background tasks for mesh processing."""

import os
import logging

from models.job import JobStatus

logger = logging.getLogger(__name__)

OUTPUT_DIR = "/tmp/scanforge/outputs"
UPLOAD_DIR = "/tmp/scanforge/uploads"
os.makedirs(OUTPUT_DIR, exist_ok=True)


async def process_mesh_job(job_id: str):
    """Background task: convert uploaded mesh to STEP."""
    from routers.scan_upload import get_jobs
    from services.step_export_service import StepExportService

    jobs = get_jobs()
    job = jobs.get(job_id)
    if not job:
        return

    try:
        job.status = JobStatus.PROCESSING
        job.progress = 0.1

        step_service = StepExportService()

        job.progress = 0.3
        output_path = os.path.join(OUTPUT_DIR, f"{job_id}.step")

        success = step_service.export_to_step(
            input_path=job.input_path,
            output_path=output_path,
            product_name="ScanForge3D Scan",
        )

        job.progress = 1.0

        if success:
            job.status = JobStatus.COMPLETED
            job.result_path = output_path
            logger.info(f"Job {job_id} completed: {output_path}")
        else:
            job.status = JobStatus.FAILED
            job.error = "STEP export failed"

    except Exception as e:
        logger.error(f"Job {job_id} failed: {e}")
        job.status = JobStatus.FAILED
        job.error = str(e)


async def process_photogrammetry_job(job_id: str):
    """Background task: run COLMAP photogrammetry, return PLY mesh."""
    from routers.scan_upload import get_jobs
    from services.photogrammetry_service import PhotogrammetryService

    jobs = get_jobs()
    job = jobs.get(job_id)
    if not job:
        return

    try:
        job.status = JobStatus.PROCESSING

        photo_service = PhotogrammetryService(use_gpu=False)
        workspace = os.path.join(UPLOAD_DIR, f"{job_id}_colmap")

        def on_progress(progress: float, step: str):
            job.progress = progress
            logger.info(f"Job {job_id}: [{progress:.0%}] {step}")

        mesh_path = photo_service.reconstruct(
            image_dir=job.input_path,
            workspace_dir=workspace,
            progress_callback=on_progress,
        )

        if not mesh_path:
            job.status = JobStatus.FAILED
            job.error = "Reconstruction failed - check server logs for details"
            logger.error(f"Job {job_id}: reconstruct() returned None")
            return

        if not os.path.exists(mesh_path):
            job.status = JobStatus.FAILED
            job.error = f"Output file not found: {mesh_path}"
            return

        # Deliver the PLY mesh directly (no STEP conversion needed for app)
        job.progress = 1.0
        job.status = JobStatus.COMPLETED
        job.result_path = mesh_path
        file_size = os.path.getsize(mesh_path)
        logger.info(
            f"Photogrammetry job {job_id} completed: {mesh_path} "
            f"({file_size / 1024:.1f} KB)"
        )

    except Exception as e:
        logger.error(f"Photogrammetry job {job_id} failed: {e}", exc_info=True)
        job.status = JobStatus.FAILED
        job.error = str(e)
