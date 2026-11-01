package mg.flybox.usage.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Règles du PIN (AC-1, AC-2). Séparé du mot de passe du routeur (BR-7). */
object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6
    const val MAX_ATTEMPTS = 5
    const val LOCK_SECONDS = 30
    const val BACKGROUND_GRACE_MILLIS = 30_000L

    fun isValidFormat(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    /** Faut-il redemander le PIN après un retour d'arrière-plan ? (strictement plus de 30 secondes). */
    fun needsUnlock(backgroundedAt: Long?, now: Long): Boolean =
        backgroundedAt == null || now - backgroundedAt > BACKGROUND_GRACE_MILLIS

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(pin: String, salt: ByteArray, iterations: Int = 60_000): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun verify(pin: String, salt: ByteArray, expectedHash: String, iterations: Int = 60_000): Boolean =
        MessageDigest.isEqual(hash(pin, salt, iterations).toByteArray(), expectedHash.toByteArray())
}

/** État d'un verrouillage après échecs répétés. */
data class PinLockState(val failures: Int = 0, val lockedUntil: Long = 0L) {
    fun remainingLockSeconds(now: Long): Int =
        if (now >= lockedUntil) 0 else (((lockedUntil - now) + 999) / 1000).toInt()

    fun isLocked(now: Long): Boolean = remainingLockSeconds(now) > 0

    /** Après un échec : au 5e, verrouille 30 s et remet le compteur à zéro. */
    fun afterFailure(now: Long): PinLockState {
        val f = failures + 1
        return if (f >= PinPolicy.MAX_ATTEMPTS) PinLockState(0, now + PinPolicy.LOCK_SECONDS * 1000L) else PinLockState(f, lockedUntil)
    }

    fun afterSuccess(): PinLockState = PinLockState()
}
