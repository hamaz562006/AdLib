package com.tqhit.adlib.sdk.analytics

import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.firebase.analytics.FirebaseAnalytics
import com.tqhit.adlib.sdk.adjust.AdjustAnalyticsHelper
import com.tqhit.adlib.sdk.firebase.FirebaseAnalyticsHelper
import com.tqhit.adlib.sdk.utils.Constant
import javax.inject.Inject
import kotlin.div

class AnalyticsTracker
@Inject
constructor(
        private val firebaseAnalyticsHelper: FirebaseAnalyticsHelper,
        private val adjustAnalyticsHelper: AdjustAnalyticsHelper
) {

    fun logEvent(eventName: String, params: Map<String, Any>? = null) {
        if (Constant.DEBUG_MODE) return
        firebaseAnalyticsHelper.logEvent(eventName, params)
        adjustAnalyticsHelper.trackEvent(eventName)
    }

    fun trackAdMobRevenueEvent(
        adValue: AdValue,
        adUnitId: String,
        adSource: String,
        adFormat: String,
    ) {
        if (Constant.DEBUG_MODE) return
        firebaseAnalyticsHelper.logEvent(
                "ad_impression_custom",
                mapOf(
                        FirebaseAnalytics.Param.AD_PLATFORM to "admob",
                        FirebaseAnalytics.Param.AD_UNIT_NAME to adUnitId,
                        FirebaseAnalytics.Param.AD_SOURCE to adSource,
                        FirebaseAnalytics.Param.AD_FORMAT to adFormat,
                        FirebaseAnalytics.Param.VALUE to adValue.valueMicros.toDouble() / 1000000.0,
                        FirebaseAnalytics.Param.CURRENCY to "USD"
                )
        )
        // https://dev.adjust.com/en/sdk/android/features/ad-revenue/
        adjustAnalyticsHelper.trackRevenueEvent(
            adValue.valueMicros / 1000000.0,
            adValue.currencyCode,
            "admob_sdk")
    }

    fun trackHouseAdImpression(adId: String, adType: String) {
        if (Constant.DEBUG_MODE) return
        firebaseAnalyticsHelper.logEvent(
            "house_ad_impression",
            mapOf(
                "house_ad_id" to adId,
                "house_ad_type" to adType
            )
        )
        adjustAnalyticsHelper.trackEvent("house_ad_impression_${adType.lowercase()}")
    }

    fun trackHouseAdClick(adId: String, adType: String, targetPackageOrUrl: String) {
        if (Constant.DEBUG_MODE) return
        firebaseAnalyticsHelper.logEvent(
            "house_ad_click",
            mapOf(
                "house_ad_id" to adId,
                "house_ad_type" to adType,
                "house_ad_target" to targetPackageOrUrl
            )
        )
        adjustAnalyticsHelper.trackEvent("house_ad_click_${adType.lowercase()}")
    }
}
