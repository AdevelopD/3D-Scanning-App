"""
ScanForge3D Cloud Backend
Processes uploaded meshes to STEP files via Open CASCADE.
"""

from fastapi import FastAPI, UploadFile, File, HTTPException, BackgroundTasks
from fastapi.responses import FileResponse
from pydantic import BaseModel
from typing import Optional
import uuid
import os
import shutil

app = FastAPI(
    title="ScanForge3D Cloud Processing",
    version="1.0.0"
)

jobs: dict = {}

UPLOAD_DIR = "/tmp/scanforge/uploads"
OUTPUT_DIR = "/tmp/scanforge/outputs"
os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(OUTPUT_DIR, exist_ok=True)


class JobStatus(BaseModel):
    job_id: str
    status: str
    progress: float
    result_url: Optional[str] = None
    error: Optional[str] = None


@app.post("/api/v1/upload-mesh", response_model=JobStatus)
async def upload_mesh(
    file: UploadFile = File(...),
    background_tasks: BackgroundTasks = None
):
    job_id = str(uuid.uuid4())
    upload_path = os.path.join(UPLOAD_DIR, f"{job_id}_{file.filename}")

    with open(upload_path, "wb") as f:
        shutil.copyfileobj(file.file, f)

    jobs[job_id] = {
        "status": "pending",
        "progress": 0.0,
        "input_path": upload_path,
        "result_path": None,
        "error": None
    }

    background_tasks.add_task(process_mesh_to_step, job_id)

    return JobStatus(job_id=job_id, status="pending", progress=0.0)


@app.get("/api/v1/job/{job_id}", response_model=JobStatus)
async def get_job_status(job_id: str):
    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job not found")

    job = jobs[job_id]
    result_url = f"/api/v1/download/{job_id}" if job["result_path"] else None

    return JobStatus(
        job_id=job_id,
        status=job["status"],
        progress=job["progress"],
        result_url=result_url,
        error=job["error"]
    )


@app.get("/api/v1/download/{job_id}")
async def download_result(job_id: str):
    if job_id not in jobs:
        raise HTTPException(status_code=404, detail="Job not found")

    job = jobs[job_id]
    if job["status"] != "completed" or not job["result_path"]:
        raise HTTPException(status_code=400, detail="Result not ready")

    return FileResponse(
        job["result_path"],
        media_type="application/step",
        filename=os.path.basename(job["result_path"])
    )


async def process_mesh_to_step(job_id: str):
    """Convert STL/OBJ/PLY to STEP via Open CASCADE."""
    try:
        job = jobs[job_id]
        job["status"] = "processing"
        job["progress"] = 0.1

        import trimesh
        from OCC.Core.STEPControl import STEPControl_Writer, STEPControl_AsIs
        from OCC.Core.Interface import Interface_Static
        from OCC.Core.StlAPI import StlAPI_Reader
        from OCC.Core.TopoDS import TopoDS_Shape

        job["progress"] = 0.3

        # Load shape via Open CASCADE STL reader
        reader = StlAPI_Reader()
        shape = TopoDS_Shape()
        reader.Read(shape, job["input_path"])

        job["progress"] = 0.7

        # Write STEP
        output_path = os.path.join(OUTPUT_DIR, f"{job_id}.step")

        Interface_Static.SetCVal("write.step.schema", "AP214")
        Interface_Static.SetCVal("write.step.product.name", "ScanForge3D Scan")

        writer = STEPControl_Writer()
        writer.Transfer(shape, STEPControl_AsIs)
        status = writer.Write(output_path)

        job["progress"] = 1.0

        if status == 1:
            job["status"] = "completed"
            job["result_path"] = output_path
        else:
            job["status"] = "failed"
            job["error"] = f"STEP write failed with status {status}"

    except Exception as e:
        jobs[job_id]["status"] = "failed"
        jobs[job_id]["error"] = str(e)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
