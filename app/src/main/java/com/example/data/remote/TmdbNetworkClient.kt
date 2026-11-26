package com.example.data.remote

import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Custom [Dns] resolver that routes TMDB hosts (`api.themoviedb.org`, `image.tmdb.org`,
 * and subdomains of `themoviedb.org` / `tmdb.org`) over DNS-over-HTTPS so mobile carrier
 * DNS blocks do not stall Discover or poster loading.
 *
 * Resolution order for TMDB hosts:
 * 1. Cloudflare DoH (`https://cloudflare-dns.com/dns-query`, bootstrap `1.1.1.1`, `1.0.0.1`)
 * 2. Google DoH (`https://dns.google/dns-query`, bootstrap `8.8.8.8`, `8.8.4.4`)
 * 3. System DNS ([Dns.SYSTEM])
 *
 * All non-TMDB hosts bypass DoH and always use [systemDns].
 */
class TmdbDohDns internal constructor(
    private val cloudflareDns: Dns,
    private val googleDns: Dns,
    private val systemDns: Dns = Dns.SYSTEM,
    private val errorLogger: (String, Throwable?) -> Unit = { msg, tr ->
        Log.e(TAG, msg, tr)
    },
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        if (!isTmdbHost(hostname)) {
            return systemDns.lookup(hostname)
        }

        val cloudflareError: Throwable? = try {
            val addresses = cloudflareDns.lookup(hostname)
            if (addresses.isNotEmpty()) return addresses
            UnknownHostException("Cloudflare DoH returned empty address list for $hostname")
        } catch (e: Throwable) {
            errorLogger(
                "Cloudflare DoH failed for $hostname: ${e::class.java.name}: ${e.message}",
                e,
            )
            e
        }

        val googleError: Throwable? = try {
            val addresses = googleDns.lookup(hostname)
            if (addresses.isNotEmpty()) return addresses
            UnknownHostException("Google DoH returned empty address list for $hostname")
        } catch (e: Throwable) {
            errorLogger(
                "Google DoH failed for $hostname: ${e::class.java.name}: ${e.message}",
                e,
            )
            e
        }

        return try {
            val addresses = systemDns.lookup(hostname)
            if (addresses.isNotEmpty()) {
                addresses
            } else {
                throw UnknownHostException("System DNS returned empty address list for $hostname")
            }
        } catch (e: Throwable) {
            errorLogger(
                "System DNS fallback failed for $hostname: ${e::class.java.name}: ${e.message}",
                e,
            )
            if (e is UnknownHostException) {
                throw e
            }
            val uhe = UnknownHostException(
                "Unable to resolve $hostname via Cloudflare DoH, Google DoH, or system DNS: " +
                    "${e::class.java.simpleName}: ${e.message}"
            )
            uhe.initCause(e)
            cloudflareError?.let { uhe.addSuppressed(it) }
            googleError?.let { uhe.addSuppressed(it) }
            throw uhe
        }
    }

    companion object {
        private const val TAG = "TmdbDohDns"
        private const val CLOUDFLARE_DOH_URL = "https://cloudflare-dns.com/dns-query"
        private const val GOOGLE_DOH_URL = "https://dns.google/dns-query"

        fun isTmdbHost(hostname: String): Boolean {
            val normalized = hostname.trim().lowercase(Locale.US).trimEnd('.')
            return normalized == "api.themoviedb.org" ||
                normalized == "image.tmdb.org" ||
                normalized == "themoviedb.org" ||
                normalized.endsWith(".themoviedb.org") ||
                normalized == "tmdb.org" ||
                normalized.endsWith(".tmdb.org")
        }

        fun createDefault(bootstrapClient: OkHttpClient): TmdbDohDns {
            val cloudflare = DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(CLOUDFLARE_DOH_URL.toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                )
                .build()

            val google = DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(GOOGLE_DOH_URL.toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("8.8.4.4"),
                )
                .build()

            return TmdbDohDns(
                cloudflareDns = cloudflare,
                googleDns = google,
                systemDns = Dns.SYSTEM,
            )
        }
    }
}

/**
 * Provides one shared [OkHttpClient] configured with TMDB-scoped DNS-over-HTTPS
 * and bounded timeouts (10s connect, 15s read) for both Retrofit TMDB requests
 * and Coil image loading (`image.tmdb.org`).
 */
object TmdbNetworkClient {
    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 15L

    private val bootstrapClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    val tmdbDohDns: TmdbDohDns by lazy {
        TmdbDohDns.createDefault(bootstrapClient)
    }

    val sharedOkHttpClient: OkHttpClient by lazy {
        bootstrapClient.newBuilder()
            .dns(tmdbDohDns)
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
