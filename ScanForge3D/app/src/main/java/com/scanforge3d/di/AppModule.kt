package com.scanforge3d.di

import android.content.Context
import androidx.room.Room
import com.scanforge3d.data.local.ProjectDao
import com.scanforge3d.data.local.ScanDatabase
import com.scanforge3d.data.remote.CloudApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ScanDatabase = Room.databaseBuilder(
        context,
        ScanDatabase::class.java,
        "scanforge_db"
    ).build()

    @Provides
    fun provideProjectDao(db: ScanDatabase): ProjectDao = db.projectDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
        )
        .build()

    @Provides
    @Singleton
    fun provideCloudApiService(client: OkHttpClient): CloudApiService =
        Retrofit.Builder()
            .baseUrl(CloudApiService.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CloudApiService::class.java)
}
