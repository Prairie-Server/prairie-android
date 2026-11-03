package org.prairieserver.prairie.common.lan

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Level `sealed` against the vectors prairie-roku shares in
 * `tests/control/sealed.spec.bs` (generated with Node's crypto, as its
 * `scripts/prairiecontrol-phone.mjs` implements the phone's side), recomputed
 * for the `prairie-cast-sealed-1` label (upstream uses `silo-cast-sealed-1`).
 */
class PrairieCastSealedTest {
    private val key = hex("000102030405060708090a0b0c0d0e0f")
    private val phoneNonce = hex("101112131415161718191a1b1c1d1e1f")
    private val tvNonce = hex("202122232425262728292a2b2c2d2e2f")
    private val keys = PrairieCastSealed.sessionKeys(key, phoneNonce, tvNonce)

    @Test
    fun namesTheKeyAndDerivesTheSessionKeys() {
        assertEquals("be45cb26", PrairieCastSealed.keyId(key))
        assertEquals("de4408ef0430df2420f995f133eefda8", keys.encKey.toHex())
        assertEquals("893dad33d613cc5e388b578ff53a9b8f27e9d3af04088f907b5690c5026d8e86", keys.macKey.toHex())
        assertEquals("3f808d9779b57fb50f67a59dac88e257", keys.ivKey.toHex())
    }

    @Test
    fun readsAndWritesThePairingKeyInBase32() {
        assertEquals("AAAQEAYEAUDAOCAJBIFQYDIOB4", PrairieCastSealed.base32(key))
        assertContentEquals(key, PrairieCastSealed.parseKey("AAAQEAYEAUDAOCAJBIFQYDIOB4"))
        // As the Roku shows it: groups of four.
        assertContentEquals(key, PrairieCastSealed.parseKey("aaaq eaye auda ocaj bifq ydio b4"))
        assertContentEquals(key, PrairieCastSealed.parseKey("000102030405060708090a0b0c0d0e0f"))
        assertNull(PrairieCastSealed.parseKey("AAAQEAYEAUDAOCAJBIFQYDIO"))
        assertNull(PrairieCastSealed.parseKey("AAAQEAYEAUDAOCAJBIFQYDIOB41"))
        assertNull(PrairieCastSealed.parseKey("AAAQEAYEAUDAOCAJBIFQYDIOB!"))
    }

    @Test
    fun readsAPeersSealedHello() {
        val read = assertNotNull(
            PrairieCastSealed.readHello("""{"type":"sealed_hello","v":1,"keyId":"BE45CB26","nonce":"EBESExQVFhcYGRobHB0eHw"}"""),
        )
        assertEquals("be45cb26", read.keyId)
        assertContentEquals(phoneNonce, read.nonce)
        assertNull(PrairieCastSealed.readHello("""{"type":"sealed_hello","v":1,"keyId":"x","nonce":"AAAA"}"""))
        assertNull(PrairieCastSealed.readHello("""{"type":"hello"}"""))
        assertNull(PrairieCastSealed.readHello("""{"type":"close","v":2,"reason":"unauthorized"}"""))
        assertNull(PrairieCastSealed.readHello("not json"))

        val hello = assertNotNull(PrairieCastSealed.readHello(PrairieCastSealed.helloText("be45cb26", phoneNonce)))
        assertEquals("be45cb26", hello.keyId)
        assertContentEquals(phoneNonce, hello.nonce)
        assert("\"nonce\":\"EBESExQVFhcYGRobHB0eHw\"" in PrairieCastSealed.helloText("be45cb26", phoneNonce))
    }

    @Test
    fun sealsAsTheRokuDoesGivenItsIv() {
        val expected = "000000000000000013fce4d6c5b63c32b97fba7675d53d9788628415eaf2cb0567532f30848c9445a1dfa511062b99cd39113d2ddff1fd54c17f0b4c3483f8dc5f2b2d6919230030f385b516c33d1ebbc341a3e6375fbba7"
        // The Roku's IV: AES-128-ECB(ivKey, direction || seq, zero-padded).
        val iv = Cipher.getInstance("AES/ECB/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.ivKey, "AES"))
            doFinal(ByteArray(16).also { it[0] = PrairieCastSealed.FROM_TV })
        }
        assertEquals(expected.substring(16, 48), iv.toHex())
        val body = PrairieCastSealed.seal(keys, PrairieCastSealed.FROM_TV, 0, """{"type":"ping","v":2}""".encodeToByteArray(), iv)
        assertEquals(expected, body.toHex())
        assertEquals("""{"type":"ping","v":2}""", PrairieCastSealed.open(keys, PrairieCastSealed.FROM_TV, 0, body)?.decodeToString())
    }

    @Test
    fun opensAPhoneFrameWithItsOwnIv() {
        val body = hex(
            "0000000000000001ffeeddccbbaa998877665544332211001e5a6529eac949e50135295d2a2db70480347b2340c8493d25ac3f86bac2b586dc5c559e11e77ec302821eb802b3273d530a9e835850c3a4d1702602e6bc20f5",
        )
        assertEquals("""{"type":"pong","v":2}""", PrairieCastSealed.open(keys, PrairieCastSealed.FROM_PHONE, 1, body)?.decodeToString())
        // The same frame, sealed here with the vector's IV.
        val iv = hex("ffeeddccbbaa99887766554433221100")
        assertContentEquals(body, PrairieCastSealed.seal(keys, PrairieCastSealed.FROM_PHONE, 1, """{"type":"pong","v":2}""".encodeToByteArray(), iv))
    }

    @Test
    fun refusesATamperedReplayedReorderedOrMisdirectedFrame() {
        val body = PrairieCastSealed.seal(keys, PrairieCastSealed.FROM_PHONE, 3, """{"type":"ping","v":2}""".encodeToByteArray())
        assertNotNull(PrairieCastSealed.open(keys, PrairieCastSealed.FROM_PHONE, 3, body))
        assertNull(PrairieCastSealed.open(keys, PrairieCastSealed.FROM_PHONE, 4, body), "replayed")
        assertNull(PrairieCastSealed.open(keys, PrairieCastSealed.FROM_PHONE, 2, body), "reordered")
        assertNull(PrairieCastSealed.open(keys, PrairieCastSealed.FROM_TV, 3, body), "the other direction")
        val tampered = body.copyOf().also { it[30] = (it[30] + 1).toByte() }
        assertNull(PrairieCastSealed.open(keys, PrairieCastSealed.FROM_PHONE, 3, tampered), "tampered")
        val other = PrairieCastSealed.sessionKeys(hex("0f0e0d0c0b0a09080706050403020100"), phoneNonce, tvNonce)
        assertNull(PrairieCastSealed.open(other, PrairieCastSealed.FROM_PHONE, 3, body), "another key")
        assertNull(PrairieCastSealed.open(keys, PrairieCastSealed.FROM_PHONE, 3, hex("00")), "short")
    }

    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
