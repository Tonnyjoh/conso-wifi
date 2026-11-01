package mg.flybox.usage.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import mg.flybox.usage.domain.PinLockState

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val alwaysGo: Boolean = false,
    val routerAccount: String = "",
    val routerLoggedIn: Boolean = false,
    /** Vrai si le dernier LOGIN a été refusé : plus aucun essai automatique avant une saisie (EC-3, évite le verrouillage). */
    val routerCredentialsRejected: Boolean = false,
    val hasPin: Boolean = false,
    val onboardingDone: Boolean = false,
    val lastReminderDay: String = "",
    val lastOrangeOk: Long = 0L,
)

/** Réglages non secrets (DataStore). Le mot de passe du routeur n'y figure jamais. */
class SettingsStore(private val context: Context) {
    private object K {
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val alwaysGo = booleanPreferencesKey("always_go")
        val routerAccount = stringPreferencesKey("router_account")
        val routerLoggedIn = booleanPreferencesKey("router_logged_in")
        val credsRejected = booleanPreferencesKey("router_creds_rejected")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val pinFailures = intPreferencesKey("pin_failures")
        val pinLockedUntil = longPreferencesKey("pin_locked_until")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val lastReminderDay = stringPreferencesKey("last_reminder_day")
        val lastOrangeOk = longPreferencesKey("last_orange_ok")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            reminderEnabled = p[K.reminderEnabled] ?: false,
            reminderHour = p[K.reminderHour] ?: 20,
            alwaysGo = p[K.alwaysGo] ?: false,
            routerAccount = p[K.routerAccount] ?: "",
            routerLoggedIn = p[K.routerLoggedIn] ?: false,
            routerCredentialsRejected = p[K.credsRejected] ?: false,
            hasPin = !p[K.pinHash].isNullOrEmpty(),
            onboardingDone = p[K.onboardingDone] ?: false,
            lastReminderDay = p[K.lastReminderDay] ?: "",
            lastOrangeOk = p[K.lastOrangeOk] ?: 0L,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setReminder(enabled: Boolean, hour: Int) = edit {
        it[K.reminderEnabled] = enabled
        it[K.reminderHour] = hour.coerceIn(0, 23)
    }

    suspend fun setAlwaysGo(v: Boolean) = edit { it[K.alwaysGo] = v }

    suspend fun setRouterSession(account: String, loggedIn: Boolean, rejected: Boolean) = edit {
        it[K.routerAccount] = account
        it[K.routerLoggedIn] = loggedIn
        it[K.credsRejected] = rejected
    }

    suspend fun setRouterLoggedIn(loggedIn: Boolean, rejected: Boolean) = edit {
        it[K.routerLoggedIn] = loggedIn
        it[K.credsRejected] = rejected
    }

    suspend fun setOnboardingDone() = edit { it[K.onboardingDone] = true }

    suspend fun setLastReminderDay(day: String) = edit { it[K.lastReminderDay] = day }

    suspend fun setLastOrangeOk(ts: Long) = edit { it[K.lastOrangeOk] = ts }

    suspend fun savePin(hash: String, saltBase64: String) = edit {
        it[K.pinHash] = hash
        it[K.pinSalt] = saltBase64
        it[K.pinFailures] = 0
        it[K.pinLockedUntil] = 0L
    }

    suspend fun pinCredentials(): Pair<String, String>? {
        val p = context.dataStore.data.first()
        val h = p[K.pinHash]
        val s = p[K.pinSalt]
        return if (h.isNullOrEmpty() || s.isNullOrEmpty()) null else h to s
    }

    suspend fun pinLockState(): PinLockState {
        val p: Preferences = context.dataStore.data.first()
        return PinLockState(p[K.pinFailures] ?: 0, p[K.pinLockedUntil] ?: 0L)
    }

    suspend fun setPinLockState(st: PinLockState) = edit {
        it[K.pinFailures] = st.failures
        it[K.pinLockedUntil] = st.lockedUntil
    }
}
