package org.siloserver.silo.android.ui.screens.player

internal data class MobilePlayerLoadOwner(
    val generation: Long,
    val contentId: String,
    val preferredFileId: Int?,
    val preferredQuality: String?,
)

internal class MobilePlayerLoadOwnerRegistry {
    private var generation = 0L
    private var current: MobilePlayerLoadOwner? = null

    @Synchronized
    fun begin(
        contentId: String,
        preferredFileId: Int?,
        preferredQuality: String?,
    ): MobilePlayerLoadOwner = MobilePlayerLoadOwner(
        generation = ++generation,
        contentId = contentId,
        preferredFileId = preferredFileId,
        preferredQuality = preferredQuality,
    ).also { current = it }

    @Synchronized
    fun owns(owner: MobilePlayerLoadOwner): Boolean = current == owner

    @Synchronized
    fun runIfOwned(owner: MobilePlayerLoadOwner, action: () -> Unit): Boolean {
        if (current != owner) return false
        action()
        return true
    }

    @Synchronized
    fun invalidate() {
        generation++
        current = null
    }
}
