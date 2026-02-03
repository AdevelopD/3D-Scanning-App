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
    """Background task: run COLMAP photogrammetry then convert to STEP."""
    from routers.scan_upload import get_jobs
    from services.photogrammetry_service import PhotogrammetryService
    from services.step_export_service import StepExportService

    jobs = get_jobs()
    job = jobs.get(job_id)
    if not job:
        return

    try:
        job.status = JobStatus.PROCESSING

        photo_service = PhotogrammetryService()
        workspace = os.path.join(UPLOAD_DIR, f"{job_id}_colmap")

        def on_progress(progress: float, step: str):
            job.progress = progress * 0.85  # Reserve last 15% for STEP export

        mesh_path = photo_service.reconstruct(
            image_dir=job.input_path,
            workspace_dir=workspace,
            progress_callback=on_progress,
        )

        if not mesh_path:
            job.status = JobStatus.FAILED
            job.error = "Photogrammetry reconstruction failed"
            return

        # Convert reconstructed mesh to STEP
        job.progress = 0.90
        step_service = StepExportService()
        output_path = os.path.join(OUTPUT_DIR, f"{job_id}.step")

        success = step_service.export_to_step(
            input_path=mesh_path,
            output_path=output_path,
        )

        job.progress = 1.0

        if success:
            job.status = JobStatus.COMPLETED
            job.result_path = output_path
        else:
            job.status = JobStatus.FAILED
            job.error = "STEP export of photogrammetry result failed"

    except Exception as e:
        logger.error(f"Photogrammetry job {job_id} failed: {e}")
        job.status = JobStatus.FAILED
        job.error = str(e)
