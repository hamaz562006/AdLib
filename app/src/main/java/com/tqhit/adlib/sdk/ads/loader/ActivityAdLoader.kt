package com.tqhit.adlib.sdk.ads.loader

import android.app.Activity
import android.util.Log
import android.widget.FrameLayout
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.tqhit.adlib.sdk.ads.admob.AdmobHelper
import com.tqhit.adlib.sdk.ads.callback.admob.BannerAdCallback
import com.tqhit.adlib.sdk.ads.callback.admob.InterstitialAdCallback
import com.tqhit.adlib.sdk.ads.callback.admob.NativeAdCallback
import com.tqhit.adlib.sdk.ads.callback.admob.RewardAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseBannerAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseInterstitialAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseNativeAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseRewardAdCallback
import com.tqhit.adlib.sdk.ads.house.HouseAdHelper
import com.tqhit.adlib.sdk.ads.house.HouseAdManager
import com.tqhit.adlib.sdk.ads.house.model.HouseAdItem
import com.tqhit.adlib.sdk.ads.house.model.HouseAdType
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityAdLoader @Inject constructor(
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val admobHelper: AdmobHelper,
    private val houseAdHelper: HouseAdHelper,
    private val houseAdManager: HouseAdManager
) {
    private val loadedAdsLiveData = mutableMapOf<String, MutableLiveData<Any>>()
    private val adLoadInProgress = mutableSetOf<String>()

    companion object {
        private const val TAG = "ActivityAdLoader"

        // Prefix for preloaded ads
        const val PRELOAD_PREFIX = "PRELOAD_"

        // Suffixes for different ad types
        const val INTERSTITIAL_SUFFIX = "_IV"
        const val REWARDED_SUFFIX = "_RV"
        const val NATIVE_SUFFIX = "_NT"
        const val BANNER_SUFFIX = "_BN"
        const val COLLAPSIBLE_BANNER_SUFFIX = "_C_BN"
        const val AOA_SUFFIX = "_AOA"

        // Remote Config Keys for Ad Unit IDs (fallback when no custom ID in JSON)
        const val RC_INTERSTITIAL_AD_UNIT_ID = "iv_ad_unit_id"
        const val RC_REWARDED_AD_UNIT_ID = "rv_ad_unit_id"
        const val RC_NATIVE_AD_UNIT_ID = "nt_ad_unit_id"
        const val RC_BANNER_AD_UNIT_ID = "bn_ad_unit_id"
        const val RC_C_BANNER_AD_UNIT_ID = "c_bn_ad_unit_id"
        const val RC_AOA_AD_UNIT_ID = "aoa_ad_unit_id"

        const val RC_ENABLE_ACTIVITY_AD_LOADER = "enable_activity_ad_loader"
    }

    // Data class for ad configuration from JSON
    data class AdConfig(
        val useHouseAd: Boolean = false,
        val autoFallback: Boolean = true,
        val customId: String? = null
    )

    fun onActivityResumed(activity: Activity, fragmentName: String? = null) {
        if (remoteConfigHelper.getBoolean(RC_ENABLE_ACTIVITY_AD_LOADER).not()) {
            Log.d(TAG, "ActivityAdLoader is disabled via remote config.")
            return
        }

        val activityKeyBase = activity.javaClass.simpleName + (fragmentName?.let { "_$it" } ?: "")
        val remoteConfigKeyForActivity = "${PRELOAD_PREFIX}${activityKeyBase}" // e.g., PRELOAD_MainActivity

        Log.d(TAG, "onActivityResumed for: $activityKeyBase")

        val adIdentifiersString = remoteConfigHelper.getString(remoteConfigKeyForActivity)

        if (adIdentifiersString.isBlank()) {
            Log.d(TAG, "No ad identifiers found in Remote Config for key: $remoteConfigKeyForActivity")
            return
        }

        Log.d(TAG, "Found ad identifiers for $remoteConfigKeyForActivity: \"$adIdentifiersString\"")
        val adIdentifiers = adIdentifiersString.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        for (adKey in adIdentifiers) {
            preloadAdForKey(activity, adKey)
        }
    }

    /**
     * Parse JSON configuration for a specific ad key from Remote Config
     * Expected JSON format: {"useHouseAd": true/false, "autoFallback": true/false, "customId": "optional_custom_id"}
     */
    fun getAdConfig(adKey: String): AdConfig? {
        return try {
            val jsonString = remoteConfigHelper.getString(adKey)
            if (jsonString.isBlank()) {
                Log.d(TAG, "No custom JSON config for $adKey; using default AdMob config")
                return AdConfig(useHouseAd = false, autoFallback = true, customId = null)
            }

            val json = JSONObject(jsonString)
            val useHouseAd = json.optBoolean("useHouseAd", false)
            val autoFallback = json.optBoolean("autoFallback", true)
            val customId = try {
                val id = json.getString("customId")
                if (id.isNotBlank()) id else null
            } catch (e: Exception) {
                null
            }

            Log.d(TAG, "Parsed ad config for $adKey: useHouseAd=$useHouseAd, customId=$customId")
            AdConfig(useHouseAd, autoFallback, customId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON configuration for ad key: $adKey", e)
            AdConfig(useHouseAd = false, autoFallback = true, customId = null)
        }
    }

    /**
     * Get ad unit ID based on ad key, configuration, and ad type
     */
    private fun getAdUnitId(adKey: String, adConfig: AdConfig): String {
        if (!adConfig.customId.isNullOrBlank()) {
            return adConfig.customId
        }

        val fallbackKey = when {
            adKey.endsWith(INTERSTITIAL_SUFFIX) -> RC_INTERSTITIAL_AD_UNIT_ID
            adKey.endsWith(REWARDED_SUFFIX) -> RC_REWARDED_AD_UNIT_ID
            adKey.endsWith(NATIVE_SUFFIX) -> RC_NATIVE_AD_UNIT_ID
            adKey.endsWith(BANNER_SUFFIX) -> RC_BANNER_AD_UNIT_ID
            adKey.endsWith(COLLAPSIBLE_BANNER_SUFFIX) -> RC_C_BANNER_AD_UNIT_ID
            adKey.endsWith(AOA_SUFFIX) -> RC_AOA_AD_UNIT_ID
            else -> {
                Log.w(TAG, "Unknown ad type suffix for key: $adKey")
                return ""
            }
        }

        return remoteConfigHelper.getString(fallbackKey)
    }

    private fun preloadAdForKey(activity: Activity, adKey: String) {
        if (loadedAdsLiveData.containsKey(adKey) || adLoadInProgress.contains(adKey)) {
            Log.d(TAG, "Ad for $adKey already loaded or in progress. Skipping.")
            return
        }

        Log.d(TAG, "Attempting to preload ad for key: $adKey")
        adLoadInProgress.add(adKey)
        val adLiveData = MutableLiveData<Any>()
        loadedAdsLiveData[adKey] = adLiveData

        val adConfig = getAdConfig(adKey) ?: AdConfig()

        if (adConfig.useHouseAd) {
            val houseAd = houseAdManager.getNextAd(getHouseTypeForAdKey(adKey))
            handleAdLoaded(adKey, houseAd, "HouseAd", adLiveData)
            return
        }

        val adUnitId = getAdUnitId(adKey, adConfig)
        if (adUnitId.isEmpty()) {
            if (adConfig.autoFallback) {
                val houseAd = houseAdManager.getNextAd(getHouseTypeForAdKey(adKey))
                handleAdLoaded(adKey, houseAd, "HouseAdFallback", adLiveData)
            } else {
                adLoadInProgress.remove(adKey)
                loadedAdsLiveData.remove(adKey)
            }
            return
        }

        when {
            adKey.endsWith(INTERSTITIAL_SUFFIX) -> {
                admobHelper.loadInterstitial(activity, adUnitId, 10000, object : InterstitialAdCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        super.onAdLoaded(interstitialAd)
                        handleAdLoaded(adKey, interstitialAd, "Interstitial", adLiveData)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError?) {
                        super.onAdFailedToLoad(adError)
                        if (adConfig.autoFallback) {
                            val houseAd = houseAdManager.getNextAd(HouseAdType.INTERSTITIAL)
                            handleAdLoaded(adKey, houseAd, "HouseInterstitialFallback", adLiveData)
                        } else {
                            handleAdFailedToLoad(adKey, adError?.message, "Interstitial")
                        }
                    }
                })
            }
            adKey.endsWith(REWARDED_SUFFIX) -> {
                admobHelper.loadReward(activity, adUnitId, 10000, object : RewardAdCallback() {
                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        super.onAdLoaded(rewardedAd)
                        handleAdLoaded(adKey, rewardedAd, "Rewarded", adLiveData)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError?) {
                        super.onAdFailedToLoad(adError)
                        if (adConfig.autoFallback) {
                            val houseAd = houseAdManager.getNextAd(HouseAdType.REWARDED)
                            handleAdLoaded(adKey, houseAd, "HouseRewardedFallback", adLiveData)
                        } else {
                            handleAdFailedToLoad(adKey, adError?.message, "Rewarded")
                        }
                    }
                })
            }
            adKey.endsWith(NATIVE_SUFFIX) -> {
                admobHelper.loadNative(activity, adUnitId, 100000, object : NativeAdCallback() {
                    override fun onAdLoaded(nativeAd: NativeAd) {
                        super.onAdLoaded(nativeAd)
                        handleAdLoaded(adKey, nativeAd, "Native", adLiveData)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError?) {
                        super.onAdFailedToLoad(adError)
                        if (adConfig.autoFallback) {
                            val houseAd = houseAdManager.getNextAd(HouseAdType.NATIVE)
                            handleAdLoaded(adKey, houseAd, "HouseNativeFallback", adLiveData)
                        } else {
                            handleAdFailedToLoad(adKey, adError?.message, "Native")
                        }
                    }
                })
            }
            adKey.endsWith(BANNER_SUFFIX) -> {
                admobHelper.loadBanner(activity, adUnitId, 10000, object : BannerAdCallback() {
                    override fun onAdLoaded(adView: AdView) {
                        super.onAdLoaded(adView)
                        handleAdLoaded(adKey, adView, "Banner", adLiveData)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError?) {
                        super.onAdFailedToLoad(adError)
                        if (adConfig.autoFallback) {
                            val houseAd = houseAdManager.getNextAd(HouseAdType.BANNER)
                            handleAdLoaded(adKey, houseAd, "HouseBannerFallback", adLiveData)
                        } else {
                            handleAdFailedToLoad(adKey, adError?.message, "Banner")
                        }
                    }
                })
            }
            adKey.endsWith(COLLAPSIBLE_BANNER_SUFFIX) -> {
                admobHelper.loadCollapsibleBanner(activity, adUnitId, 10000, object : BannerAdCallback() {
                    override fun onAdLoaded(adView: AdView) {
                        super.onAdLoaded(adView)
                        handleAdLoaded(adKey, adView, "Collapsible Banner", adLiveData)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError?) {
                        super.onAdFailedToLoad(adError)
                        if (adConfig.autoFallback) {
                            val houseAd = houseAdManager.getNextAd(HouseAdType.BANNER)
                            handleAdLoaded(adKey, houseAd, "HouseBannerFallback", adLiveData)
                        } else {
                            handleAdFailedToLoad(adKey, adError?.message, "Collapsible Banner")
                        }
                    }
                })
            }
            adKey.endsWith(AOA_SUFFIX) -> {
                admobHelper.setAppOpenAdUnitId(adUnitId)
                admobHelper.loadAOA(activity)
            }
            else -> {
                adLoadInProgress.remove(adKey)
                loadedAdsLiveData.remove(adKey)
            }
        }
    }

    private fun getHouseTypeForAdKey(adKey: String): HouseAdType {
        return when {
            adKey.endsWith(INTERSTITIAL_SUFFIX) -> HouseAdType.INTERSTITIAL
            adKey.endsWith(REWARDED_SUFFIX) -> HouseAdType.REWARDED
            adKey.endsWith(NATIVE_SUFFIX) -> HouseAdType.NATIVE
            adKey.endsWith(AOA_SUFFIX) -> HouseAdType.APP_OPEN
            else -> HouseAdType.BANNER
        }
    }

    private fun handleAdLoaded(adKey: String, adObject: Any?, adType: String, liveDataToUpdate: MutableLiveData<Any>) {
        adLoadInProgress.remove(adKey)
        if (adObject != null) {
            Log.d(TAG, "$adType ad loaded successfully for key: $adKey")
            liveDataToUpdate.postValue(adObject)
        } else {
            Log.e(TAG, "$adType ad loaded as null for key: $adKey")
            loadedAdsLiveData.remove(adKey)
        }
    }

    private fun handleAdFailedToLoad(adKey: String, error: String?, adType: String) {
        adLoadInProgress.remove(adKey)
        Log.w(TAG, "Failed to load $adType ad for $adKey: $error")
        loadedAdsLiveData.remove(adKey)
    }

    fun getAdLiveData(key: String): MutableLiveData<Any>? {
        return loadedAdsLiveData[key]
    }

    fun adShownAndShouldBeRemoved(key: String) {
        val removedLiveData = loadedAdsLiveData.remove(key)
        if (removedLiveData != null) {
            Log.d(TAG, "Removed LiveData for ad key after it was shown: $key")
        }
    }
}
