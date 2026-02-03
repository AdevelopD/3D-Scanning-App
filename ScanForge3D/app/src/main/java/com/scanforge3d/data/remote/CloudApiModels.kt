package com.scanforge3d.data.remote

data class JobStatusResponse(
    val job_id: String,
    val status: String,
    val progress: Float,
    val result_url: String?,
    val error: String?
)
