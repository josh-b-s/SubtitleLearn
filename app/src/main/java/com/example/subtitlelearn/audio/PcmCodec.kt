package com.example.subtitlelearn.audio

/** Converts between 16-bit PCM `ShortArray` and little-endian `ByteArray`, for on-disk clip storage. */
object PcmCodec {
    fun shortsToBytes(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            bytes[i * 2] = (s.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (s.toInt() shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    fun bytesToShorts(bytes: ByteArray): ShortArray =
        ShortArray(bytes.size / 2) { i ->
            ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort()
        }
}
