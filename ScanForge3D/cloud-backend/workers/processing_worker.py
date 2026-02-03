"""Celery worker tasks for heavy mesh processing jobs."""

import os
import logging
from workers.celery_app import celery_app

logger = logging.getLogger(__name__)

OUTPUT_DIR = "/tmp/scanforge/outputs"
os.makedirs(OUTPUT_DIR, exist_ok=True)


@celery_app.task(bind=True, name="process_mesh_to_step")
def process_mesh_to_step_task(self, input_path: str, job_id: str):
    """
    Celery task for mesh-to-STEP conversion.
    Use this for production deployments instead of FastAPI BackgroundTasks.
    """
    from services.step_export_service import StepExportService

    try:
        self.update_state(state="PROCESSING", meta={"progress": 0.1})

        step_service = StepExportService()
        output_path = os.path.join(OUTPUT_DIR, f"{job_id}.step")

        self.update_state(state="PROCESSING", meta={"progress": 0.3})

        success = step_service.export_to_step(
            input_path=input_path,
            output_path=output_path,
        )

        if success:
            return {"status": "completed", "result_path": output_path}
        else:
            return {"status": "failed", "error": "STEP export failed"}

    except Exception as e:
        logger.error(f"Celery task {job_id} failed: {e}")
        return {"status": "failed", "error": str(e)}


@celery_app.task(bind=True, name="process_photogrammetry")
def process_photogrammetry_task(self, image_dir: str, job_id: str):
    """
    Celery task for photogrammetry pipeline.
    Use this for production deployments with COLMAP.
    """
    from services.photogrammetry_service import PhotogrammetryService
    from services.step_export_service import StepExportService

    try:
        photo_service = PhotogrammetryService()
        workspace = os.path.join("/tmp/scanforge/uploads", f"{job_id}_colmap")

        def on_progress(progress: float, step: str):
            self.update_state(
                state="PROCESSING",
                meta={"progress": progress * 0.85, "step": step},
            )

        mesh_path = photo_service.reconstruct(
            image_dir=image_dir,
            workspace_dir=workspace,
            progress_callback=on_progress,
        )

        if not mesh_path:
            return {"status": "failed", "error": "Photogrammetry failed"}

        self.update_state(state="PROCESSING", meta={"progress": 0.9})

        step_service = StepExportService()
        output_path = os.path.join(OUTPUT_DIR, f"{job_id}.step")

        success = step_service.export_to_step(
            input_path=mesh_path,
            output_path=output_path,
        )

        if success:
            return {"status": "completed", "result_path": output_path}
        else:
            return {"status": "failed", "error": "STEP export failed"}

    except Exception as e:
        logger.error(f"Photogrammetry task {job_id} failed: {e}")
        return {"status": "failed", "error": str(e)}
