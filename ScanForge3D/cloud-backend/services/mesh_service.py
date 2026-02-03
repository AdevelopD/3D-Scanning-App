"""Mesh loading and processing service."""

import os
import logging
from typing import Optional

logger = logging.getLogger(__name__)


class MeshService:
    """Handles mesh file loading, validation, and basic processing."""

    SUPPORTED_FORMATS = {".stl", ".obj", ".ply"}

    def validate_file(self, filepath: str) -> bool:
        """Check if the file exists and has a supported format."""
        if not os.path.exists(filepath):
            return False
        ext = os.path.splitext(filepath)[1].lower()
        return ext in self.SUPPORTED_FORMATS

    def get_mesh_info(self, filepath: str) -> dict:
        """Load mesh and return basic info (vertex/triangle counts)."""
        try:
            import trimesh
            mesh = trimesh.load(filepath)
            return {
                "vertex_count": len(mesh.vertices),
                "triangle_count": len(mesh.faces),
                "is_watertight": mesh.is_watertight,
                "bounding_box_min": mesh.bounds[0].tolist(),
                "bounding_box_max": mesh.bounds[1].tolist(),
                "file_size_bytes": os.path.getsize(filepath),
            }
        except Exception as e:
            logger.error(f"Failed to load mesh {filepath}: {e}")
            return {}

    def simplify_mesh(self, filepath: str, target_ratio: float = 0.5) -> Optional[str]:
        """Simplify mesh by reducing triangle count. Returns path to simplified mesh."""
        try:
            import trimesh
            mesh = trimesh.load(filepath)
            target_faces = int(len(mesh.faces) * target_ratio)

            if hasattr(mesh, 'simplify_quadric_decimation'):
                simplified = mesh.simplify_quadric_decimation(target_faces)
            else:
                logger.warning("Quadric decimation not available, returning original")
                return filepath

            output_path = filepath.replace(".", "_simplified.")
            simplified.export(output_path)
            logger.info(f"Simplified: {len(mesh.faces)} -> {len(simplified.faces)} faces")
            return output_path
        except Exception as e:
            logger.error(f"Simplification failed: {e}")
            return None
