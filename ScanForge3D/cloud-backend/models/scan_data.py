"""Scan data models for mesh and point cloud representations."""

from pydantic import BaseModel
from typing import Optional, List


class MeshInfo(BaseModel):
    """Metadata about a processed mesh."""
    vertex_count: int
    triangle_count: int
    is_watertight: bool = False
    bounding_box_min: Optional[List[float]] = None
    bounding_box_max: Optional[List[float]] = None
    file_size_bytes: int = 0


class UploadInfo(BaseModel):
    """Information about an uploaded file."""
    filename: str
    file_size: int
    content_type: str


class ProcessingOptions(BaseModel):
    """Options for mesh processing pipeline."""
    simplify: bool = True
    target_triangle_ratio: float = 0.5
    repair: bool = True
    smooth: bool = False
    smooth_iterations: int = 3
