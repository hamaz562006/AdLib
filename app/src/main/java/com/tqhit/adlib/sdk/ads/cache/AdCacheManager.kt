package com.tqhit.adlib.sdk.ads.cache

import android.os.SystemClock
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.rewarded.RewardedAd
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdCacheManager @Inject constructor() {
    companion object {
        // Standard AdMob ad validity is 1 hour (3,600,000 ms)
        const val AD_EXPIRATION_MILLIS = 3600000L
    }

    private data class CachedAd<T>(
        val ad: T,
        val loadTimeMillis: Long
    )

    private val interstitialCache = mutableMapOf<String, CachedAd<InterstitialAd>>()
    private val rewardedCache = mutableMapOf<String, CachedAd<RewardedAd>>()

    fun putInterstitial(adUnitId: String, ad: InterstitialAd) {
        interstitialCache[adUnitId] = CachedAd(ad, SystemClock.elapsedRealtime())
    }

    fun getInterstitial(adUnitId: String): InterstitialAd? {
        val cached = interstitialCache[adUnitId] ?: return null
        val age = SystemClock.elapsedRealtime() - cached.loadTimeMillis
        if (age > AD_EXPIRATION_MILLIS) {
            interstitialCache.remove(adUnitId)
            return null
        }
        return cached.ad
    }

    fun removeInterstitial(adUnitId: String) {
        interstitialCache.remove(adUnitId)
    }

    fun putRewarded(adUnitId: String, ad: RewardedAd) {
        rewardedCache[adUnitId] = CachedAd(ad, SystemClock.elapsedRealtime())
    }

    fun getRewarded(adUnitId: String): RewardedAd? {
        val cached = rewardedCache[adUnitId] ?: return null
        val age = SystemClock.elapsedRealtime() - cached.loadTimeMillis
        if (age > AD_EXPIRATION_MILLIS) {
            rewardedCache.remove(adUnitId)
            return null
        }
        return cached.ad
    }

    fun removeRewarded(adUnitId: String) {
        rewardedCache.remove(adUnitId)
    }

    fun clearAll() {
        interstitialCache.clear()
        rewardedCache.clear()
    }
}
