package com.tqhit.adlib.sdk.ads

import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global frequency and capping manager for ad displays
 * Coordinates cooldowns, delay after rewarded ads, per-placement rules, and session counts.
 */
@Singleton
class AdFrequencyManager @Inject constructor(
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val preferencesHelper: PreferencesHelper
) {
    private var lastInterstitialShowTime: Long = 0L
    private var lastRewardedShowTime: Long = 0L
    private var lastAppOpenShowTime: Long = 0L

    private var interstitialSessionCount: Int = 0
    private var rewardedSessionCount: Int = 0
    private var appOpenSessionCount: Int = 0

    private var placementLastShowTimes = mutableMapOf<String, Long>()

    init {
        lastInterstitialShowTime = preferencesHelper.getLong(PREF_KEY_FREQ_LAST_IV, 0L)
        lastRewardedShowTime = preferencesHelper.getLong(PREF_KEY_FREQ_LAST_RV, 0L)
        lastAppOpenShowTime = preferencesHelper.getLong(PREF_KEY_FREQ_LAST_AO, 0L)

        val savedPlacementMap = preferencesHelper.getObject(PREF_KEY_FREQ_PLACEMENT_MAP, Map::class.java)
        if (savedPlacementMap != null) {
            val convertedMap = mutableMapOf<String, Long>()
            for ((k, v) in savedPlacementMap) {
                if (k != null && v != null) {
                    val keyStr = k.toString()
                    val longVal = when (v) {
                        is Number -> v.toLong()
                        is String -> v.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    convertedMap[keyStr] = longVal
                }
            }
            placementLastShowTimes = convertedMap
        }
    }

    companion object {
        const val RC_IV_SHOW_FREQUENCY = "iv_show_frequency"
        const val RC_IV_DELAY_SHOW_AFTER_RV = "iv_delay_show_after_rv"
        const val RC_AOA_SHOW_FREQUENCY = "aoa_show_frequency"
        const val RC_MAX_IV_PER_SESSION = "max_iv_per_session"

        const val PREF_KEY_FREQ_LAST_IV = "freq_last_iv"
        const val PREF_KEY_FREQ_LAST_RV = "freq_last_rv"
        const val PREF_KEY_FREQ_LAST_AO = "freq_last_ao"
        const val PREF_KEY_FREQ_PLACEMENT_MAP = "freq_placement_map"
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
        preferencesHelper.saveLong(PREF_KEY_FREQ_LAST_IV, now)
        interstitialSessionCount++
        if (!placementTag.isNullOrBlank()) {
            placementLastShowTimes[placementTag] = now
            preferencesHelper.saveObject(PREF_KEY_FREQ_PLACEMENT_MAP, placementLastShowTimes)
        }
    }

    fun onInterstitialShown(placementTag: String? = null) = recordInterstitialShown(placementTag)
    fun onInterstitialDismissed() {}

    fun canShowRewarded(): Boolean {
        return true
    }

    fun recordRewardedShown() {
        val now = System.currentTimeMillis()
        lastRewardedShowTime = now
        preferencesHelper.saveLong(PREF_KEY_FREQ_LAST_RV, now)
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
        val now = System.currentTimeMillis()
        lastAppOpenShowTime = now
        preferencesHelper.saveLong(PREF_KEY_FREQ_LAST_AO, now)
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
