"""Photogrammetry service using COLMAP for image-based 3D reconstruction."""

import os
import subprocess
import logging
from typing import Optional, Callable

logger = logging.getLogger(__name__)


class PhotogrammetryService:
    """
    Runs COLMAP photogrammetry pipeline:
    1. Feature extraction (SIFT)
    2. Feature matching
    3. Sparse reconstruction
    4. Dense reconstruction
    5. Poisson meshing
    """

    def __init__(self, use_gpu: bool = True):
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
        dense_dir = os.path.join(workspace_dir, "dense")
        os.makedirs(sparse_dir, exist_ok=True)
        os.makedirs(dense_dir, exist_ok=True)

        gpu_flag = "1" if self.use_gpu else "0"

        try:
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

            # 4. Image Undistortion
            self._report(progress_callback, 0.55, "Image Undistortion")
            self._run_colmap([
                "colmap", "image_undistorter",
                "--image_path", image_dir,
                "--input_path", os.path.join(sparse_dir, "0"),
                "--output_path", dense_dir,
                "--output_type", "COLMAP",
            ])

            # 5. Dense Stereo
            self._report(progress_callback, 0.65, "Dense Stereo")
            self._run_colmap([
                "colmap", "patch_match_stereo",
                "--workspace_path", dense_dir,
                "--PatchMatchStereo.geom_consistency", "true",
            ])

            # 6. Stereo Fusion
            self._report(progress_callback, 0.75, "Stereo Fusion")
            fused_path = os.path.join(dense_dir, "fused.ply")
            self._run_colmap([
                "colmap", "stereo_fusion",
                "--workspace_path", dense_dir,
                "--output_path", fused_path,
            ])

            # 7. Poisson Meshing
            self._report(progress_callback, 0.85, "Poisson Meshing")
            meshed_path = os.path.join(dense_dir, "meshed.ply")
            self._run_colmap([
                "colmap", "poisson_mesher",
                "--input_path", fused_path,
                "--output_path", meshed_path,
            ])

            self._report(progress_callback, 1.0, "Complete")
            return meshed_path

        except subprocess.CalledProcessError as e:
            logger.error(f"COLMAP failed at step: {e}")
            return None
        except FileNotFoundError:
            logger.error("COLMAP not found. Install via: apt install colmap")
            return None

    def _run_colmap(self, cmd: list):
        """Run a COLMAP command and check for errors."""
        logger.info(f"Running: {' '.join(cmd)}")
        subprocess.run(cmd, check=True, capture_output=True, text=True)

    def _report(
        self,
        callback: Optional[Callable],
        progress: float,
        step: str
    ):
        logger.info(f"[{progress:.0%}] {step}")
        if callback:
            callback(progress, step)
