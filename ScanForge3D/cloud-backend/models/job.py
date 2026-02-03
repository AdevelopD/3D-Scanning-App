"""Job data models for tracking processing tasks."""

from pydantic import BaseModel
from typing import Optional
from enum import Enum


class JobStatus(str, Enum):
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


class ExportFormat(str, Enum):
    STEP = "step"
    IGES = "iges"


class Job:
    """Internal job tracking object."""

    def __init__(self, job_id: str, input_path: str):
        self.job_id = job_id
        self.status = JobStatus.PENDING
        self.progress = 0.0
        self.input_path = input_path
        self.result_path: Optional[str] = None
        self.error: Optional[str] = None
        self.export_format = ExportFormat.STEP

    def to_dict(self) -> dict:
        return {
            "status": self.status.value,
            "progress": self.progress,
            "input_path": self.input_path,
            "result_path": self.result_path,
            "error": self.error,
        }


class JobStatusResponse(BaseModel):
    """API response model for job status."""
    job_id: str
    status: str
    progress: float
    result_url: Optional[str] = None
    error: Optional[str] = None


class ExportRequest(BaseModel):
    """Request parameters for mesh export."""
    format: str = "step"
    simplify: bool = True
    fit_primitives: bool = False
    tolerance: float = 0.1  # mm
