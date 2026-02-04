package com.scanforge3d.data.remote

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface CloudApiService {

    companion object {
        val BASE_URL: String = com.scanforge3d.BuildConfig.CLOUD_API_URL
    }

    @Multipart
    @POST("api/v1/upload-mesh")
    suspend fun uploadMesh(
        @Part file: MultipartBody.Part
    ): Response<JobStatusResponse>

    @Multipart
    @POST("api/v1/upload-images")
    suspend fun uploadImages(
        @Part files: List<MultipartBody.Part>
    ): Response<JobStatusResponse>

    @GET("api/v1/job/{jobId}")
    suspend fun getJobStatus(
        @Path("jobId") jobId: String
    ): Response<JobStatusResponse>

    @Streaming
    @GET("api/v1/download/{jobId}")
    suspend fun downloadResult(
        @Path("jobId") jobId: String
    ): Response<ResponseBody>
}
