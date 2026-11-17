package com.oam.pdftools.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity data class Recent(
    @PrimaryKey val uri: String, val name: String, val op: String, val size: Long,
    val time: Long = System.currentTimeMillis()
)

@Dao interface RecentDao {
    @Query("SELECT * FROM Recent ORDER BY time DESC LIMIT 50") fun all(): Flow<List<Recent>>
    @Upsert suspend fun put(r: Recent)
    @Query("DELETE FROM Recent WHERE uri = :uri") suspend fun remove(uri: String)
}

@Database(entities = [Recent::class], version = 1)
abstract class AppDb : RoomDatabase() { abstract fun recents(): RecentDao }
