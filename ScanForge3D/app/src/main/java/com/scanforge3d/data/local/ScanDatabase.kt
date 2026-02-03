package com.scanforge3d.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.scanforge3d.data.model.ScanProject
import com.scanforge3d.data.model.ScanSession

@Database(
    entities = [ScanProject::class, ScanSession::class],
    version = 2,
    exportSchema = false
)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun scanDao(): ScanDao
}
