package org.siloserver.silo.common.io

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class ContentLimitExceeded(
    val maxBytes: Long,
    limitName: String = "content",
) : IOException("$limitName exceeds the allowed limit of $maxBytes")

fun checkedLimitedByteCount(
    currentBytes: Long,
    additionalBytes: Long,
    maxBytes: Long,
    limitName: String = "content",
): Long {
    require(currentBytes >= 0) { "currentBytes must not be negative" }
    require(additionalBytes >= 0) { "additionalBytes must not be negative" }
    require(maxBytes >= 0) { "maxBytes must not be negative" }
    if (currentBytes > maxBytes || additionalBytes > maxBytes - currentBytes) {
        throw ContentLimitExceeded(maxBytes, limitName)
    }
    return currentBytes + additionalBytes
}

fun InputStream.copyToLimited(
    out: OutputStream,
    maxBytes: Long,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
): Long {
    require(maxBytes >= 0) { "maxBytes must not be negative" }
    require(bufferSize > 0) { "bufferSize must be positive" }
    var total = 0L
    val buffer = ByteArray(bufferSize)
    while (true) {
        val read = read(buffer)
        if (read < 0) return total
        if (read == 0) {
            val byte = read()
            if (byte < 0) return total
            total = checkedLimitedByteCount(total, 1, maxBytes)
            out.write(byte)
            continue
        }
        total = checkedLimitedByteCount(total, read.toLong(), maxBytes)
        out.write(buffer, 0, read)
    }
}
