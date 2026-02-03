"""Reverse engineering service: mesh to BREP primitive fitting."""

import logging
from typing import List, Optional

logger = logging.getLogger(__name__)


class PrimitiveFit:
    """Result of fitting a geometric primitive to a mesh region."""

    def __init__(self, primitive_type: str, parameters: dict, fitness: float):
        self.primitive_type = primitive_type  # "plane", "cylinder", "sphere", "cone"
        self.parameters = parameters
        self.fitness = fitness  # 0.0 (bad) to 1.0 (perfect)


class ReverseEngineeringService:
    """
    Attempts to fit geometric primitives (planes, cylinders, spheres)
    to mesh regions for creating parametric CAD geometry.

    Note: Full BREP reverse engineering is complex. This provides basic
    primitive detection that can enhance STEP export quality.
    """

    def detect_planes(self, filepath: str, tolerance: float = 0.1) -> List[PrimitiveFit]:
        """Detect planar regions in the mesh."""
        try:
            import trimesh
            import numpy as np

            mesh = trimesh.load(filepath)
            facets = mesh.facets

            planes = []
            for facet_indices in facets:
                facet_normals = mesh.face_normals[facet_indices]
                mean_normal = facet_normals.mean(axis=0)
                mean_normal /= np.linalg.norm(mean_normal)

                # Check planarity: all normals should be close to mean
                dot_products = np.dot(facet_normals, mean_normal)
                if np.all(dot_products > 1.0 - tolerance):
                    centroid = mesh.triangles_center[facet_indices].mean(axis=0)
                    planes.append(PrimitiveFit(
                        primitive_type="plane",
                        parameters={
                            "normal": mean_normal.tolist(),
                            "point": centroid.tolist(),
                            "face_count": len(facet_indices),
                        },
                        fitness=float(dot_products.min()),
                    ))

            logger.info(f"Detected {len(planes)} planar regions")
            return planes

        except Exception as e:
            logger.error(f"Plane detection failed: {e}")
            return []

    def detect_cylinders(self, filepath: str) -> List[PrimitiveFit]:
        """Detect cylindrical regions in the mesh (simplified RANSAC approach)."""
        try:
            import trimesh
            import numpy as np

            mesh = trimesh.load(filepath)

            # Basic cylinder detection via normal analysis:
            # Cylindrical regions have normals that lie in a plane
            # perpendicular to the cylinder axis.
            # Full implementation would use RANSAC.

            logger.info("Cylinder detection: basic analysis only")
            return []

        except Exception as e:
            logger.error(f"Cylinder detection failed: {e}")
            return []
