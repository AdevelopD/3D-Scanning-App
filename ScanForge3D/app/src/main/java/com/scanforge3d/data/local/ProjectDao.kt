package com.scanforge3d.data.local

import androidx.room.*
import com.scanforge3d.data.model.ScanProject
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM scan_projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ScanProject>>

    @Query("SELECT * FROM scan_projects WHERE id = :id")
    suspend fun getProject(id: String): ScanProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ScanProject)

    @Update
    suspend fun updateProject(project: ScanProject)

    @Delete
    suspend fun deleteProject(project: ScanProject)

    @Query("DELETE FROM scan_projects WHERE id = :id")
    suspend fun deleteById(id: String)
}
