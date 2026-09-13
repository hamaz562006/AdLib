package com.tqhit.adlib.sdk.ads.admob

import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rate limiter to cooldown AdMob requests after NO_FILL errors.
 * Prevents spamming AdMob servers repeatedly in the same session.
 */
@Singleton
class AdmobRateLimiter @Inject constructor(
    private val remoteConfigHelper: FirebaseRemoteConfigHelper
) {
    companion object {
        const val RC_ADMOB_NO_FILL_COOLDOWN_SECONDS = "admob_no_fill_cooldown_seconds"
        private const val DEFAULT_COOLDOWN_SECONDS = 60L
    }

    private val lastNoFillTimestamps = ConcurrentHashMap<String, Long>()

    fun recordNoFill(adUnitId: String) {
        if (adUnitId.isNotBlank()) {
            lastNoFillTimestamps[adUnitId] = System.currentTimeMillis()
        }
    }

    fun canRequest(adUnitId: String): Boolean {
        if (adUnitId.isBlank()) return true
        val lastNoFillTime = lastNoFillTimestamps[adUnitId] ?: return true
        val cooldownSeconds = remoteConfigHelper.getLong(RC_ADMOB_NO_FILL_COOLDOWN_SECONDS).let {
            if (it > 0) it else DEFAULT_COOLDOWN_SECONDS
        }
        val elapsedSeconds = (System.currentTimeMillis() - lastNoFillTime) / 1000
        return elapsedSeconds >= cooldownSeconds
    }
}
