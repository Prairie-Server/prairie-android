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
    private val roots = listOf(
        File(baseDir, "ebook_state").canonicalFile,
        File(baseDir, "audiobook_bookmarks").canonicalFile,
    )

    fun deleteAllForServer(serverId: String) {
        roots.forEach { root ->
            containedSafeChild(root, serverId)?.let(::deleteTree)
            containedLegacyChild(root, serverId)
                ?.takeIf { it.path != containedSafeChild(root, serverId)?.path }
                ?.let(::deleteTree)
        }
    }

    fun deleteJsonOnlyOrphans(
        registeredServerIds: Set<String>,
        protectedServerIds: Set<String>,
    ) {
        val retainedIds = registeredServerIds + protectedServerIds
        roots.forEach { root ->
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
                .forEach(::deleteTree)
        }
    }

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
