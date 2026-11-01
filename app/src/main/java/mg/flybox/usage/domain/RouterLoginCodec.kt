package mg.flybox.usage.domain

import java.security.MessageDigest
import java.util.Base64

/**
 * Encodage des identifiants pour le LOGIN du routeur, tel que lu dans le client web
 * (app.js et chunk-59d2018e.js) :
 *  - username = base64(compte)
 *  - ancienne variante (dont le code client FR0296 observé) : password = base64(random_login + sha256hex(mot de passe))
 *  - nouvelle variante (codes MA0553, EG0657_common_encryption, IO250013_common_encryption, CMR510_common_encryption) :
 *    password = base64(sha256hex(random_login + mot de passe))
 * sha256hex est le condensé SHA-256 en hexadécimal minuscule (js-sha256), base64 standard avec remplissage.
 */
object RouterLoginCodec {
    private val newVariantCodes = setOf("MA0553", "EG0657_common_encryption", "IO250013_common_encryption", "CMR510_common_encryption")

    fun usesNewVariant(customerCode: String?): Boolean = customerCode != null && customerCode in newVariantCodes

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun base64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    fun encodeUsername(account: String): String = base64(account)

    fun encodePassword(randomLogin: String, password: String, newVariant: Boolean): String =
        if (newVariant) base64(sha256Hex(randomLogin + password)) else base64(randomLogin + sha256Hex(password))

    /** Jeton CSRF : sha256hex du jeton retourné par get_token (vide avant connexion, alors omis). */
    fun csrfToken(token: String): String = sha256Hex(token)
}
