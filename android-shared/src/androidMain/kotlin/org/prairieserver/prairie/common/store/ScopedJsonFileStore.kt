package org.prairieserver.prairie.common.store

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * Shared plumbing for the per-(serverId, profileId, contentId) JSON
 * file stores (ebook reading state, audiobook positions/bookmarks):
 * scoped path resolution under a root directory, one lenient [Json]
 * instance, safe reads (missing/corrupt files log and return null),
 * and atomic writes (tmp + fsync + rename) so a crash mid-write can't
 * leave a half-written file that fails to decode next launch.
 */
internal class ScopedJsonFileStore(
    private val root: File,
    internal val tag: String,
) {

    internal fun resolve(relativePath: String): File = File(root, relativePath)

    internal fun fileFor(
        serverId: String,
        profileId: String,
        contentId: String,
        suffix: String = ".json",
    ): File = resolve("$serverId/$profileId/$contentId$suffix")

    internal inline fun <reified T> read(file: File): T? {
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<T>(file.readText()) }
            .onFailure { Log.w(tag, "read failed for ${file.path}", it) }
            .getOrNull()
    }

    internal inline fun <reified T> write(target: File, value: T) {
        writeAtomic(target, json.encodeToString(value))
    }

    internal fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(tmp).use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            Log.w(tag, "atomic rename failed for ${target.path}")
        }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    }
}
