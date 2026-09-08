package dev.jdtech.jellyfin.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

object Mp4Remuxer {

    private const val DEFAULT_BUFFER_SIZE = 2 * 1024 * 1024 // 2 MB buffer

    /**
     * Checks if a file is an ISO BMFF Fragmented MP4 (fMP4) by inspecting top-level box headers.
     * In an fMP4 file, fragments are stored in "moof" boxes following "ftyp" and "moov".
     */
    fun isFragmentedMp4(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        try {
            RandomAccessFile(file, "r").use { raf ->
                var pos = 0L
                val len = raf.length()
                val typeBytes = ByteArray(4)
                while (pos + 8 <= len) {
                    raf.seek(pos)
                    val size32 = raf.readInt().toLong() and 0xFFFFFFFFL
                    raf.readFully(typeBytes)
                    val type = String(typeBytes, Charsets.US_ASCII)

                    if (type == "moof") {
                        return true
                    }

                    val boxSize = when (size32) {
                        1L -> {
                            if (pos + 16 > len) return false
                            raf.readLong()
                        }
                        0L -> len - pos
                        else -> size32
                    }

                    if (boxSize <= 0 || pos + boxSize > len) break
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
     *
     * @return true if remuxing succeeded, false otherwise.
     */
    fun remuxToStandardMp4(inputFile: File, outputFile: File): Boolean {
        if (!inputFile.exists() || inputFile.length() == 0L) return false

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")

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
                    val muxerTrack = muxer.addTrack(format)
                    trackMap[i] = muxerTrack
                    extractor.selectTrack(i)

                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val inputSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (inputSize > maxBufferSize) {
                            maxBufferSize = inputSize
                        }
                    }
                }
            }

            if (trackMap.isEmpty()) {
                Timber.w("No video or audio tracks found in ${inputFile.name}")
                return false
            }

            muxer.start()

            val buffer = ByteBuffer.allocateDirect(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var samplesWritten = 0L

            while (true) {
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
                bufferInfo.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                samplesWritten++
                extractor.advance()
            }

            if (samplesWritten == 0L) {
                Timber.w("No samples written during remux of ${inputFile.name}")
                return false
            }

            muxer.stop()
            muxer.release()
            muxer = null

            extractor.release()

            if (outputFile.exists()) outputFile.delete()
            val renamed = tempFile.renameTo(outputFile)
            if (!renamed) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }

            Timber.i("Successfully remuxed ${inputFile.name} to ${outputFile.name} ($samplesWritten samples)")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to remux ${inputFile.name} to ${outputFile.name}")
            tempFile.delete()
            if (outputFile.exists() && outputFile.length() == 0L) {
                outputFile.delete()
            }
            return false
        } finally {
            try {
                muxer?.release()
            } catch (_: Exception) {}
            try {
                extractor.release()
            } catch (_: Exception) {}
        }
    }
}
