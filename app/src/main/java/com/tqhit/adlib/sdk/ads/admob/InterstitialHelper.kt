package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.tqhit.adlib.sdk.ads.AdFrequencyManager
import com.tqhit.adlib.sdk.ads.callback.admob.InterstitialAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseInterstitialAdCallback
import com.tqhit.adlib.sdk.ads.house.HouseInterstitialHelper
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.ui.dialog.LoadingAdsDialog
import com.tqhit.adlib.sdk.utils.Constant
import com.tqhit.adlib.sdk.utils.NetworkUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.getValue

@Singleton
class InterstitialHelper @Inject constructor(
    private val admobConsentHelper: AdmobConsentHelper,
    private val analyticsTracker: AnalyticsTracker,
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val preferencesHelper: PreferencesHelper,
    private val adFrequencyManager: AdFrequencyManager,
    private val adMobRateLimiter: AdmobRateLimiter,
    private val houseInterstitialHelper: HouseInterstitialHelper
) {
    private val TAG = InterstitialHelper::class.java.simpleName
    private fun isAdEnabled() =
        remoteConfigHelper.getBoolean("iv_enable")
                && !preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)

    private fun isHouseAdsEnabled() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)

    private fun isHouseAutoFallback() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)

    private fun createBridgedHouseCallback(adCallback: InterstitialAdCallback?): HouseInterstitialAdCallback {
        return object : HouseInterstitialAdCallback() {
            override fun onAdImpression() {
                adCallback?.onAdImpression()
            }

            override fun onAdClicked() {
                adCallback?.onAdClicked()
            }

            override fun onAdClosed() {
                adCallback?.onAdClosed()
            }

            override fun onAdFailedToLoad(errorMessage: String) {
                adCallback?.onAdFailedToLoad(null)
            }
        }
    }

    private fun getAdRequest(timeout: Int = 60000): AdRequest {
        return AdRequest.Builder().setHttpTimeoutMillis(timeout).build()
    }

    fun showInterstitial(
        activity: Activity,
        interstitialAdUnitId: String,
        interstitialAd: InterstitialAd?,
        timeoutMilliSecond: Int?,
        adCallback: InterstitialAdCallback?
    ) {
        // If device is offline and House Ads are enabled, show House Interstitial immediately
        if (!NetworkUtils.isNetworkAvailable(activity) && isHouseAdsEnabled()) {
            adCallback?.onHouseAdShown()
            houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
            return
        }

        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            if (isHouseAdsEnabled()) {
                adCallback?.onHouseAdShown()
                houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
            } else {
                adCallback?.onAdClosed()
            }
            return
        }
        
        // Check frequency and delay rules: fallback to House ad if blocked
        if (!adFrequencyManager.canShowInterstitial()) {
            if (isHouseAdsEnabled()) {
                adCallback?.onHouseAdShown()
                houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
            } else {
                adCallback?.onAdClosed()
            }
            return
        }
        
        if (interstitialAd == null) {
            val loadingAdsDialog = LoadingAdsDialog(activity)
            if (!activity.isFinishing && !activity.isDestroyed)
                loadingAdsDialog.show()
            loadInterstitial(activity, interstitialAdUnitId, timeoutMilliSecond, object : InterstitialAdCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    showInterstitial(activity, interstitialAd, adCallback)
                    if (loadingAdsDialog.isShowing) {
                        loadingAdsDialog.dismiss()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError?) {
                    if (loadingAdsDialog.isShowing) {
                        loadingAdsDialog.dismiss()
                    }
                    if (isHouseAutoFallback() && !activity.isFinishing && !activity.isDestroyed) {
                        adCallback?.onHouseAdShown()
                        houseInterstitialHelper.showHouseInterstitial(
                            activity,
                            object : HouseInterstitialAdCallback() {
                                override fun onAdImpression() { adCallback?.onAdImpression() }
                                override fun onAdClicked() { adCallback?.onAdClicked() }
                                override fun onAdClosed() { adCallback?.onAdClosed() }
                                override fun onAdFailedToLoad(errorMessage: String) {
                                    adCallback?.onAdFailedToLoad(adError)
                                    adCallback?.onAdClosed()
                                }
                            },
                            ignoreFrequencyCheck = true
                        )
                    } else {
                        adCallback?.onAdFailedToLoad(adError)
                        adCallback?.onAdClosed()
                    }
                }
            })
        }
        else {
            showInterstitial(activity, interstitialAd, adCallback)
        }
    }

    fun showInterstitial(
        activity: Activity,
        interstitialAd: InterstitialAd,
        adCallback: InterstitialAdCallback?
    ) {
        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            if (isHouseAdsEnabled()) {
                adCallback?.onHouseAdShown()
                houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
            } else {
                adCallback?.onAdClosed()
            }
            return
        }
        
        // Check frequency and delay rules: fallback to House ad if blocked
        if (!adFrequencyManager.canShowInterstitial()) {
            if (isHouseAdsEnabled()) {
                adCallback?.onHouseAdShown()
                houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
            } else {
                adCallback?.onAdClosed()
            }
            return
        }
        
        analyticsTracker.logEvent("aj_inters_show")
        interstitialAd.apply {
            onPaidEventListener = OnPaidEventListener { adValue ->
                analyticsTracker.trackAdMobRevenueEvent(
                    adValue,
                    interstitialAd.adUnitId,
                    interstitialAd.responseInfo.loadedAdapterResponseInfo?.adSourceName
                        ?: "AdMob",
                    "Interstitial"
                )
            }
            fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent()
                    adFrequencyManager.recordInterstitialShown()
                    adCallback?.onAdClosed()
                    analyticsTracker.logEvent("aj_inters_close")
                }

                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    super.onAdFailedToShowFullScreenContent(p0)
                    if (isHouseAutoFallback() && !activity.isFinishing && !activity.isDestroyed) {
                        adCallback?.onHouseAdShown()
                        houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                    } else {
                        adCallback?.onAdClosed()
                    }
                    analyticsTracker.logEvent("aj_inters_show_fail")
                }

                override fun onAdShowedFullScreenContent() {
                    super.onAdShowedFullScreenContent()
                    analyticsTracker.logEvent("aj_inters_show_success")
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    adCallback?.onAdClicked()
                    analyticsTracker.logEvent("aj_inters_click")
                }

                override fun onAdImpression() {
                    super.onAdImpression()
                    adCallback?.onAdImpression()
                }
            }
        }
        interstitialAd.show(activity)
    }
    

    fun loadInterstitial(
        context: Context,
        interstitialAdUnitId: String,
        timeoutMilliSecond: Int?,
        adCallback: InterstitialAdCallback?
    ) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            adCallback?.onAdFailedToLoad(null)
            return
        }

        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            adCallback?.onAdFailedToLoad(null)
            return
        }

        analyticsTracker.logEvent("aj_inters_load")

        val adUnitId = if (Constant.DEBUG_MODE) Constant.ADMOB_INTERSTITIAL_AD_UNIT_ID else interstitialAdUnitId
        if (!adMobRateLimiter.canRequest(adUnitId)) {
            Log.w(TAG, "Interstitial adUnitId $adUnitId is in NO_FILL cooldown")
            val noFillError = LoadAdError(
                com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL,
                "در کولداون NO_FILL",
                "com.google.android.gms.ads",
                null,
                null
            )
            adCallback?.onAdFailedToLoad(noFillError)
            analyticsTracker.logEvent("aj_inters_load_fail_cooldown")
            return
        }

        InterstitialAd.load(context, adUnitId, getAdRequest(timeoutMilliSecond ?: 60000), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                adCallback?.onAdLoaded(interstitialAd)
                analyticsTracker.logEvent("aj_inters_load_success")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                if (adError.code == com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL) {
                    adMobRateLimiter.recordNoFill(adUnitId)
                }
                adCallback?.onAdFailedToLoad(adError)
                analyticsTracker.logEvent("aj_inters_load_fail")
            }
        })
    }
}