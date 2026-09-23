package com.openplayer.music.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos Room de OpenPlayer.
 *
 * - version = 4: se agregó la tabla "artists" (caché reconstruible del
 *   enriquecimiento remoto MusicBrainz → Fanart.tv: MBID, URL de imagen
 *   y timestamp). Al igual que "songs", no contiene datos del usuario.
 * - version = 3: se eliminaron las columnas de MediaInfo (realFormat,
 *   formatVersion, formatProfile, compressionMode, containerFormat,
 *   bitDepth, streamSize) y los campos descriptivos no utilizados
 *   (lyricist, conductor, remixer, arranger, copyright, subtitle,
 *   comment). El esquema se simplificó a los campos realmente usados.
 * - fallbackToDestructiveMigration(dropAllTables = true): las tablas
 *   "songs" y "artists" son CACHÉS RECONSTRUIBLES (escaneo de
 *   MediaStore y enriquecimiento remoto), no datos creados por el
 *   usuario; si el esquema cambia en el futuro, Room borra y recrea
 *   las tablas y el escaneo/enriquecido las vuelve a poblar solo.
 *   Esto NO aplicará a futuras tablas con datos del usuario
 *   (playlists, favoritos, historial), que necesitarán migraciones reales.
 * - exportSchema = false: no se requiere directorio de exportación
 *   de esquemas para un caché reconstruible.
 * - Singleton thread-safe por proceso.
 */
@Database(
    entities = [SongEntity::class, ArtistEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun artistDao(): ArtistDao

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
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}