package com.tqhit.adlib.sdk.ads.house

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tqhit.adlib.sdk.ads.house.model.HouseAdItem
import com.tqhit.adlib.sdk.ads.house.model.HouseAdType
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.utils.Constant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseAdManager @Inject constructor(
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val analyticsTracker: AnalyticsTracker
) {
    companion object {
        private const val TAG = "HouseAdManager"
    }

    private val defaultAds = listOf(
        HouseAdItem(
            id = "house_tool_pro",
            title = "Cleaner & Battery Master Pro",
            description = "Optimize storage and accelerate phone performance effortlessly.",
            ctaText = "Get on Google Play",
            targetUrl = "https://play.google.com/store/apps/details?id=com.google.android.apps.nbu.files",
            targetPackageName = "com.google.android.apps.nbu.files",
            rating = 4.9f,
            rewardAmount = 25,
            rewardType = "Tokens",
            adType = HouseAdType.INTERSTITIAL
        ),
        HouseAdItem(
            id = "house_security_shield",
            title = "Security & Privacy Guard",
            description = "Real-time threat detection and ultimate browser protection.",
            ctaText = "Install Free",
            targetUrl = "https://play.google.com/store",
            targetPackageName = null,
            rating = 4.8f,
            rewardAmount = 15,
            rewardType = "Tokens",
            adType = HouseAdType.BANNER
        ),
        HouseAdItem(
            id = "house_speed_booster",
            title = "Smart File Manager",
            description = "Manage, extract, and organize all your files with lightning speed.",
            ctaText = "Download Now",
            targetUrl = "https://play.google.com/store",
            targetPackageName = null,
            rating = 4.7f,
            rewardAmount = 30,
            rewardType = "Coins",
            adType = HouseAdType.REWARDED
        )
    )

    private var currentBannerIndex = 0
    private var currentInterstitialIndex = 0
    private var currentNativeIndex = 0
    private var currentRewardIndex = 0
    private var currentAppOpenIndex = 0

    fun isHouseAdsEnabled(): Boolean {
        // Default to true if not specified in Remote Config
        return remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)
    }

    fun isAutoFallbackEnabled(): Boolean {
        // Default to true
        return remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)
    }

    fun getHouseAds(): List<HouseAdItem> {
        val jsonConfig = remoteConfigHelper.getString(Constant.RC_HOUSE_ADS_JSON)
        if (jsonConfig.isNotBlank()) {
            try {
                val listType = object : TypeToken<List<HouseAdItem>>() {}.type
                val parsed: List<HouseAdItem>? = Gson().fromJson(jsonConfig, listType)
                if (!parsed.isNullOrEmpty()) {
                    return parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse Remote Config house ads JSON: ${e.message}")
            }
        }
        return defaultAds
    }

    fun getNextAd(type: HouseAdType): HouseAdItem {
        val ads = getHouseAds().filter { it.adType == type || it.adType == HouseAdType.BANNER }
        val pool = if (ads.isNotEmpty()) ads else defaultAds
        val index = when (type) {
            HouseAdType.BANNER -> (currentBannerIndex++) % pool.size
            HouseAdType.INTERSTITIAL -> (currentInterstitialIndex++) % pool.size
            HouseAdType.NATIVE -> (currentNativeIndex++) % pool.size
            HouseAdType.REWARDED -> (currentRewardIndex++) % pool.size
            HouseAdType.APP_OPEN -> (currentAppOpenIndex++) % pool.size
        }
        return pool[index]
    }

    fun recordImpression(adItem: HouseAdItem) {
        Log.d(TAG, "House Ad impression: ${adItem.id} (${adItem.adType})")
        analyticsTracker.trackHouseAdImpression(adItem.id, adItem.adType.name)
    }

    fun recordClick(context: Context, adItem: HouseAdItem) {
        Log.d(TAG, "House Ad clicked: ${adItem.id} -> ${adItem.targetUrl}")
        analyticsTracker.trackHouseAdClick(
            adItem.id,
            adItem.adType.name,
            adItem.targetPackageName ?: adItem.targetUrl
        )

        // Navigate user
        try {
            if (!adItem.targetPackageName.isNullOrBlank()) {
                val marketIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=${adItem.targetPackageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
                return
            }
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "Play store app not found, falling back to web URL")
        }

        try {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(adItem.targetUrl)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open house ad target URL: ${e.message}")
        }
    }
}
