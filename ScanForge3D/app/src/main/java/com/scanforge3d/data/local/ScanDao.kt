package com.scanforge3d.data.local

import androidx.room.*
import com.scanforge3d.data.model.ScanSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_sessions WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun getSessionsForProject(projectId: String): Flow<List<ScanSession>>

    @Query("SELECT * FROM scan_sessions WHERE id = :id")
    suspend fun getSession(id: String): ScanSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSession)

    @Update
    suspend fun updateSession(session: ScanSession)

    @Delete
    suspend fun deleteSession(session: ScanSession)

    @Query("DELETE FROM scan_sessions WHERE projectId = :projectId")
    suspend fun deleteSessionsForProject(projectId: String)

    @Query("SELECT COUNT(*) FROM scan_sessions WHERE projectId = :projectId")
    suspend fun getSessionCount(projectId: String): Int

    @Query("SELECT SUM(pointCount) FROM scan_sessions WHERE projectId = :projectId")
    suspend fun getTotalPointCount(projectId: String): Int?
}
