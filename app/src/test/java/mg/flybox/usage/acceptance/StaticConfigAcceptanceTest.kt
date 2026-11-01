package mg.flybox.usage.acceptance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Static checks on shipped configuration files (EC-5, constraints, AC-14 partial). Gradle runs tests from the app/ directory. */
class StaticConfigAcceptanceTest {
    private fun f(p: String): File = File("src/main/$p").takeIf { it.exists() } ?: File("app/src/main/$p")

    @Test fun `EC-5 cleartext allowed for exactly the two hosts`() {
        val x = f("res/xml/network_security_config.xml").readText().replace(Regex("<!--[\\s\\S]*?-->"), "")
        assertTrue(Regex("<base-config\\s+cleartextTrafficPermitted=\"false\"").containsMatchIn(x))
        val domains = Regex("<domain[^>]*>([^<]+)</domain>").findAll(x).map { it.groupValues[1].trim() }.toSet()
        assertEquals(setOf("192.168.1.1", "123.orange.mg"), domains)
        assertTrue(x.contains("includeSubdomains=\"false\""))
    }

    @Test fun `EC-5 manifest references the network security config and no global cleartext`() {
        val m = f("AndroidManifest.xml").readText()
        assertTrue(m.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertTrue(!m.contains("usesCleartextTraffic=\"true\""))
    }

    @Test fun `Constraints permissions are limited to the documented ones`() {
        val m = f("AndroidManifest.xml").readText()
        val perms = Regex("<uses-permission[^>]*?android:name=\"([^\"]+)\"").findAll(m).map { it.groupValues[1] }.toSet()
        val allowed = setOf(
            "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.POST_NOTIFICATIONS", "android.permission.PACKAGE_USAGE_STATS",
        )
        assertTrue("unexpected permissions: ${perms - allowed}", allowed.containsAll(perms))
        assertTrue(perms.contains("android.permission.PACKAGE_USAGE_STATS"))
    }

    @Test fun `AC-14 partial accent color and Montserrat font resource present, no dark theme switch`() {
        val theme = f("java/mg/flybox/usage/ui/theme/Theme.kt").readText()
        assertTrue(theme.contains("0xFFFF7900", ignoreCase = true))
        assertTrue(File(f("res/font/montserrat.ttf").path).exists())
        assertTrue("dark theme must not be used", !theme.contains("isSystemInDarkTheme") && !theme.contains("darkColorScheme"))
    }

    @Test fun `Constraints no analytics or tracker dependencies`() {
        val g = File("build.gradle.kts").takeIf { it.exists() } ?: File("app/build.gradle.kts")
        val t = g.readText().lowercase()
        listOf("firebase", "crashlytics", "analytics", "sentry", "appsflyer", "mixpanel", "admob").forEach {
            assertTrue("found $it", !t.contains(it))
        }
    }
}
