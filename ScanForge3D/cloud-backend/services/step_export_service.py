"""STEP export service using Open CASCADE (PythonOCC)."""

import os
import logging
from typing import Optional

logger = logging.getLogger(__name__)


class StepExportService:
    """Converts mesh files (STL/OBJ/PLY) to STEP format via Open CASCADE."""

    def export_to_step(
        self,
        input_path: str,
        output_path: str,
        product_name: str = "ScanForge3D Scan",
        schema: str = "AP214"
    ) -> bool:
        """
        Convert a mesh file to STEP format.

        Args:
            input_path: Path to input mesh file (STL/OBJ/PLY)
            output_path: Path for output STEP file
            product_name: Product name embedded in STEP metadata
            schema: STEP schema (AP203 or AP214)

        Returns:
            True if export succeeded
        """
        try:
            from OCC.Core.STEPControl import STEPControl_Writer, STEPControl_AsIs
            from OCC.Core.Interface import Interface_Static
            from OCC.Core.TopoDS import TopoDS_Shape

            # Determine input format and load
            ext = os.path.splitext(input_path)[1].lower()
            shape = self._load_shape(input_path, ext)

            if shape is None or shape.IsNull():
                logger.error(f"Failed to load shape from {input_path}")
                return False

            # Configure STEP writer
            Interface_Static.SetCVal("write.step.schema", schema)
            Interface_Static.SetCVal("write.step.product.name", product_name)

            writer = STEPControl_Writer()
            writer.Transfer(shape, STEPControl_AsIs)
            status = writer.Write(output_path)

            success = status == 1  # IFSelect_RetDone
            if success:
                logger.info(f"STEP export: {input_path} -> {output_path}")
            else:
                logger.error(f"STEP write failed with status {status}")

            return success

        except ImportError:
            logger.error("PythonOCC not installed. Install via: conda install -c conda-forge pythonocc-core")
            return False
        except Exception as e:
            logger.error(f"STEP export failed: {e}")
            return False

    def _load_shape(self, filepath: str, ext: str):
        """Load a TopoDS_Shape from various mesh formats."""
        from OCC.Core.TopoDS import TopoDS_Shape

        if ext == ".stl":
            return self._load_stl(filepath)
        elif ext in (".obj", ".ply"):
            return self._load_via_trimesh_stl(filepath)
        else:
            logger.error(f"Unsupported format: {ext}")
            return None

    def _load_stl(self, filepath: str):
        """Load STL directly via Open CASCADE."""
        from OCC.Core.StlAPI import StlAPI_Reader
        from OCC.Core.TopoDS import TopoDS_Shape

        reader = StlAPI_Reader()
        shape = TopoDS_Shape()
        reader.Read(shape, filepath)
        return shape

    def _load_via_trimesh_stl(self, filepath: str):
        """Load OBJ/PLY by converting to STL first, then loading in OCC."""
        import trimesh
        import tempfile

        mesh = trimesh.load(filepath)
        with tempfile.NamedTemporaryFile(suffix=".stl", delete=False) as tmp:
            tmp_path = tmp.name
            mesh.export(tmp_path, file_type="stl")

        shape = self._load_stl(tmp_path)
        os.unlink(tmp_path)
        return shape
