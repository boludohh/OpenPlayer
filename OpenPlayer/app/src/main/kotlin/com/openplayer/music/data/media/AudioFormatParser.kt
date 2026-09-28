package com.openplayer.music.data.media

import android.util.Log
import java.io.RandomAccessFile

/**
 * Valida formatos de audio (MP3, FLAC, OGG, M4A) leyendo bytes del header.
 * Formatos soportados: MP3 (MPEG-1/2 Layer III), FLAC, OGG (Vorbis/Opus/FLAC), M4A (AAC-LC).
 */
object AudioFormatParser {

    private const val LOG_TAG = "AudioFormatParser"
    private const val MAX_BYTES_TO_READ = 65536

    sealed class AudioFormat(val displayName: String) {
        object Mp3Mpeg1Layer3 : AudioFormat("MP3 MPEG-1 Layer III")
        object Mp3Mpeg2Layer3 : AudioFormat("MP3 MPEG-2 Layer III")
        object Flac : AudioFormat("FLAC nativo")
        object OggVorbis : AudioFormat("OGG Vorbis")
        object OggOpus : AudioFormat("OGG Opus")
        object OggFlac : AudioFormat("OGG FLAC")
        object M4aAacLc : AudioFormat("M4A AAC-LC")
    }

    private data class BoxInfo(val contentStart: Long, val contentEnd: Long)

    /** Valida si el archivo corresponde a un formato permitido. */
    fun isValid(path: String): Boolean {
        val format = runCatching { detectFormat(path) }
            .onFailure { Log.e(LOG_TAG, "Error en [$path]: ${it.message}") }
            .getOrNull()

        val valid = format != null
        Log.d(LOG_TAG, "[$path] formato=${format?.displayName ?: "NO VÁLIDO"} válido=$valid")
        return valid
    }

    private fun detectFormat(path: String): AudioFormat? {
        RandomAccessFile(path, "r").use { file ->
            val fileLength = file.length()
            if (fileLength < 4) {
                Log.d(LOG_TAG, "[$path] Archivo demasiado pequeño: $fileLength bytes")
                return null
            }

            val header = ByteArray(12)
            val headerRead = file.read(header)
            if (headerRead < 4) {
                Log.d(LOG_TAG, "[$path] No se pudieron leer los primeros bytes")
                return null
            }

            if (matchesAscii(header, "fLaC")) return AudioFormat.Flac
            if (matchesAscii(header, "OggS")) {
                file.seek(0)
                return detectOggCodec(file, path)
            }
            if (hasFtypBox(file, path)) {
                file.seek(0)
                return detectM4aCodec(file, path)
            }
            
            file.seek(0)
            return detectMp3(file, path)
        }
    }

    private fun detectMp3(file: RandomAccessFile, path: String): AudioFormat? {
        val header = ByteArray(4)
        file.seek(0)
        if (file.read(header) < 4) {
            Log.d(LOG_TAG, "[$path] MP3: No se pudieron leer los primeros 4 bytes")
            return null
        }

        var searchStartPos = 0L
        if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
            val id3Size = readId3v2Size(file)
            if (id3Size > 0) {
                searchStartPos = 10 + id3Size
                Log.d(LOG_TAG, "[$path] MP3: ID3v2 detectado, saltando a offset $searchStartPos")
                file.seek(searchStartPos)
                if (file.read(header) < 4) {
                    Log.d(LOG_TAG, "[$path] MP3: No se pudieron leer bytes después del ID3v2")
                    return null
                }
            } else {
                Log.d(LOG_TAG, "[$path] MP3: ID3v2 detectado pero tamaño inválido: $id3Size")
            }
        }

        file.seek(searchStartPos)
        return findMp3FrameSync(file, path)
    }

    /** Lee el tamaño del tag ID3v2 (syncsafe integer). */
    private fun readId3v2Size(file: RandomAccessFile): Long {
        val sizeBytes = ByteArray(4)
        file.seek(6)
        if (file.read(sizeBytes) < 4) return 0

        return ((sizeBytes[0].toInt() and 0x7F).toLong() shl 21) or
               ((sizeBytes[1].toInt() and 0x7F).toLong() shl 14) or
               ((sizeBytes[2].toInt() and 0x7F).toLong() shl 7) or
               (sizeBytes[3].toInt() and 0x7F).toLong()
    }

    private fun findMp3FrameSync(file: RandomAccessFile, path: String): AudioFormat? {
        val buffer = ByteArray(4)
        var position = file.filePointer
        val maxPosition = minOf(file.length(), position + MAX_BYTES_TO_READ)
        var bytesScanned = 0

        while (position < maxPosition - 4) {
            file.seek(position)
            if (file.read(buffer) < 4) {
                Log.d(LOG_TAG, "[$path] MP3: Fin de archivo después de escanear $bytesScanned bytes")
                return null
            }

            // Frame sync: byte 0 = 0xFF, byte 1 bits 7-5 = 111
            if ((buffer[0].toInt() and 0xFF) == 0xFF && (buffer[1].toInt() and 0xE0) == 0xE0) {
                val versionBits = (buffer[1].toInt() shr 3) and 0x03
                val layerBits = (buffer[1].toInt() shr 1) and 0x03

                if (layerBits == 0x01) {
                    Log.d(LOG_TAG, "[$path] MP3: Frame sync válido en offset $position (versión=$versionBits, layer=$layerBits)")
                    return when (versionBits) {
                        0x03 -> AudioFormat.Mp3Mpeg1Layer3
                        0x02 -> AudioFormat.Mp3Mpeg2Layer3
                        else -> {
                            Log.d(LOG_TAG, "[$path] MP3: Versión MPEG no soportada: $versionBits")
                            null
                        }
                    }
                }
            }
            position++
            bytesScanned++
        }
        
        Log.d(LOG_TAG, "[$path] MP3: Frame sync no encontrado tras escanear $bytesScanned bytes")
        return null
    }

    private fun detectOggCodec(file: RandomAccessFile, path: String): AudioFormat? {
        val pageHeader = ByteArray(27)
        file.seek(0)
        if (file.read(pageHeader) < 27) {
            Log.d(LOG_TAG, "[$path] OGG: No se pudo leer el header de página")
            return null
        }

        if (!matchesAscii(pageHeader, "OggS") || pageHeader[4] != 0.toByte()) {
            Log.d(LOG_TAG, "[$path] OGG: Header inválido")
            return null
        }

        val numSegments = pageHeader[26].toInt() and 0xFF
        val segmentTable = ByteArray(numSegments)
        if (file.read(segmentTable) < numSegments) {
            Log.d(LOG_TAG, "[$path] OGG: No se pudo leer la tabla de segmentos")
            return null
        }

        var packetSize = 0
        for (i in 0 until numSegments) {
            val segSize = segmentTable[i].toInt() and 0xFF
            packetSize += segSize
            if (segSize < 255) break
        }

        if (packetSize == 0) {
            Log.d(LOG_TAG, "[$path] OGG: Tamaño de paquete inválido: 0")
            return null
        }

        val readSize = minOf(packetSize, 64)
        val packetData = ByteArray(readSize)
        if (file.read(packetData) < readSize) {
            Log.d(LOG_TAG, "[$path] OGG: No se pudo leer el primer paquete")
            return null
        }

        if (packetData.size >= 7 && packetData[0] == 0x01.toByte() && matchesAscii(packetData, "vorbis", 1)) return AudioFormat.OggVorbis
        if (packetData.size >= 8 && matchesAscii(packetData, "OpusHead")) return AudioFormat.OggOpus
        if (packetData.size >= 5 && packetData[0] == 0x7F.toByte() && matchesAscii(packetData, "FLAC", 1)) return AudioFormat.OggFlac

        Log.d(LOG_TAG, "[$path] OGG: Códec no reconocido en el primer paquete")
        return null
    }

    private fun hasFtypBox(file: RandomAccessFile, path: String): Boolean {
        file.seek(0)
        val fileLength = file.length()
        var pos = 0L
        val buffer = ByteArray(8)
        var boxesScanned = 0

        while (pos <= fileLength - 8 && boxesScanned < 10) {
            file.seek(pos)
            if (file.read(buffer) < 8) return false

            var size = ((buffer[0].toInt() and 0xFF).toLong() shl 24) or
                       ((buffer[1].toInt() and 0xFF).toLong() shl 16) or
                       ((buffer[2].toInt() and 0xFF).toLong() shl 8) or
                       (buffer[3].toInt() and 0xFF).toLong()

            if (size == 1L) {
                val extSizeBytes = ByteArray(8)
                if (file.read(extSizeBytes) < 8) return false
                size = ((extSizeBytes[0].toLong() and 0xFF) shl 56) or
                       ((extSizeBytes[1].toLong() and 0xFF) shl 48) or
                       ((extSizeBytes[2].toLong() and 0xFF) shl 40) or
                       ((extSizeBytes[3].toLong() and 0xFF) shl 32) or
                       ((extSizeBytes[4].toLong() and 0xFF) shl 24) or
                       ((extSizeBytes[5].toLong() and 0xFF) shl 16) or
                       ((extSizeBytes[6].toLong() and 0xFF) shl 8) or
                       (extSizeBytes[7].toLong() and 0xFF)
            } else if (size == 0L) {
                size = fileLength - pos
            }

            if (size < 8 || pos + size > fileLength) {
                Log.d(LOG_TAG, "[$path] M4A: Box inválido en offset $pos (size=$size)")
                return false
            }

            if (matchesAscii(buffer, "ftyp", 4)) {
                Log.d(LOG_TAG, "[$path] M4A: Box 'ftyp' encontrado en offset $pos")
                return true
            }

            pos += size
            boxesScanned++
        }
        return false
    }

    private fun detectM4aCodec(file: RandomAccessFile, path: String): AudioFormat? {
        val fileLength = file.length()
        var position = 0L

        val moov = findBox(file, position, fileLength, "moov", path) ?: run {
            Log.d(LOG_TAG, "[$path] M4A: Box 'moov' no encontrado"); return null
        }
        val trak = findBox(file, moov.contentStart, moov.contentEnd, "trak", path) ?: run {
            Log.d(LOG_TAG, "[$path] M4A: Box 'trak' no encontrado"); return null
        }
        val mdia = findBox(file, trak.contentStart, trak.contentEnd, "mdia", path) ?: run {
            Log.d(LOG_TAG, "[$path] M4A: Box 'mdia' no encontrado"); return null
        }
        val minf = findBox(file, mdia.contentStart, mdia.contentEnd, "minf", path) ?: run {
            Log.d(LOG_TAG, "[$path] M4A: Box 'minf' no encontrado"); return null
        }
        val stbl = findBox(file, minf.contentStart, minf.contentEnd, "stbl", path) ?: run {
            Log.d(LOG_TAG, "[$path] M4A: Box 'stbl' no encontrado"); return null
        }
        val stsd = findBox(file, stbl.contentStart, stbl.contentEnd, "stsd", path) ?: run {
            Log.d(LOG_TAG, "[$path] M4A: Box 'stsd' no encontrado"); return null
        }

        // stsd es un FullBox: 8 bytes header + 4 bytes version/flags + 4 bytes entry_count
        // El contenido devuelto por findBox empieza después del header de 8 bytes.
        // Los entry boxes empiezan en stsd.contentStart + 8.
        
        val mp4a = findBox(file, stsd.contentStart + 8, stsd.contentEnd, "mp4a", path) ?: run {
            Log.d(LOG_TAG, "[$path] M4A: Box 'mp4a' no encontrado"); return null
        }

        // Dentro de mp4a, hay 28 bytes de datos específicos de audio (reserved, data reference index, channel count, sample size, etc.)
        // Los sub-boxes empiezan en mp4a.contentStart + 28.
        
        val esds = findBox(file, mp4a.contentStart + 28, mp4a.contentEnd, "esds", path) ?: run {
            Log.d(LOG_TAG, "[$path] M4A: Box 'esds' no encontrado"); return null
        }

        // esds es un FullBox, su contenido empieza después de los 8 bytes de header.
        // Los primeros 4 bytes son version y flags.
        val esdsDataSize = minOf((esds.contentEnd - esds.contentStart - 4).toInt(), 256)
        if (esdsDataSize < 4) {
            Log.d(LOG_TAG, "[$path] M4A: Box 'esds' demasiado pequeño")
            return null
        }
        
        val esdsData = ByteArray(esdsDataSize)
        file.seek(esds.contentStart + 4) // Saltar version y flags
        if (file.read(esdsData) < 4) {
            Log.d(LOG_TAG, "[$path] M4A: No se pudieron leer datos de 'esds'")
            return null
        }

        val ascOffset = findDecSpecificInfo(esdsData, esdsData.size)
        if (ascOffset < 0 || ascOffset >= esdsData.size) {
            Log.d(LOG_TAG, "[$path] M4A: AudioSpecificConfig no encontrado en 'esds'")
            return null
        }

        val firstByte = esdsData[ascOffset].toInt() and 0xFF
        val audioObjectType = (firstByte shr 3) and 0x1F

        return if (audioObjectType == 2) {
            Log.d(LOG_TAG, "[$path] M4A: AAC-LC detectado (audioObjectType=2)")
            AudioFormat.M4aAacLc
        } else {
            Log.d(LOG_TAG, "[$path] M4A: AudioObjectType no soportado: $audioObjectType")
            null
        }
    }

    private fun findBox(file: RandomAccessFile, startPos: Long, endPos: Long, boxType: String, path: String): BoxInfo? {
        var pos = startPos
        val buffer = ByteArray(8)

        while (pos <= endPos - 8) {
            file.seek(pos)
            if (file.read(buffer) < 8) return null

            var size = ((buffer[0].toInt() and 0xFF).toLong() shl 24) or
                       ((buffer[1].toInt() and 0xFF).toLong() shl 16) or
                       ((buffer[2].toInt() and 0xFF).toLong() shl 8) or
                       (buffer[3].toInt() and 0xFF).toLong()
            
            var headerSize = 8L

            if (size == 1L) {
                val extSizeBytes = ByteArray(8)
                if (file.read(extSizeBytes) < 8) return null
                size = ((extSizeBytes[0].toLong() and 0xFF) shl 56) or
                       ((extSizeBytes[1].toLong() and 0xFF) shl 48) or
                       ((extSizeBytes[2].toLong() and 0xFF) shl 40) or
                       ((extSizeBytes[3].toLong() and 0xFF) shl 32) or
                       ((extSizeBytes[4].toLong() and 0xFF) shl 24) or
                       ((extSizeBytes[5].toLong() and 0xFF) shl 16) or
                       ((extSizeBytes[6].toLong() and 0xFF) shl 8) or
                       (extSizeBytes[7].toLong() and 0xFF)
                headerSize = 16L
            } else if (size == 0L) {
                size = endPos - pos
            }

            if (size < headerSize || pos + size > endPos) {
                Log.d(LOG_TAG, "[$path] M4A: Box '$boxType' inválido o fuera de límites en offset $pos (size=$size)")
                return null
            }

            if (matchesAscii(buffer, boxType, 4)) {
                return BoxInfo(pos + headerSize, pos + size)
            }
            
            pos += size
        }
        return null
    }

    private fun findDecSpecificInfo(data: ByteArray, length: Int): Int {
        var i = 0
        while (i < length - 4) {
            val tag = data[i].toInt() and 0xFF
            if (tag == 0x05) {
                i++
                var size = 0
                var b: Int
                do {
                    if (i >= length) return -1
                    b = data[i].toInt() and 0xFF
                    size = (size shl 7) or (b and 0x7F)
                    i++
                } while ((b and 0x80) != 0)
                return i
            }
            i++
        }
        return -1
    }

    private fun matchesAscii(data: ByteArray, expected: String, offset: Int = 0): Boolean {
        if (offset + expected.length > data.size) return false
        for (i in expected.indices) {
            if (data[offset + i] != expected[i].code.toByte()) return false
        }
        return true
    }
}