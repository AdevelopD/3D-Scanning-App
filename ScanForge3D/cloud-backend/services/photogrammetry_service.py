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
    4. Export sparse point cloud
    5. Open3D Poisson meshing from sparse cloud
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

        Args:
            image_dir: Directory containing input images
            workspace_dir: Working directory for COLMAP files
            progress_callback: Optional callback(progress, step_name)

        Returns:
            Path to output mesh PLY file, or None on failure
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
                logger.error("COLMAP mapper produced no model (directory sparse/0 missing)")
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

            # 5. Mesh reconstruction from sparse cloud using Open3D
            self._report(progress_callback, 0.7, "Meshing (Poisson)")
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
            logger.error(f"Photogrammetry error: {e}")
            return None

    def _mesh_from_pointcloud(self, input_ply: str, output_ply: str) -> bool:
        """
        Create a triangle mesh from a sparse point cloud using Open3D.

        Pipeline:
        1. Load PLY point cloud
        2. Estimate normals
        3. Poisson surface reconstruction
        4. Clean up (remove low-density vertices)
        5. Save as PLY
        """
        try:
            import open3d as o3d

            logger.info(f"Loading point cloud from {input_ply}")
            pcd = o3d.io.read_point_cloud(input_ply)
            num_points = len(pcd.points)
            logger.info(f"Point cloud has {num_points} points")

            if num_points < 50:
                logger.error(f"Too few points for meshing: {num_points}")
                return False

            # Remove statistical outliers
            pcd, _ = pcd.remove_statistical_outlier(
                nb_neighbors=20, std_ratio=2.0
            )
            logger.info(f"After outlier removal: {len(pcd.points)} points")

            # Estimate normals
            search_radius = self._estimate_search_radius(pcd)
            pcd.estimate_normals(
                search_param=o3d.geometry.KDTreeSearchParamHybrid(
                    radius=search_radius, max_nn=30
                )
            )

            # Orient normals consistently (toward cameras/outward)
            pcd.orient_normals_consistent_tangent_plane(k=15)

            # Poisson reconstruction
            # Use depth 8 for sparse clouds (lower = smoother, higher = more detail)
            depth = 8 if num_points < 10000 else 9
            logger.info(f"Running Poisson reconstruction (depth={depth})")
            mesh, densities = o3d.geometry.TriangleMesh.create_from_point_cloud_poisson(
                pcd, depth=depth, linear_fit=True
            )

            if len(mesh.vertices) == 0:
                logger.error("Poisson reconstruction produced empty mesh")
                return False

            # Remove low-density vertices (cleanup artifacts)
            densities_np = np.asarray(densities)
            density_threshold = np.quantile(densities_np, 0.05)
            vertices_to_remove = densities_np < density_threshold
            mesh.remove_vertices_by_mask(vertices_to_remove)

            logger.info(
                f"Mesh result: {len(mesh.vertices)} vertices, "
                f"{len(mesh.triangles)} triangles"
            )

            # Save mesh
            o3d.io.write_triangle_mesh(output_ply, mesh)
            return os.path.exists(output_ply) and os.path.getsize(output_ply) > 0

        except ImportError:
            logger.error("open3d not installed. Install via: pip install open3d")
            return False
        except Exception as e:
            logger.error(f"Meshing failed: {e}")
            return False

    def _estimate_search_radius(self, pcd) -> float:
        """Estimate a good search radius for normal estimation based on point density."""
        import open3d as o3d
        distances = pcd.compute_nearest_neighbor_distance()
        avg_dist = np.mean(distances)
        return avg_dist * 5.0

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
