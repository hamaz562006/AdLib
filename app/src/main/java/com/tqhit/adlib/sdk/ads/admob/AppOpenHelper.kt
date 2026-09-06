package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.ads.AdActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.appopen.AppOpenAd
import com.tqhit.adlib.sdk.ads.AdFrequencyManager
import com.tqhit.adlib.sdk.ads.house.HouseAppOpenHelper
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.utils.Constant
import com.tqhit.adlib.sdk.utils.NetworkUtils
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenHelper @Inject constructor(
    private val admobConsentHelper: AdmobConsentHelper,
    private val analyticsTracker: AnalyticsTracker,
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val preferencesHelper: PreferencesHelper,
    private val adFrequencyManager: AdFrequencyManager,
    private val adMobRateLimiter: AdmobRateLimiter,
    private val houseAppOpenHelper: HouseAppOpenHelper
) {
    private fun isAdEnabled() =
        remoteConfigHelper.getBoolean("aoa_enable")
                && !preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)

    private fun isHouseAdsEnabled() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)

    private fun isHouseAutoFallback() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)

    private var loadTime: Long = 0
    private var adUnitId = ""
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false

    val adLoaded = MutableLiveData<Boolean>()

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
        fun onHouseAdShown() {}
    }

    fun setAdUnitId(adUnitId: String) {
        this.adUnitId = adUnitId
    }

    fun loadAd(context: Context) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            adLoaded.postValue(false)
            return
        }

        if (!isAdEnabled()) return
        if (!admobConsentHelper.canRequestAds()) return
        if (isLoadingAd || isAdAvailable()) return

        val targetAdUnitId = if (Constant.DEBUG_MODE) Constant.ADMOB_AOA_AD_UNIT_ID else adUnitId
        if (!adMobRateLimiter.canRequest(targetAdUnitId)) {
            android.util.Log.w("AppOpenHelper", "AppOpen adUnitId $targetAdUnitId is in NO_FILL cooldown")
            adLoaded.postValue(false)
            analyticsTracker.logEvent("aj_app_open_load_fail_cooldown")
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder().build()
        analyticsTracker.logEvent("aj_app_open_load")
        AppOpenAd.load(
            context, targetAdUnitId, request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    super.onAdLoaded(ad)
                    analyticsTracker.logEvent("aj_app_open_load_success")
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    appOpenAd?.onPaidEventListener = OnPaidEventListener { adValue ->
                        analyticsTracker.trackAdMobRevenueEvent(
                            adValue,
                            ad.adUnitId,
                            appOpenAd?.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "AdMob",
                            "AOA"
                        )
                    }
                    adLoaded.postValue(true)
                }
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    if (loadAdError.code == com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL) {
                        adMobRateLimiter.recordNoFill(targetAdUnitId)
                    }
                    analyticsTracker.logEvent("aj_app_open_load_fail")
                    isLoadingAd = false
                    adLoaded.postValue(false)
                }
            }
        )
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    /** Check if ad exists and can be shown.  */
    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    /** Shows the ad if one isn't already showing.  */
    fun showAdIfAvailable(activity: Activity, adCallback: OnShowAdCompleteListener) {
        if (!isAdEnabled() || isShowingAd) {
            adCallback.onShowAdComplete()
            return
        }

        // If device is offline and House Ads enabled, show House App Open immediately
        if (!NetworkUtils.isNetworkAvailable(activity) && isHouseAdsEnabled()) {
            adCallback.onHouseAdShown()
            houseAppOpenHelper.showHouseAppOpen(
                activity,
                object : HouseAppOpenHelper.OnShowAdCompleteListener {
                    override fun onShowAdComplete() {
                        adCallback.onShowAdComplete()
                    }
                },
                ignoreFrequencyCheck = true
            )
            return
        }

        if (!isAdAvailable()) {
            if (isHouseAutoFallback()) {
                adCallback.onHouseAdShown()
                houseAppOpenHelper.showHouseAppOpen(
                    activity,
                    object : HouseAppOpenHelper.OnShowAdCompleteListener {
                        override fun onShowAdComplete() {
                            adCallback.onShowAdComplete()
                        }
                    },
                    ignoreFrequencyCheck = true
                )
            } else {
                adCallback.onShowAdComplete()
            }
            loadAd(activity)
            return
        }

        // Frequency gating via AdFrequencyManager: fallback to House App Open if blocked
        if (!adFrequencyManager.canShowAppOpen()) {
            if (isHouseAdsEnabled()) {
                adCallback.onHouseAdShown()
                houseAppOpenHelper.showHouseAppOpen(
                    activity,
                    object : HouseAppOpenHelper.OnShowAdCompleteListener {
                        override fun onShowAdComplete() {
                            adCallback.onShowAdComplete()
                        }
                    },
                    ignoreFrequencyCheck = true
                )
            } else {
                adCallback.onShowAdComplete()
            }
            return
        }

        analyticsTracker.logEvent("aj_app_open_show")
        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                super.onAdDismissedFullScreenContent()
                analyticsTracker.logEvent("aj_app_open_close")
                appOpenAd = null
                isShowingAd = false
                adLoaded.postValue(false)
                adFrequencyManager.recordAppOpenShown()
                adCallback.onShowAdComplete()
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                super.onAdFailedToShowFullScreenContent(adError)
                analyticsTracker.logEvent("aj_app_open_show_fail")
                appOpenAd = null
                isShowingAd = false
                adLoaded.postValue(false)
                adFrequencyManager.recordAppOpenShown()
                if (isHouseAutoFallback() && !activity.isFinishing && !activity.isDestroyed) {
                    adCallback.onHouseAdShown()
                    houseAppOpenHelper.showHouseAppOpen(
                        activity,
                        object : HouseAppOpenHelper.OnShowAdCompleteListener {
                            override fun onShowAdComplete() {
                                adCallback.onShowAdComplete()
                            }
                        },
                        ignoreFrequencyCheck = true
                    )
                } else {
                    adCallback.onShowAdComplete()
                }
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                super.onAdShowedFullScreenContent()
                analyticsTracker.logEvent("aj_app_open_show_success")
            }

            override fun onAdClicked() {
                super.onAdClicked()
                analyticsTracker.logEvent("aj_app_open_click")
            }
        }
        isShowingAd = true
        appOpenAd?.show(activity)
    }
}