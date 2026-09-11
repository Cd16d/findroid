package dev.jdtech.jellyfin.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.StatFs
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import timber.log.Timber

object Mp4Remuxer {

    private const val DEFAULT_BUFFER_SIZE = 4 * 1024 * 1024 // 4 MB buffer
    private const val YIELD_INTERVAL_SAMPLES = 500L
    private val KNOWN_BMFF_BOXES =
        setOf("ftyp", "styp", "moov", "moof", "free", "skip", "wide", "mdat")

    /**
     * Checks if a file is an ISO BMFF Fragmented MP4 (fMP4) by inspecting top-level box headers. In
     * an fMP4 file, fragments are stored in "moof" boxes following "ftyp" and "moov".
     */
    fun isFragmentedMp4(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        try {
            RandomAccessFile(file, "r").use { raf ->
                var pos = 0L
                val len = raf.length()
                val typeBytes = ByteArray(4)
                var boxCount = 0
                while (pos + 8 <= len) {
                    raf.seek(pos)
                    val size32 = raf.readInt().toLong() and 0xFFFFFFFFL
                    raf.readFully(typeBytes)
                    val type = String(typeBytes, Charsets.US_ASCII)

                    if (type == "moof") {
                        return true
                    }

                    if (boxCount == 0 && type !in KNOWN_BMFF_BOXES) {
                        return false
                    }
                    boxCount++

                    val boxSize =
                        when (size32) {
                            1L -> {
                                if (pos + 16 > len) return false
                                val size64 = raf.readLong()
                                if (size64 < 16L) return false
                                size64
                            }
                            0L -> len - pos
                            in 2L..7L -> return false
                            else -> size32
                        }

                    if (boxSize <= 0 || pos > Long.MAX_VALUE - boxSize || pos + boxSize > len) {
                        break
                    }
                    pos += boxSize
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to inspect MP4 boxes in ${file.name}")
        }
        return false
    }

    /**
     * Remuxes an input media file (such as a fragmented MP4 with empty moov) into a standard
     * progressive MP4 using Android's native MediaExtractor and MediaMuxer.
     *
     * This stream-copies elementary packets (H.264/HEVC/AAC) without re-encoding, preserving
     * timestamps and keyframe flags, and generates a fully indexed moov atom with duration.
     * Supports cooperative coroutine cancellation and safe atomic file replacement.
     *
     * @return true if remuxing succeeded, false otherwise.
     */
    suspend fun remuxToStandardMp4(inputFile: File, outputFile: File): Boolean {
        if (!inputFile.exists() || inputFile.length() == 0L) return false

        val parentDir = outputFile.parentFile ?: outputFile.absoluteFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        if (parentDir != null && parentDir.exists()) {
            try {
                val stat = StatFs(parentDir.path)
                val availableBytes = stat.availableBytes
                if (availableBytes < inputFile.length()) {
                    Timber.e(
                        "Insufficient disk space to remux ${inputFile.name}: available=$availableBytes, required=${inputFile.length()}"
                    )
                    return false
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to check disk space via StatFs")
            }
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var success = false
        val tempFile = File(parentDir ?: File("."), "${outputFile.name}.part")

        try {
            if (tempFile.exists()) tempFile.delete()

            extractor.setDataSource(inputFile.absolutePath)
            val trackCount = extractor.trackCount
            if (trackCount == 0) {
                Timber.w("No tracks found in ${inputFile.name}")
                return false
            }

            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackMap = mutableMapOf<Int, Int>()
            var maxBufferSize = DEFAULT_BUFFER_SIZE

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    val muxerTrack =
                        try {
                            muxer.addTrack(format)
                        } catch (e: Exception) {
                            Timber.w(e, "Skipping unsupported track $i ($mime) for remuxing")
                            continue
                        }
                    trackMap[i] = muxerTrack
                    extractor.selectTrack(i)

                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        try {
                            val inputSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                            if (inputSize > maxBufferSize) {
                                maxBufferSize = inputSize
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            if (trackMap.isEmpty()) {
                Timber.w("No supported video or audio tracks found in ${inputFile.name}")
                return false
            }

            muxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            val lastPtsMap = mutableMapOf<Int, Long>()
            var samplesWritten = 0L

            while (true) {
                coroutineContext.ensureActive()

                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break

                val muxerTrack = trackMap[trackIndex]
                if (muxerTrack == null) {
                    extractor.advance()
                    continue
                }

                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize

                val lastPts = lastPtsMap[muxerTrack] ?: -1L
                val sampleTime = extractor.sampleTime.coerceAtLeast(0L)
                val pts = if (sampleTime <= lastPts) lastPts + 1L else sampleTime
                lastPtsMap[muxerTrack] = pts
                bufferInfo.presentationTimeUs = pts

                val sampleFlags = extractor.sampleFlags
                var bufferFlags = 0
                if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    bufferFlags = bufferFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                    bufferFlags = bufferFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                }
                bufferInfo.flags = bufferFlags

                muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                samplesWritten++

                if (samplesWritten % YIELD_INTERVAL_SAMPLES == 0L) {
                    yield()
                }

                extractor.advance()
            }

            if (samplesWritten == 0L) {
                Timber.w("No samples written during remux of ${inputFile.name}")
                return false
            }

            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null

            extractor.release()

            val moved =
                try {
                    Files.move(
                        tempFile.toPath(),
                        outputFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    true
                } catch (_: AtomicMoveNotSupportedException) {
                    try {
                        Files.move(
                            tempFile.toPath(),
                            outputFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                        true
                    } catch (e: Exception) {
                        Timber.w(e, "Files.move failed, falling back to renameTo")
                        false
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Atomic move failed, falling back to renameTo")
                    false
                }

            if (!moved) {
                val renamed = tempFile.renameTo(outputFile)
                if (!renamed) {
                    tempFile.copyTo(outputFile, overwrite = true)
                    tempFile.delete()
                }
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                Timber.w("Remux output file is missing or empty for ${outputFile.name}")
                return false
            }

            success = true
            Timber.i(
                "Successfully remuxed ${inputFile.name} to ${outputFile.name} ($samplesWritten samples)"
            )
            return true
        } catch (e: CancellationException) {
            Timber.d("Remuxing cancelled for ${inputFile.name}")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to remux ${inputFile.name} to ${outputFile.name}")
            return false
        } finally {
            if (muxerStarted) {
                try {
                    muxer?.stop()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to stop MediaMuxer during cleanup")
                }
            }
            try {
                muxer?.release()
            } catch (e: Exception) {
                Timber.w(e, "Failed to release MediaMuxer")
            }
            try {
                extractor.release()
            } catch (e: Exception) {
                Timber.w(e, "Failed to release MediaExtractor")
            }
            if (!success) {
                try {
                    if (tempFile.exists()) tempFile.delete()
                } catch (_: Exception) {}
                try {
                    if (
                        outputFile.exists() &&
                            outputFile.length() == 0L &&
                            outputFile.canonicalPath != inputFile.canonicalPath
                    ) {
                        outputFile.delete()
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
