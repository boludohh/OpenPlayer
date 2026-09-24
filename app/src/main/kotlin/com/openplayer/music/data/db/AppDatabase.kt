package com.openplayer.music.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos Room de OpenPlayer.
 *
 * - version = 5: se agregaron las tablas "playlists" y "playlist_songs"
 *   (datos del usuario). La migración 4→5 es REAL (no destructiva):
 *   las playlists del usuario se preservan entre actualizaciones.
 * - version = 4: se agregó la tabla "artists" (caché reconstruible del
 *   enriquecimiento remoto MusicBrainz → Fanart.tv: MBID, URL de imagen
 *   y timestamp). Al igual que "songs", no contiene datos del usuario.
 * - version = 3: se eliminaron las columnas de MediaInfo (realFormat,
 *   formatVersion, formatProfile, compressionMode, containerFormat,
 *   bitDepth, streamSize) y los campos descriptivos no utilizados
 *   (lyricist, conductor, remixer, arranger, copyright, subtitle,
 *   comment). El esquema se simplificó a los campos realmente usados.
 * - fallbackToDestructiveMigration(dropAllTables = true): ÚLTIMO
 *   recurso para migraciones destructivas no contempladas; NO debe
 *   aplicarse a las tablas de datos del usuario (playlists). La
 *   migración 4→5 está registrada explícitamente y se ejecuta ANTES
 *   de cualquier fallback, preservando las playlists.
 * - exportSchema = false: no se requiere directorio de exportación
 *   de esquemas para el uso actual del proyecto.
 * - Singleton thread-safe por proceso.
 */
@Database(
    entities = [
        SongEntity::class,
        ArtistEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun artistDao(): ArtistDao

    abstract fun playlistDao(): PlaylistDao

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
                .addMigrations(MIGRATION_4_5)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        /**
         * Migración 4→5: crea las tablas de playlists del usuario.
         *
         * Es NO DESTRUCTIVA: las tablas "songs" y "artists" no se
         * tocan; solo se añaden las dos nuevas. Las playlists creadas
         * por el usuario sobreviven entre versiones de la app.
         *
         * El orden de las operaciones importa: primero playlists
         * (porque playlist_songs tiene FK hacia playlists), luego
         * playlist_songs.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_name`
                    ON `playlists` (`name`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_songs` (
                        `playlistId` INTEGER NOT NULL,
                        `songId` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        PRIMARY KEY(`playlistId`, `songId`),
                        FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`songId`) REFERENCES `songs`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId_position`
                    ON `playlist_songs` (`playlistId`, `position`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_playlist_songs_songId`
                    ON `playlist_songs` (`songId`)
                    """.trimIndent()
                )
            }
        }
    }
}