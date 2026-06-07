package com.cashierapp.photocheckout.domain.model

/**
 * A captured (and typically downscaled) counter photo handed to the [Recognizer].
 * Pure data — the bytes are an already-encoded image (e.g. JPEG); no Android types.
 */
public data class CapturedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val mimeType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapturedImage) return false
        return width == other.width &&
            height == other.height &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
