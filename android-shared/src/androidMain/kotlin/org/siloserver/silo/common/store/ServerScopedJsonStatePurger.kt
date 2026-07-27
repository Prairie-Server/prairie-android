package org.siloserver.silo.common.store

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Cleans local JSON state whose only identity index is its directory name.
 *
 * Directory names are compared in their encoded storage form because long
 * identity values intentionally use a one-way digest and cannot be decoded.
 * Tree deletion never follows symlinks.
 */
class ServerScopedJsonStatePurger(baseDir: File) {
    private val canonicalBase = baseDir.canonicalFile
    private val roots = listOf(
        File(canonicalBase, "ebook_state"),
        File(canonicalBase, "audiobook_bookmarks"),
    )

    fun allowRegistered(serverIds: Set<String>) {
        roots.mapNotNull(::validatedRoot).forEach { root ->
            serverIds.forEach { serverId ->
                ServerScopedJsonAccess.allow(root, safePathSegment(serverId))
            }
        }
    }

    fun deleteAllForServer(serverId: String) {
        roots.mapNotNull(::validatedRoot).forEach { root ->
            val safeKey = safePathSegment(serverId)
            ServerScopedJsonAccess.invalidateAndRun(root, safeKey) {
                // Keep the leaf lexical: canonicalising it would follow an
                // in-root symlink to a registered sibling and delete that
                // sibling's real directory.
                deleteTree(File(root, safeKey))
            }
            legacyDirectChild(root, serverId)?.let { legacy ->
                if (legacy.name != safeKey) {
                    ServerScopedJsonAccess.invalidateAndRun(root, legacy.name) {
                        deleteTree(legacy)
                    }
                }
            }
        }
    }

    private fun legacyDirectChild(root: File, serverId: String): File? =
        serverId.takeIf {
            it != "." && it != ".." && !it.startsWith("~") &&
                '/' !in it && '\\' !in it
        }?.let { File(root, it) }

    fun deleteJsonOnlyOrphans(
        registeredServerIds: Set<String>,
        protectedServerIds: Set<String>,
    ) {
        val retainedIds = registeredServerIds + protectedServerIds
        roots.mapNotNull(::validatedRoot).forEach { root ->
            registeredServerIds.forEach { serverId ->
                ServerScopedJsonAccess.allow(root, safePathSegment(serverId))
            }
            val retainedTopLevelNames = retainedIds.flatMapTo(mutableSetOf()) { serverId ->
                buildList {
                    add(safePathSegment(serverId))
                    containedLegacyChild(root, serverId)?.let { legacy ->
                        val relative = root.toPath().relativize(legacy.toPath())
                        relative.firstOrNull()?.toString()?.let(::add)
                    }
                }
            }
            root.listFiles().orEmpty()
                .filterNot { it.name in retainedTopLevelNames }
                .forEach { orphan ->
                    ServerScopedJsonAccess.invalidateAndRun(root, orphan.name) {
                        deleteTree(orphan)
                    }
                }
        }
    }

    private fun validatedRoot(root: File): File? = runCatching {
        val path = root.toPath()
        if (Files.isSymbolicLink(path)) return@runCatching null
        if (Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
            !Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
        ) {
            return@runCatching null
        }
        root.canonicalFile.takeIf { canonical ->
            canonical.parentFile == canonicalBase &&
                canonical.path.startsWith(canonicalBase.path + File.separator)
        }
    }.getOrNull()

    private fun deleteTree(candidate: File) {
        val path = candidate.toPath()
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
                    // A dangling or unreadable symlink is still safe to remove as
                    // a directory entry; Files.delete does not follow it.
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }
}
