package dev.elainedb.android_junie.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos")
    suspend fun getAll(): List<VideoEntity>

    @Query("SELECT MAX(lastUpdated) FROM videos")
    suspend fun getLastUpdated(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VideoEntity>)

    @Query("DELETE FROM videos")
    suspend fun clear()
}