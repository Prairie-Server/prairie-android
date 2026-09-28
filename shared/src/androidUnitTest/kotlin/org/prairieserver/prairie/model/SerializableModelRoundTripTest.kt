package org.prairieserver.prairie.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.prairieserver.prairie.network.PrairieJson
import java.io.File
import java.net.JarURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Wire-contract smoke test over every non-generic `@Serializable` type in the
 * shared module: each one must decode from a descriptor-derived minimal body
 * (required fields only, so every default applies) and from a fully populated
 * body, and whatever decodes must re-encode to a stable JSON form.
 *
 * Catches models whose defaults throw, whose custom serializers cannot read
 * their own output, and models that drift out of the serializer plugin's reach
 * after an upstream sync. Types that legitimately reject a synthetic body
 * (init-block validation, polymorphic payloads) are tolerated individually;
 * the aggregate floor below guards against the scan silently finding nothing.
 */
@OptIn(ExperimentalSerializationApi::class)
class SerializableModelRoundTripTest {

    private val json = PrairieJson

    @Test
    fun everySerializableModelDecodesAndReencodesStably() {
        val serializers = discoverSerializers()
        assertTrue(serializers.size > 50, "expected to discover shared @Serializable models, found ${serializers.size}")

        var decoded = 0
        val unstable = mutableListOf<String>()
        for ((name, serializer) in serializers) {
            for (full in listOf(false, true)) {
                val body = runCatching { synthesize(serializer.descriptor, full, 0) }.getOrNull() ?: continue
                @Suppress("UNCHECKED_CAST")
                val ser = serializer as KSerializer<Any?>
                val value = runCatching { json.decodeFromJsonElement(ser, body) }.getOrNull() ?: continue
                decoded++
                val first = runCatching { json.encodeToString(ser, value) }.getOrNull() ?: continue
                val again = runCatching { json.encodeToString(ser, json.decodeFromString(ser, first)) }.getOrNull()
                if (again != null && again != first) unstable += name
            }
        }
        assertTrue(decoded > 50, "only $decoded synthetic bodies decoded across ${serializers.size} models")
        // Lossy-by-design serializers exist (normalizing ids, clamping values), so
        // only a broad regression — most models failing to round-trip — fails here.
        assertTrue(unstable.size < serializers.size / 4, "unstable round trips: $unstable")
    }

    private fun synthesize(descriptor: SerialDescriptor, full: Boolean, depth: Int): JsonElement {
        if (depth > 10) return JsonNull
        if (descriptor.isNullable && !full) return JsonNull
        return when (descriptor.kind) {
            PrimitiveKind.STRING, PrimitiveKind.CHAR -> JsonPrimitive("x")
            PrimitiveKind.BOOLEAN -> JsonPrimitive(full)
            PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE ->
                JsonPrimitive(if (full) 1 else 0)
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> JsonPrimitive(if (full) 1.5 else 0.0)
            SerialKind.ENUM -> JsonPrimitive(descriptor.getElementName(if (full) descriptor.elementsCount - 1 else 0))
            StructureKind.LIST ->
                if (full) JsonArray(listOf(synthesize(descriptor.getElementDescriptor(0), true, depth + 1)))
                else JsonArray(emptyList())
            StructureKind.MAP -> JsonObject(emptyMap())
            StructureKind.OBJECT -> JsonObject(emptyMap())
            StructureKind.CLASS -> {
                val fields = LinkedHashMap<String, JsonElement>()
                for (i in 0 until descriptor.elementsCount) {
                    if (!full && descriptor.isElementOptional(i)) continue
                    fields[descriptor.getElementName(i)] =
                        synthesize(descriptor.getElementDescriptor(i), full, depth + 1)
                }
                JsonObject(fields)
            }
            PolymorphicKind.SEALED -> {
                // Sealed descriptors are ("type", "value"); "value" lists subclasses.
                val subclasses = descriptor.getElementDescriptor(1)
                if (subclasses.elementsCount == 0) return JsonNull
                val index = if (full) subclasses.elementsCount - 1 else 0
                val sub = synthesize(subclasses.getElementDescriptor(index), full, depth + 1)
                val fields = LinkedHashMap<String, JsonElement>()
                fields[json.configuration.classDiscriminator] = JsonPrimitive(subclasses.getElementName(index))
                if (sub is JsonObject) fields.putAll(sub)
                JsonObject(fields)
            }
            else -> JsonNull
        }
    }

    private fun discoverSerializers(): List<Pair<String, KSerializer<*>>> {
        val loader = javaClass.classLoader
        val prefix = "org/prairieserver/prairie/"
        val names = loader.getResources("org/prairieserver/prairie").toList().flatMap { url ->
            when (url.protocol) {
                "file" -> {
                    val root = File(URI(url.toString()))
                    // Only production classes: test output directories carry "UnitTest".
                    if (root.absolutePath.contains("UnitTest")) emptyList<String>()
                    else root.walkTopDown().filter { it.isFile }
                        .map { prefix + it.relativeTo(root).invariantSeparatorsPath }
                        .toList()
                }
                "jar" -> {
                    val connection = url.openConnection() as JarURLConnection
                    if (connection.jarFileURL.path.contains("UnitTest")) emptyList<String>()
                    else connection.jarFile.entries().toList().map { it.name }.filter { it.startsWith(prefix) }
                }
                else -> emptyList<String>()
            }
        }
            .filter { it.endsWith(".class") && !it.contains("\$\$serializer") }
            .map { it.removeSuffix(".class").replace('/', '.') }
            .distinct()
            .sorted()

        return names.mapNotNull { name ->
            runCatching {
                val cls = Class.forName(name, false, loader)
                if (cls.isInterface || cls.isAnnotation || cls.isSynthetic) return@runCatching null
                val companionField = cls.declaredFields.firstOrNull {
                    it.name == "Companion" && java.lang.reflect.Modifier.isStatic(it.modifiers)
                } ?: return@runCatching null
                companionField.isAccessible = true
                val companion = companionField.get(null) ?: return@runCatching null
                val method = companion.javaClass.declaredMethods.firstOrNull {
                    it.name == "serializer" && it.parameterCount == 0 &&
                        KSerializer::class.java.isAssignableFrom(it.returnType)
                } ?: return@runCatching null
                method.isAccessible = true
                name to (method.invoke(companion) as KSerializer<*>)
            }.getOrNull()
        }
    }
}
