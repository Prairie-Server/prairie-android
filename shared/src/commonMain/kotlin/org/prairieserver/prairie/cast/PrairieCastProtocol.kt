package org.prairieserver.prairie.cast

object PrairieCastProtocol {
    const val version: Int = 2
    val supportedVersions: List<Int> = listOf(version)
    const val serviceType: String = "_prairiecast._tcp"

    fun negotiatedVersion(peerVersions: List<Int>): Int? =
        supportedVersions.filter(peerVersions::contains).maxOrNull()
}
