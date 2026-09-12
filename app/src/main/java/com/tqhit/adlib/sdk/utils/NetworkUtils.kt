package com.tqhit.adlib.sdk.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object NetworkUtils {
    /**
     * Check if device is currently connected to the internet
     */
    fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            activeNetworkInfo?.isConnected == true
        }
    }

    /**
     * Checks real reachability of Google ad servers at the HTTPS layer (not just raw TCP).
     * Some network-level filtering (e.g. SNI-based or DPI filtering) allows the raw TCP
     * handshake to succeed while still blocking the actual HTTPS request, so a simple
     * socket connect check can give a false positive. This performs a lightweight HEAD
     * request instead, which exercises the same layer that real ad requests use.
     */
    suspend fun isAdServerReachable(timeoutMs: Int = 3000): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            val url = URL("https://pubads.g.doubleclick.net/gampad/ads")
            connection = url.openConnection() as HttpsURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = "HEAD"
            connection.connect()
            val responseCode = connection.responseCode
            // Any real HTTP response (even 4xx) means we actually reached the server at the
            // HTTPS layer. Only a timeout/connection failure means it's actually unreachable.
            responseCode in 100..599
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
