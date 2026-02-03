package com.scanforge3d.data.model

enum class ExportFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val requiresCloud: Boolean
) {
    STL("stl", "application/sla", "STL (Binary)", false),
    OBJ("obj", "text/plain", "Wavefront OBJ", false),
    PLY("ply", "application/x-ply", "Stanford PLY", false),
    STEP("step", "application/step", "STEP (AP214)", true),
    CATIA("CATPart", "application/octet-stream", "CATIA V5 Part", true)
}
