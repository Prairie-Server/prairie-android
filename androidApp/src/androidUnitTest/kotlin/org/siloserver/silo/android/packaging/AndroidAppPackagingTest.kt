package org.siloserver.silo.android.packaging

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AndroidAppPackagingTest {
    @Test
    fun excludesDuplicateMultiReleaseOsgiManifest() {
        val buildFile = File("build.gradle.kts").readText()

        assertTrue(
            buildFile.contains("excludes += \"/META-INF/versions/9/OSGI-INF/MANIFEST.MF\""),
            "androidApp should mirror androidTvApp's packaging exclusion for duplicate BouncyCastle/jspecify multi-release OSGi manifests.",
        )
    }
}
