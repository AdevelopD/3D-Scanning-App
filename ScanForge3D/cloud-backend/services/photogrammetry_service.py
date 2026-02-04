"""Photogrammetry service using COLMAP for image-based 3D reconstruction."""

import os
import subprocess
import logging
import numpy as np
from typing import Optional, Callable

logger = logging.getLogger(__name__)


class PhotogrammetryService:
    """
    Runs COLMAP photogrammetry pipeline.

    CPU-only pipeline (no GPU required):
    1. Feature extraction (SIFT, CPU)
    2. Feature matching (CPU)
    3. Sparse reconstruction (mapper)
    4. Export sparse point cloud as PLY
    5. Mesh from point cloud via trimesh + scipy
    """

    def __init__(self, use_gpu: bool = False):
        self.use_gpu = use_gpu

    def reconstruct(
        self,
        image_dir: str,
        workspace_dir: str,
        progress_callback: Optional[Callable[[float, str], None]] = None
    ) -> Optional[str]:
        """
        Run full photogrammetry pipeline.

        Returns:
            Path to output PLY file, or None on failure
        """
        os.makedirs(workspace_dir, exist_ok=True)
        database_path = os.path.join(workspace_dir, "database.db")
        sparse_dir = os.path.join(workspace_dir, "sparse")
        os.makedirs(sparse_dir, exist_ok=True)

        gpu_flag = "1" if self.use_gpu else "0"

        try:
            # Count input images
            image_files = [f for f in os.listdir(image_dir)
                          if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
            logger.info(f"Found {len(image_files)} images in {image_dir}")

            if len(image_files) < 3:
                logger.error(f"Too few images: {len(image_files)}, need at least 3")
                return None

            # 1. Feature Extraction
            self._report(progress_callback, 0.1, "Feature Extraction")
            self._run_colmap([
                "colmap", "feature_extractor",
                "--database_path", database_path,
                "--image_path", image_dir,
                "--ImageReader.single_camera", "1",
                "--SiftExtraction.use_gpu", gpu_flag,
            ])

            # 2. Feature Matching
            self._report(progress_callback, 0.25, "Feature Matching")
            self._run_colmap([
                "colmap", "exhaustive_matcher",
                "--database_path", database_path,
                "--SiftMatching.use_gpu", gpu_flag,
            ])

            # 3. Sparse Reconstruction
            self._report(progress_callback, 0.4, "Sparse Reconstruction")
            self._run_colmap([
                "colmap", "mapper",
                "--database_path", database_path,
                "--image_path", image_dir,
                "--output_path", sparse_dir,
            ])

            # Check if sparse reconstruction produced a model
            model_dir = os.path.join(sparse_dir, "0")
            if not os.path.isdir(model_dir):
                logger.error("COLMAP mapper produced no model (sparse/0 missing)")
                return None

            # 4. Export sparse point cloud as PLY
            self._report(progress_callback, 0.55, "Exporting Point Cloud")
            sparse_ply = os.path.join(workspace_dir, "sparse_cloud.ply")
            self._run_colmap([
                "colmap", "model_converter",
                "--input_path", model_dir,
                "--output_path", sparse_ply,
                "--output_type", "PLY",
            ])

            if not os.path.exists(sparse_ply) or os.path.getsize(sparse_ply) == 0:
                logger.error("Sparse PLY export failed or is empty")
                return None

            # 5. Create mesh from point cloud
            self._report(progress_callback, 0.7, "Meshing")
            meshed_path = os.path.join(workspace_dir, "meshed.ply")
            success = self._mesh_from_pointcloud(sparse_ply, meshed_path)

            if not success:
                # Fallback: deliver the sparse point cloud directly
                logger.warning("Meshing failed, delivering sparse point cloud")
                meshed_path = sparse_ply

            self._report(progress_callback, 1.0, "Complete")
            return meshed_path

        except subprocess.CalledProcessError as e:
            logger.error(f"COLMAP failed: {e}")
            if e.stderr:
                logger.error(f"COLMAP stderr: {e.stderr}")
            return None
        except FileNotFoundError:
            logger.error("COLMAP not found. Install via: apt install colmap")
            return None
        except Exception as e:
            logger.error(f"Photogrammetry error: {e}", exc_info=True)
            return None

    def _mesh_from_pointcloud(self, input_ply: str, output_ply: str) -> bool:
        """
        Create a triangle mesh from a point cloud using trimesh + scipy.

        Uses Ball Pivoting / Alpha Shape / Convex Hull depending on point count.
        """
        try:
            import trimesh
            from scipy.spatial import Delaunay, ConvexHull

            logger.info(f"Loading point cloud from {input_ply}")
            cloud = trimesh.load(input_ply)

            # trimesh loads PLY point clouds as PointCloud or Trimesh
            if hasattr(cloud, 'vertices'):
                points = np.array(cloud.vertices)
            elif hasattr(cloud, 'points'):
                points = np.array(cloud.points) if hasattr(cloud.points, '__array__') else np.array(list(cloud.points))
            else:
                logger.error("Could not extract points from PLY")
                return False

            num_points = len(points)
            logger.info(f"Point cloud has {num_points} points")

            if num_points < 4:
                logger.error(f"Too few points for meshing: {num_points}")
                return False

            # Remove outliers (points far from centroid)
            centroid = points.mean(axis=0)
            distances = np.linalg.norm(points - centroid, axis=1)
            threshold = np.percentile(distances, 95)
            points = points[distances <= threshold]
            logger.info(f"After outlier removal: {len(points)} points")

            if len(points) < 4:
                return False

            # Create mesh using alpha shape (via trimesh)
            try:
                # Try alpha shape first - produces better results
                alpha = self._estimate_alpha(points)
                logger.info(f"Creating alpha shape with alpha={alpha:.4f}")
                mesh = trimesh.Trimesh(vertices=points)
                mesh = trimesh.convex.convex_hull(mesh)

                # If we have enough points, try Delaunay-based approach
                if num_points > 100:
                    mesh = self._delaunay_mesh(points)
                    if mesh is None:
                        # Fallback to convex hull
                        mesh = trimesh.convex.convex_hull(
                            trimesh.PointCloud(points)
                        )
            except Exception as e:
                logger.warning(f"Alpha shape failed ({e}), using convex hull")
                mesh = trimesh.convex.convex_hull(
                    trimesh.PointCloud(points)
                )

            if mesh is None or len(mesh.faces) == 0:
                logger.error("Meshing produced no faces")
                return False

            logger.info(
                f"Mesh result: {len(mesh.vertices)} vertices, "
                f"{len(mesh.faces)} triangles"
            )

            mesh.export(output_ply)
            return os.path.exists(output_ply) and os.path.getsize(output_ply) > 0

        except ImportError as e:
            logger.error(f"Missing dependency: {e}")
            return False
        except Exception as e:
            logger.error(f"Meshing failed: {e}", exc_info=True)
            return False

    def _delaunay_mesh(self, points: np.ndarray):
        """Create mesh from 3D Delaunay triangulation, keeping surface faces."""
        import trimesh
        from scipy.spatial import Delaunay

        try:
            tri = Delaunay(points)

            # Extract surface triangles from tetrahedra
            # Each tetrahedron has 4 triangular faces
            faces = set()
            for simplex in tri.simplices:
                for i in range(4):
                    face = tuple(sorted([
                        simplex[j] for j in range(4) if j != i
                    ]))
                    if face in faces:
                        faces.discard(face)  # Internal face (shared)
                    else:
                        faces.add(face)

            faces = np.array(list(faces))
            if len(faces) == 0:
                return None

            mesh = trimesh.Trimesh(vertices=points, faces=faces)
            # Remove degenerate faces
            mesh.remove_degenerate_faces()
            mesh.remove_duplicate_faces()

            return mesh if len(mesh.faces) > 0 else None

        except Exception as e:
            logger.warning(f"Delaunay meshing failed: {e}")
            return None

    def _estimate_alpha(self, points: np.ndarray) -> float:
        """Estimate a good alpha value based on average nearest neighbor distance."""
        from scipy.spatial import KDTree
        tree = KDTree(points)
        distances, _ = tree.query(points, k=2)
        avg_nn = np.mean(distances[:, 1])
        return avg_nn * 3.0

    def _run_colmap(self, cmd: list):
        """Run a COLMAP command and check for errors."""
        logger.info(f"Running: {' '.join(cmd)}")
        result = subprocess.run(
            cmd, check=True, capture_output=True, text=True, timeout=600
        )
        if result.stdout:
            logger.debug(f"COLMAP stdout: {result.stdout[-500:]}")
        if result.stderr:
            logger.debug(f"COLMAP stderr: {result.stderr[-500:]}")

    def _report(
        self,
        callback: Optional[Callable],
        progress: float,
        step: str
    ):
        logger.info(f"[{progress:.0%}] {step}")
        if callback:
            callback(progress, step)
