package com.openplayer.music.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Base de datos Room de OpenPlayer.
 *
 * - version = 7: versión actual del esquema.
 * - fallbackToDestructiveMigration: como la app aún no está en
 *   producción y el usuario desinstala/instala en cada prueba,
 *   se usa migración destructiva. Cuando la app esté en producción
 *   con usuarios reales, se agregarán migraciones reales para
 *   preservar playlists y estadísticas.
 * - exportSchema = false: no se requiere directorio de exportación
 *   de esquemas para el uso actual del proyecto.
 * - Singleton thread-safe por proceso.
 * - Room3 requiere un SQLiteDriver: se usa BundledSQLiteDriver.
 */
@Database(
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlayStatsEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun artistDao(): ArtistDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun playStatsDao(): PlayStatsDao

    companion object {
        private const val DATABASE_NAME = "openplayer.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }

        private fun buildDatabase(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .setDriver(BundledSQLiteDriver())
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}