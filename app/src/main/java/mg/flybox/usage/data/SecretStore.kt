package mg.flybox.usage.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stockage de secrets (BR-7). Le contenu n'est jamais journalisé. */
interface SecretStore {
    fun put(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
}

/**
 * Chiffrement AES-256-GCM avec une clé non exportable de l'Android Keystore.
 * Le texte chiffré (IV + données, en base64) est conservé dans des SharedPreferences privées.
 */
class KeystoreSecretStore(context: android.content.Context) : SecretStore {
    private val prefs = context.applicationContext.getSharedPreferences("secrets", android.content.Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    @Synchronized
    override fun put(key: String, value: String) {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = c.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = c.iv + enc
        prefs.edit().putString(key, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    @Synchronized
    override fun get(key: String): String? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val packed = Base64.decode(raw, Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, IV_LEN)
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(c.doFinal(packed, IV_LEN, packed.size - IV_LEN), Charsets.UTF_8)
        } catch (_: Exception) {
            // Clé invalidée ou donnée corrompue : on considère le secret absent.
            prefs.edit().remove(key).apply()
            null
        }
    }

    @Synchronized
    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "flybox_secret_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}

object SecretKeys {
    const val ROUTER_PASSWORD = "router_password"
}
