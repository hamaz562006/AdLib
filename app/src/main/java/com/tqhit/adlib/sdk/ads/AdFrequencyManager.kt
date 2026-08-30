package com.tqhit.adlib.sdk.ads

import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global frequency and capping manager for ad displays
 * Coordinates cooldowns, delay after rewarded ads, per-placement rules, and session counts.
 */
@Singleton
class AdFrequencyManager @Inject constructor(
    private val remoteConfigHelper: FirebaseRemoteConfigHelper
) {
    private var lastInterstitialShowTime: Long = 0L
    private var lastRewardedShowTime: Long = 0L
    private var lastAppOpenShowTime: Long = 0L

    private var interstitialSessionCount: Int = 0
    private var rewardedSessionCount: Int = 0
    private var appOpenSessionCount: Int = 0

    private val placementLastShowTimes = mutableMapOf<String, Long>()

    companion object {
        const val RC_IV_SHOW_FREQUENCY = "iv_show_frequency"
        const val RC_IV_DELAY_SHOW_AFTER_RV = "iv_delay_show_after_rv"
        const val RC_AOA_SHOW_FREQUENCY = "aoa_show_frequency"
        const val RC_MAX_IV_PER_SESSION = "max_iv_per_session"
    }

    /**
     * Check if interstitial ad can be shown based on frequency and delay rules
     */
    fun canShowInterstitial(placementTag: String? = null): Boolean {
        val currentTime = System.currentTimeMillis()
        val frequencySeconds = remoteConfigHelper.getLong(RC_IV_SHOW_FREQUENCY)
        val delayAfterRewardedSeconds = remoteConfigHelper.getLong(RC_IV_DELAY_SHOW_AFTER_RV)
        val maxIvPerSession = remoteConfigHelper.getLong(RC_MAX_IV_PER_SESSION)

        // Session cap
        if (maxIvPerSession > 0 && interstitialSessionCount >= maxIvPerSession) {
            return false
        }

        // Check global frequency rule (time between interstitial ads)
        val timeSinceLastIv = (currentTime - lastInterstitialShowTime) / 1000
        if (frequencySeconds > 0 && timeSinceLastIv < frequencySeconds) {
            return false
        }

        // Check delay after rewarded rule
        val timeSinceLastRv = (currentTime - lastRewardedShowTime) / 1000
        if (delayAfterRewardedSeconds > 0 && timeSinceLastRv < delayAfterRewardedSeconds) {
            return false
        }

        // Check placement specific cooldown if tag provided
        if (!placementTag.isNullOrBlank()) {
            val lastPlacementTime = placementLastShowTimes[placementTag] ?: 0L
            val timeSincePlacement = (currentTime - lastPlacementTime) / 1000
            if (frequencySeconds > 0 && timeSincePlacement < frequencySeconds) {
                return false
            }
        }

        return true
    }

    fun recordInterstitialShown(placementTag: String? = null) {
        val now = System.currentTimeMillis()
        lastInterstitialShowTime = now
        interstitialSessionCount++
        if (!placementTag.isNullOrBlank()) {
            placementLastShowTimes[placementTag] = now
        }
    }

    fun onInterstitialShown(placementTag: String? = null) = recordInterstitialShown(placementTag)
    fun onInterstitialDismissed() {}

    fun canShowRewarded(): Boolean {
        return true
    }

    fun recordRewardedShown() {
        lastRewardedShowTime = System.currentTimeMillis()
        rewardedSessionCount++
    }

    fun onRewardedAdShown() = recordRewardedShown()
    fun onRewardedAdClosed() {}

    fun canShowAppOpen(): Boolean {
        val currentTime = System.currentTimeMillis()
        val frequencySeconds = remoteConfigHelper.getLong(RC_AOA_SHOW_FREQUENCY)
        if (frequencySeconds <= 0) return true
        val timeSinceLast = (currentTime - lastAppOpenShowTime) / 1000
        return timeSinceLast >= frequencySeconds
    }

    fun recordAppOpenShown() {
        lastAppOpenShowTime = System.currentTimeMillis()
        appOpenSessionCount++
    }

    fun onAppOpenShown() = recordAppOpenShown()
    fun onAppOpenDismissed() {}

    fun getSessionStats(): Map<String, Any> {
        return mapOf(
            "interstitialCount" to interstitialSessionCount,
            "rewardedCount" to rewardedSessionCount,
            "appOpenCount" to appOpenSessionCount
        )
    }
}
