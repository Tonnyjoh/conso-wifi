package mg.flybox.usage.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Oriente les requêtes vers le réseau Wi-Fi quand il existe, même si Android préfère les données
 * mobiles : le routeur n'est joignable que par le Wi-Fi Flybox.
 */
fun wifiNetwork(context: Context): Network? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Suppress("DEPRECATION")
    return cm.allNetworks.firstOrNull { n ->
        cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}

class WifiSocketFactory(private val context: Context) : SocketFactory() {
    private fun delegate(): SocketFactory = wifiNetwork(context)?.socketFactory ?: getDefault()

    override fun createSocket(): Socket = delegate().createSocket()
    override fun createSocket(host: String?, port: Int): Socket = delegate().createSocket(host, port)
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        delegate().createSocket(host, port, localHost, localPort)
    override fun createSocket(host: InetAddress?, port: Int): Socket = delegate().createSocket(host, port)
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        delegate().createSocket(address, port, localAddress, localPort)
}

object HttpClients {
    fun create(context: Context, cookies: MemoryCookieJar? = null): OkHttpClient =
        OkHttpClient.Builder()
            .socketFactory(WifiSocketFactory(context.applicationContext))
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    wifiNetwork(context.applicationContext)?.getAllByName(hostname)?.toList() ?: Dns.SYSTEM.lookup(hostname)
            })
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .apply { if (cookies != null) cookieJar(cookies) }
            .build()
}
