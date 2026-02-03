package com.scanforge3d.data.repository

import com.scanforge3d.data.local.ProjectDao
import com.scanforge3d.data.model.ScanProject
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao
) {
    fun getAllProjects(): Flow<List<ScanProject>> = projectDao.getAllProjects()

    suspend fun getProject(id: String): ScanProject? = projectDao.getProject(id)

    suspend fun saveProject(project: ScanProject) = projectDao.insertProject(project)

    suspend fun updateProject(project: ScanProject) = projectDao.updateProject(project)

    suspend fun deleteProject(id: String) = projectDao.deleteById(id)
}
