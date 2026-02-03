package com.scanforge3d.data.repository

import com.scanforge3d.data.remote.CloudApiService
import com.scanforge3d.data.remote.JobStatusResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudRepository @Inject constructor(
    private val api: CloudApiService
) {
    suspend fun uploadMesh(filePart: MultipartBody.Part): Response<JobStatusResponse> {
        return api.uploadMesh(filePart)
    }

    suspend fun getJobStatus(jobId: String): Response<JobStatusResponse> {
        return api.getJobStatus(jobId)
    }

    suspend fun downloadResult(jobId: String): Response<ResponseBody> {
        return api.downloadResult(jobId)
    }
}
