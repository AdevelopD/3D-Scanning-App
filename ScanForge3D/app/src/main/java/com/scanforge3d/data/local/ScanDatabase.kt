package com.scanforge3d.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.scanforge3d.data.model.ScanProject

@Database(
    entities = [ScanProject::class],
    version = 1,
    exportSchema = false
)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
