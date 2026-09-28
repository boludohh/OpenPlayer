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
 * - version = 7: se agregó la tabla "play_stats" (estadísticas de
 *   reproducción por canción: playCount, completedCount, playedMs,
 *   lastPlayedAt). La migración 6→7 es REAL (no destructiva): las
 *   estadísticas del usuario se preservan entre actualizaciones.
 * - version = 6: la tabla "artists" se reconstruyó para el pipeline
 *   Deezer (columnas name, deezerId, imageUrl, updatedAt). La
 *   migración 5→6 es QUIRÚRGICA: hace DROP+CREATE únicamente de
 *   "artists" (caché reconstruible); "songs", "playlists" y
 *   "playlist_songs" (datos del usuario) quedan intactos.
 * - version = 5: se agregaron las tablas "playlists" y "playlist_songs"
 *   (datos del usuario). La migración 4→5 es REAL (no destructiva):
 *   las playlists del usuario se preservan entre actualizaciones.
 * - version = 4: se agregó la tabla "artists" original (caché del
 *   enriquecimiento MusicBrainz → Fanart.tv, hoy reemplazado por Deezer).
 * - version = 3: se eliminaron las columnas de MediaInfo (realFormat,
 *   formatVersion, formatProfile, compressionMode, containerFormat,
 *   bitDepth, streamSize) y los campos descriptivos no utilizados
 *   (lyricist, conductor, remixer, arranger, copyright, subtitle,
 *   comment). El esquema se simplificó a los campos realmente usados.
 * - fallbackToDestructiveMigration(dropAllTables = true): ÚLTIMO
 *   recurso para migraciones destructivas no contempladas; NO debe
 *   aplicarse a las tablas de datos del usuario (playlists, play_stats).
 *   Las migraciones 4→5, 5→6 y 6→7 están registradas explícitamente y
 *   se ejecutan ANTES de cualquier fallback.
 * - exportSchema = false: no se requiere directorio de exportación
 *   de esquemas para el uso actual del proyecto.
 * - Singleton thread-safe por proceso.
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
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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

        /**
         * Migración 5→6: reconstruye la tabla "artists" para el
         * pipeline Deezer (deezerId + imageUrl en lugar de mbid +
         * disambiguation + thumbUrl).
         *
         * Es QUIRÚRGICA: el DROP afecta ÚNICAMENTE a "artists", que es
         * un caché reconstruible sin datos del usuario. "songs",
         * "playlists" y "playlist_songs" no se tocan en absoluto.
         * Tras la migración, la pestaña Artistas re-enriquece todo el
         * caché contra Deezer en la primera visita.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `artists`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `artists` (
                        `name` TEXT NOT NULL,
                        `deezerId` INTEGER NOT NULL,
                        `imageUrl` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`name`)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Migración 6→7: crea la tabla "play_stats" (estadísticas de
         * reproducción por canción).
         *
         * Es NO DESTRUCTIVA: las tablas "songs", "artists", "playlists"
         * y "playlist_songs" no se tocan; solo se añade la nueva. Las
         * estadísticas del usuario se preservan entre versiones de la app.
         *
         * La FK hacia "songs" con CASCADE on delete garantiza que si
         * una canción se elimina de la biblioteca, sus estadísticas se
         * borran automáticamente (consistencia sin referencias rotas).
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `play_stats` (
                        `songId` INTEGER NOT NULL,
                        `playCount` INTEGER NOT NULL,
                        `completedCount` INTEGER NOT NULL,
                        `playedMs` INTEGER NOT NULL,
                        `lastPlayedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`songId`),
                        FOREIGN KEY(`songId`) REFERENCES `songs`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_play_stats_lastPlayedAt`
                    ON `play_stats` (`lastPlayedAt`)
                    """.trimIndent()
                )
            }
        }
    }
}