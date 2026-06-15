package com.continuum.app.common.downloads

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.continuum.app.model.download.DownloadMediaType
import com.continuum.app.model.download.DownloadSidecar
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.net.URI

/**
 * Storage coordinator for downloads. Sidecars live under private [baseDir];
 * downloaded media bytes live in [publicStore] so other Android apps can
 * discover/open the original files.
 */
class DownloadStorage(
    private val baseDir: File,
    private val publicStore: PublicDownloadStore = FileBackedPublicDownloadStore(File(baseDir, "public-downloads")),
) {

    constructor(context: Context) : this(
        baseDir = context.filesDir,
        publicStore = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStorePublicDownloadStore(context.applicationContext)
        } else {
            FileBackedPublicDownloadStore(
                collectionRoots = legacyPublicCollectionRoots(),
                onFileCompleted = { file ->
                    MediaScannerConnection.scanFile(
                        context.applicationContext,
                        arrayOf(file.absolutePath),
                        null,
                        null,
                    )
                },
            )
        },
    )

    private val downloadsRoot: File get() = File(baseDir, DOWNLOADS_DIR_NAME)

    fun locateLocalMedia(serverId: String, profileId: String, fileId: Int): DownloadLocation? {
        readSidecar(serverId, profileId, fileId)?.localUri?.let { uri ->
            publicStore.locate(uri)?.let { return it }
        }
        return publicStore.locateByFileId(serverId, profileId, fileId)
    }

    fun locateLocalFile(serverId: String, profileId: String, fileId: Int): File? =
        (locateLocalMedia(serverId, profileId, fileId) as? FileDownloadLocation)?.file

    /**
     * Ensures the parent directory exists and returns the target file.
     * Caller is responsible for the actual write (typically via the
     * DownloadWorker's streaming copy).
     */
    fun prepareWrite(
        serverId: String,
        profileId: String,
        fileId: Int,
        fileName: String? = null,
        container: String? = null,
        mediaType: String? = null,
    ): DownloadTarget {
        publicStore.delete(serverId, profileId, fileId, readSidecar(serverId, profileId, fileId)?.localUri)
        return publicStore.create(
            serverId = serverId,
            profileId = profileId,
            fileId = fileId,
            displayName = localMediaFileName(fileId, fileName, container),
            container = container,
            mediaType = mediaType,
        )
    }

    fun exists(serverId: String, profileId: String, fileId: Int): Boolean =
        locateLocalMedia(serverId, profileId, fileId) != null

    /**
     * Deletes the local file for this triple. Returns true if a file was
     * actually removed (false if nothing existed to delete). Empty parent
     * directories are left in place; cleanup at higher granularity goes
     * through [deleteAllForProfile] or [deleteAllForServer].
     */
    fun delete(serverId: String, profileId: String, fileId: Int): Boolean =
        publicStore.delete(serverId, profileId, fileId, readSidecar(serverId, profileId, fileId)?.localUri)

    fun completeWrite(uriString: String) {
        publicStore.complete(uriString)
    }

    fun totalBytesUsed(serverId: String, profileId: String): Long =
        publicStore.totalBytesUsed(serverId, profileId)

    fun deleteUri(uriString: String): Boolean =
        publicStore.delete("", "", -1, uriString)

    /** Wipes every download under (serverId, profileId). Used on sign-out
     *  and profile switch. */
    fun deleteAllForProfile(serverId: String, profileId: String): Boolean {
        val dir = File(downloadsRoot, "$serverId/$profileId")
        val sidecarsDeleted = if (dir.exists()) dir.deleteRecursively() else false
        return publicStore.deleteAllForProfile(serverId, profileId) || sidecarsDeleted
    }

    /** Wipes every download under (serverId). Used on server delete /
     *  re-bind. */
    fun deleteAllForServer(serverId: String): Boolean {
        val dir = File(downloadsRoot, serverId)
        val sidecarsDeleted = if (dir.exists()) dir.deleteRecursively() else false
        return publicStore.deleteAllForServer(serverId) || sidecarsDeleted
    }

    /** Sum of bytes across every downloaded file under this storage. */
    fun totalBytesUsed(): Long = publicStore.totalBytesUsed()

    /** Reports the filesystem's remaining usable space for the base dir,
     *  useful for the Downloads header storage card. */
    fun usableSpaceBytes(): Long = baseDir.usableSpace

    // ── Sidecar (.record.json) primitives ──────────────────────────────
    //
    // Disk-as-truth design (v1.1): the sidecar is the complete picture of
    // every download the user owns. Written at enqueue time, rewritten on
    // every worker status transition, read at app start to seed the
    // in-memory cache before any server call. Without it, an offline boot
    // shows an empty Downloads tab even when bytes exist on disk.

    /** Path of the sidecar JSON for `(serverId, profileId, fileId)`. */
    fun sidecarFile(serverId: String, profileId: String, fileId: Int): File =
        File(downloadsRoot, "$serverId/$profileId/$fileId.record.json")

    /** Writes the sidecar atomically (tmp + rename). Creates parent dirs. */
    fun writeSidecar(serverId: String, profileId: String, sidecar: DownloadSidecar) {
        val target = sidecarFile(serverId, profileId, sidecar.record.mediaFileId)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(JSON.encodeToString(sidecar))
        tmp.renameTo(target)
    }

    /** Reads the sidecar, or null if missing or malformed. */
    fun readSidecar(serverId: String, profileId: String, fileId: Int): DownloadSidecar? {
        val file = sidecarFile(serverId, profileId, fileId)
        if (!file.isFile) return null
        return runCatching { JSON.decodeFromString<DownloadSidecar>(file.readText()) }.getOrNull()
    }

    /** Deletes the sidecar. Returns true if a file was removed. */
    fun deleteSidecar(serverId: String, profileId: String, fileId: Int): Boolean =
        sidecarFile(serverId, profileId, fileId).let { if (it.exists()) it.delete() else false }

    /**
     * Walks every sidecar under the downloads tree (across all servers /
     * profiles) and returns the parsed contents. Silently skips files that
     * fail to decode (forward-compat with future sidecar shape changes).
     */
    fun listAllSidecars(): List<DownloadSidecar> =
        listAllSidecarsWithScope().map { it.third }

    fun listSidecars(serverId: String, profileId: String): List<DownloadSidecar> {
        val dir = File(downloadsRoot, "$serverId/$profileId")
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".record.json") }
            .mapNotNull { file ->
                runCatching { JSON.decodeFromString<DownloadSidecar>(file.readText()) }.getOrNull()
            }
            .toList()
    }

    /**
     * Like [listAllSidecars] but preserves the `(serverId, profileId)`
     * scope derived from each sidecar's path
     * (`<downloadsRoot>/<serverId>/<profileId>/<fileId>.record.json`).
     * One walk serves callers that need both the sidecar and where it
     * lives, instead of a per-fileId [locateSidecarByFileId] re-walk.
     */
    fun listAllSidecarsWithScope(): List<Triple<String, String, DownloadSidecar>> {
        val root = downloadsRoot
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".record.json") }
            .mapNotNull { file ->
                val sidecar = runCatching { JSON.decodeFromString<DownloadSidecar>(file.readText()) }.getOrNull()
                    ?: return@mapNotNull null
                val profileDir = file.parentFile ?: return@mapNotNull null
                val serverDir = profileDir.parentFile ?: return@mapNotNull null
                Triple(serverDir.name, profileDir.name, sidecar)
            }
            .toList()
    }

    /**
     * Finds the sidecar (across all server/profile combinations under this
     * storage) whose `record.contentId` matches. Used by the player's
     * offline path to locate the local file from a contentId without
     * touching the network. Returns the first match.
     */
    fun findSidecarByContentId(contentId: String): DownloadSidecar? =
        listAllSidecars().firstOrNull { it.record.contentId == contentId }

    /**
     * Returns the `(serverId, profileId)` tuple for the sidecar whose
     * fileId matches, or null if not found. Used by the delete path to
     * unconditionally clean up the sidecar even when the in-memory record
     * is gone.
     */
    fun locateSidecarByFileId(fileId: Int): Triple<String, String, DownloadSidecar>? {
        val root = downloadsRoot
        if (!root.exists()) return null
        val target = "$fileId.record.json"
        root.walkTopDown().forEach { f ->
            if (f.isFile && f.name == target) {
                val sidecar = runCatching { JSON.decodeFromString<DownloadSidecar>(f.readText()) }.getOrNull()
                if (sidecar != null) {
                    // path: <downloadsRoot>/<serverId>/<profileId>/<fileId>.record.json
                    val profileDir = f.parentFile ?: return null
                    val serverDir = profileDir.parentFile ?: return null
                    return Triple(serverDir.name, profileDir.name, sidecar)
                }
            }
        }
        return null
    }

    fun locateSidecarByFileId(serverId: String, profileId: String, fileId: Int): Triple<String, String, DownloadSidecar>? {
        val sidecar = readSidecar(serverId, profileId, fileId) ?: return null
        return Triple(serverId, profileId, sidecar)
    }

    private fun localMediaFileName(fileId: Int, fileName: String?, container: String?): String {
        val originalName = fileName
            ?.replace('\\', '/')
            ?.substringAfterLast('/')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::sanitizeBasename)
            ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
        if (originalName != null) return originalName

        val extension = container
            ?.trim()
            ?.lowercase()
            ?.removePrefix(".")
            ?.replace(Regex("[^a-z0-9]"), "")
            ?.takeIf { it.isNotBlank() }
            ?: "download"
        return "$fileId.$extension"
    }

    private fun sanitizeBasename(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|]"), "_")

    companion object {
        const val DOWNLOADS_DIR_NAME = "downloads"
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}

data class DownloadTarget(
    val uriString: String,
    val displayName: String,
    val openOutputStream: () -> OutputStream,
    val sizeBytes: () -> Long,
)

open class DownloadLocation(
    val uriString: String,
    val displayName: String,
    val sizeBytes: Long,
)

class FileDownloadLocation(
    uriString: String,
    displayName: String,
    sizeBytes: Long,
    val file: File,
) : DownloadLocation(uriString, displayName, sizeBytes)

interface PublicDownloadStore {
    fun create(
        serverId: String,
        profileId: String,
        fileId: Int,
        displayName: String,
        container: String?,
        mediaType: String?,
    ): DownloadTarget
    fun locate(uriString: String): DownloadLocation?
    fun locateByFileId(serverId: String, profileId: String, fileId: Int): DownloadLocation?
    fun delete(serverId: String, profileId: String, fileId: Int, uriString: String?): Boolean
    fun complete(uriString: String) = Unit
    fun deleteAllForProfile(serverId: String, profileId: String): Boolean
    fun deleteAllForServer(serverId: String): Boolean
    fun totalBytesUsed(): Long
    fun totalBytesUsed(serverId: String, profileId: String): Long
}

class FileBackedPublicDownloadStore(
    root: File? = null,
    collectionRoots: Map<PublicDownloadCollection, File>? = null,
    private val onFileCompleted: (File) -> Unit = {},
) : PublicDownloadStore {
    private val rootsByCollection: Map<PublicDownloadCollection, File> =
        collectionRoots ?: PublicDownloadCollection.entries.associateWith { collection ->
            File(requireNotNull(root) { "root or collectionRoots is required" }, collection.directoryName)
        }
    private val roots: List<File> = rootsByCollection.values.distinctBy { file ->
        runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
    }

    override fun create(
        serverId: String,
        profileId: String,
        fileId: Int,
        displayName: String,
        container: String?,
        mediaType: String?,
    ): DownloadTarget {
        val collection = PublicDownloadCollection.forMediaType(mediaType)
        val dir = File(collectionRoot(collection), "$serverId/$profileId/$fileId").apply {
            deleteRecursively()
            mkdirs()
        }
        val file = File(dir, displayName)
        return DownloadTarget(
            uriString = fileUriString(file),
            displayName = displayName,
            openOutputStream = { file.outputStream() },
            sizeBytes = { file.length() },
        )
    }

    override fun locate(uriString: String): DownloadLocation? {
        if (!uriString.startsWith("file:", ignoreCase = true)) return null
        val file = runCatching { File(URI(uriString)) }.getOrElse {
            File(uriString.removePrefix("file://").removePrefix("file:"))
        }
        if (!file.isFile) return null
        return FileDownloadLocation(uriString, file.name, file.length(), file)
    }

    override fun locateByFileId(serverId: String, profileId: String, fileId: Int): DownloadLocation? {
        PublicDownloadCollection.entries.forEach { collection ->
            val dir = File(collectionRoot(collection), "$serverId/$profileId/$fileId")
            val file = dir.listFiles { f -> f.isFile }?.sortedBy { it.name }?.firstOrNull()
            if (file != null) {
                return FileDownloadLocation(fileUriString(file), file.name, file.length(), file)
            }
        }
        return null
    }

    override fun delete(serverId: String, profileId: String, fileId: Int, uriString: String?): Boolean {
        val fromUri = uriString?.let { locate(it) as? FileDownloadLocation }?.file
        if (fromUri != null) return fromUri.delete()
        return PublicDownloadCollection.entries.any { collection ->
            val target = File(collectionRoot(collection), "$serverId/$profileId/$fileId")
            target.exists() && target.deleteRecursively()
        }
    }

    override fun complete(uriString: String) {
        val file = (locate(uriString) as? FileDownloadLocation)?.file ?: return
        onFileCompleted(file)
    }

    override fun deleteAllForProfile(serverId: String, profileId: String): Boolean {
        var deleted = false
        PublicDownloadCollection.entries.forEach { collection ->
            val target = File(collectionRoot(collection), "$serverId/$profileId")
            deleted = (target.exists() && target.deleteRecursively()) || deleted
        }
        return deleted
    }

    override fun deleteAllForServer(serverId: String): Boolean {
        var deleted = false
        PublicDownloadCollection.entries.forEach { collection ->
            val target = File(collectionRoot(collection), serverId)
            deleted = (target.exists() && target.deleteRecursively()) || deleted
        }
        return deleted
    }

    override fun totalBytesUsed(): Long {
        var total = 0L
        roots.forEach { root ->
            if (root.exists()) root.walkTopDown().forEach { if (it.isFile) total += it.length() }
        }
        return total
    }

    override fun totalBytesUsed(serverId: String, profileId: String): Long {
        var total = 0L
        PublicDownloadCollection.entries.forEach { collection ->
            val dir = File(collectionRoot(collection), "$serverId/$profileId")
            if (dir.exists()) dir.walkTopDown().forEach { if (it.isFile) total += it.length() }
        }
        return total
    }

    private fun collectionRoot(collection: PublicDownloadCollection): File =
        rootsByCollection[collection] ?: error("No public download root for $collection")

    private fun fileUriString(file: File): String =
        URI("file", "", file.absolutePath, null).toASCIIString()
}

class MediaStorePublicDownloadStore(context: Context) : PublicDownloadStore {
    private val resolver: ContentResolver = context.contentResolver

    override fun create(
        serverId: String,
        profileId: String,
        fileId: Int,
        displayName: String,
        container: String?,
        mediaType: String?,
    ): DownloadTarget {
        delete(serverId, profileId, fileId, uriString = null)
        val collection = PublicDownloadCollection.forMediaType(mediaType)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeForDownloadName(displayName, container))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, mediaStoreRelativePath(collection, serverId, profileId, fileId))
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection.contentUri(), values) ?: error("Could not create MediaStore download")
        return DownloadTarget(
            uriString = uri.toString(),
            displayName = displayName,
            openOutputStream = { resolver.openOutputStream(uri, "w") ?: error("Could not open $uri") },
            sizeBytes = { locate(uri.toString())?.sizeBytes ?: 0L },
        )
    }

    override fun locate(uriString: String): DownloadLocation? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val name = cursor.getString(0) ?: uri.lastPathSegment.orEmpty()
            val size = cursor.getLong(1)
            return DownloadLocation(uri.toString(), name, size)
        }
        return null
    }

    override fun locateByFileId(serverId: String, profileId: String, fileId: Int): DownloadLocation? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        PublicDownloadCollection.entries.forEach { collection ->
            resolver.query(
                collection.contentUri(),
                projection,
                selection,
                arrayOf(mediaStoreRelativePath(collection, serverId, profileId, fileId)),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val name = cursor.getString(1) ?: fileId.toString()
                    val size = cursor.getLong(2)
                    val uri = ContentUris.withAppendedId(collection.contentUri(), id)
                    return DownloadLocation(uri.toString(), name, size)
                }
            }
        }
        return null
    }

    override fun delete(serverId: String, profileId: String, fileId: Int, uriString: String?): Boolean {
        uriString?.let { uri ->
            return runCatching { resolver.delete(Uri.parse(uri), null, null) > 0 }.getOrDefault(false)
        }
        return false
    }

    override fun deleteAllForProfile(serverId: String, profileId: String): Boolean =
        deleteAcrossCollections { collection ->
            deleteByRelativePath(collection, "${collection.relativeRoot}/Silo/$serverId/$profileId/%")
        }

    override fun deleteAllForServer(serverId: String): Boolean =
        deleteAcrossCollections { collection ->
            deleteByRelativePath(collection, "${collection.relativeRoot}/Silo/$serverId/%")
        }

    override fun totalBytesUsed(): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0L
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        var total = 0L
        PublicDownloadCollection.entries.forEach { collection ->
            resolver.query(collection.contentUri(), projection, selection, arrayOf("${collection.relativeRoot}/Silo/%"), null)?.use { cursor ->
                while (cursor.moveToNext()) total += cursor.getLong(0)
            }
        }
        return total
    }

    override fun totalBytesUsed(serverId: String, profileId: String): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0L
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        var total = 0L
        PublicDownloadCollection.entries.forEach { collection ->
            resolver.query(
                collection.contentUri(),
                projection,
                selection,
                arrayOf("${collection.relativeRoot}/Silo/$serverId/$profileId/%"),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) total += cursor.getLong(0)
            }
        }
        return total
    }

    override fun complete(uriString: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        resolver.update(Uri.parse(uriString), values, null, null)
    }

    private fun deleteByRelativePath(collection: PublicDownloadCollection, pattern: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        return resolver.delete(collection.contentUri(), selection, arrayOf(pattern)) > 0
    }

    private fun deleteAcrossCollections(block: (PublicDownloadCollection) -> Boolean): Boolean {
        var deleted = false
        PublicDownloadCollection.entries.forEach { collection ->
            deleted = block(collection) || deleted
        }
        return deleted
    }
}

fun mediaStoreRelativePath(
    collection: PublicDownloadCollection,
    serverId: String,
    profileId: String,
    fileId: Int,
): String = "${collection.relativeRoot}/Silo/$serverId/$profileId/$fileId/"

fun legacyPublicCollectionRoots(): Map<PublicDownloadCollection, File> =
    mapOf(
        PublicDownloadCollection.Downloads to File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Silo",
        ),
        PublicDownloadCollection.Video to File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "Silo",
        ),
        PublicDownloadCollection.Audio to File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Silo",
        ),
    )

enum class PublicDownloadCollection(val directoryName: String, val relativeRoot: String) {
    Video("Movies", "Movies"),
    Audio("Music", "Music"),
    Downloads("Downloads", "Downloads"),
    ;

    fun contentUri(): Uri = when (this) {
        Video -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        Audio -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        Downloads -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }

    companion object {
        fun forMediaType(mediaType: String?): PublicDownloadCollection =
            when (DownloadMediaType.fromWire(mediaType)) {
                DownloadMediaType.Movie, DownloadMediaType.TvShow -> Video
                DownloadMediaType.Audiobook -> Audio
                DownloadMediaType.Ebook, DownloadMediaType.Unknown -> Downloads
            }
    }
}
