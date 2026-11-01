package mg.flybox.usage.data

import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mg.flybox.usage.data.net.LoginOutcome
import mg.flybox.usage.data.net.RouterClient
import mg.flybox.usage.data.net.RouterSessionRequiredException
import mg.flybox.usage.data.net.RouterUnreachableException
import mg.flybox.usage.domain.DeviceLists
import mg.flybox.usage.domain.DeviceTraffic

sealed interface DevicesResult {
    /** Aucune connexion routeur configurée ou identifiants refusés (AC-10). */
    data object NotLoggedIn : DevicesResult
    data object Unreachable : DevicesResult
    data object AccountInUse : DevicesResult
    data class Ready(val lists: DeviceLists, val traffic: List<DeviceTraffic>) : DevicesResult
}

/**
 * Gère la session du routeur (AC-3, EC-3, EC-6). Le mot de passe vit dans [SecretStore] seulement.
 * Les identifiants refusés ne sont plus rejoués automatiquement pour ne pas verrouiller le compte.
 */
class RouterSession(
    private val router: RouterClient,
    private val secrets: SecretStore,
    private val settings: SettingsStore,
) {
    private val mutex = Mutex()

    /** Connexion explicite depuis les réglages. Aucun stockage si elle échoue (AC-3). */
    suspend fun login(account: String, password: String): LoginOutcome = mutex.withLock {
        val outcome = try {
            router.login(account.trim(), password)
        } catch (e: IOException) {
            throw RouterUnreachableException(e)
        }
        if (outcome is LoginOutcome.Success) {
            secrets.put(SecretKeys.ROUTER_PASSWORD, password)
            settings.setRouterSession(account.trim(), loggedIn = true, rejected = false)
        }
        outcome
    }

    /** Oublie les identifiants et ferme la session. */
    suspend fun forget() = mutex.withLock {
        router.logout()
        secrets.remove(SecretKeys.ROUTER_PASSWORD)
        settings.setRouterSession("", loggedIn = false, rejected = false)
    }

    private suspend fun silentLogin(): Boolean {
        val s = settings.current()
        if (s.routerAccount.isBlank() || s.routerCredentialsRejected) return false
        val pw = secrets.get(SecretKeys.ROUTER_PASSWORD) ?: return false
        return when (router.login(s.routerAccount, pw)) {
            LoginOutcome.Success -> { settings.setRouterLoggedIn(true, false); true }
            is LoginOutcome.WrongPassword, is LoginOutcome.Rejected, is LoginOutcome.Locked -> {
                settings.setRouterLoggedIn(false, true)
                false
            }
            LoginOutcome.AccountInUse -> false
        }
    }

    /** Appareils et trafic. Une ré-authentification silencieuse au plus (EC-3). */
    suspend fun devices(): DevicesResult = mutex.withLock {
        val s = settings.current()
        if (s.routerAccount.isBlank() || !secrets.hasPassword()) return DevicesResult.NotLoggedIn
        if (s.routerCredentialsRejected) return DevicesResult.NotLoggedIn
        try {
            try {
                return ready()
            } catch (_: RouterSessionRequiredException) {
                // session expirée ou routeur redémarré
            }
            if (!silentLogin()) {
                return if (settings.current().routerCredentialsRejected) DevicesResult.NotLoggedIn else DevicesResult.AccountInUse
            }
            try {
                ready()
            } catch (_: RouterSessionRequiredException) {
                settings.setRouterLoggedIn(false, false)
                DevicesResult.NotLoggedIn
            }
        } catch (_: RouterUnreachableException) {
            DevicesResult.Unreachable
        }
    }

    private suspend fun ready(): DevicesResult.Ready {
        val lists = router.deviceLists()
        val traffic = try { router.deviceTraffic() } catch (_: IOException) { emptyList() }
        return DevicesResult.Ready(lists, traffic)
    }

    private fun SecretStore.hasPassword(): Boolean = get(SecretKeys.ROUTER_PASSWORD) != null
}
