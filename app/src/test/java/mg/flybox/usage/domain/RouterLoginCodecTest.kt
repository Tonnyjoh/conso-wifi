package mg.flybox.usage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vecteurs calculés indépendamment (sha256sum et base64 du shell) avec un mot de passe fictif. */
class RouterLoginCodecTest {
    @Test fun sha256IsLowercaseHex() {
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", RouterLoginCodec.sha256Hex("test"))
    }

    @Test fun usernameIsBase64() = assertEquals("YWRtaW4=", RouterLoginCodec.encodeUsername("admin"))

    @Test fun oldVariantIsBase64OfRandomPlusSha256OfPassword() {
        // base64("6516650" + sha256("test"))
        val expected = java.util.Base64.getEncoder().encodeToString(
            ("6516650" + "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08").toByteArray(),
        )
        assertEquals(expected, RouterLoginCodec.encodePassword("6516650", "test", newVariant = false))
    }

    @Test fun newVariantIsBase64OfSha256OfRandomPlusPassword() {
        val inner = RouterLoginCodec.sha256Hex("6516650test")
        assertEquals(java.util.Base64.getEncoder().encodeToString(inner.toByteArray()), RouterLoginCodec.encodePassword("6516650", "test", true))
    }

    @Test fun variantSelectionFollowsCustomerCode() {
        assertFalse(RouterLoginCodec.usesNewVariant("FR0296"))
        assertFalse(RouterLoginCodec.usesNewVariant(null))
        assertTrue(RouterLoginCodec.usesNewVariant("MA0553"))
    }

    @Test fun utf8PasswordsAreEncodedAsUtf8() {
        assertEquals(RouterLoginCodec.sha256Hex("é"), "4a99557e4033c3539de2eb65472017cad5f9557f7a0625a09f1c3f6e2ba69c4c")
    }
}
