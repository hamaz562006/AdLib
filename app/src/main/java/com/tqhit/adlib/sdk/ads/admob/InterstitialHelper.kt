package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

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
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun isAdEnabled() =
        remoteConfigHelper.getBoolean("iv_enable")
                && !preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)

    private fun isHouseAdsEnabled() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)

    private fun isHouseAutoFallback() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun createBridgedHouseCallback(adCallback: InterstitialAdCallback?): HouseInterstitialAdCallback {
        return object : HouseInterstitialAdCallback() {
            override fun onAdImpression() {
                runOnUiThread { adCallback?.onAdImpression() }
            }

            override fun onAdClicked() {
                runOnUiThread { adCallback?.onAdClicked() }
            }

            override fun onAdClosed() {
                runOnUiThread { adCallback?.onAdClosed() }
            }

            override fun onAdFailedToLoad(errorMessage: String) {
                runOnUiThread { adCallback?.onAdFailedToLoad(null) }
            }
        }
    }

    private fun getAdRequest(adUnitId: String): AdRequest {
        return AdRequest.Builder(adUnitId).build()
    }

    fun showInterstitial(
        activity: Activity,
        interstitialAdUnitId: String,
        interstitialAd: InterstitialAd?,
        timeoutMilliSecond: Int?,
        adCallback: InterstitialAdCallback?
    ) {
        // If device is offline and House Ads are enabled, show House Interstitial immediately
        if (!NetworkUtils.isNetworkAvailable(activity)) {
            if (isHouseAdsEnabled()) {
                runOnUiThread {
                    adCallback?.onHouseAdShown("Network unavailable")
                    houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                }
            } else {
                runOnUiThread { adCallback?.onAdClosed() }
            }
            return
        }

        if (interstitialAd != null) {
            showInterstitial(activity, interstitialAd, adCallback)
            return
        }

        // For loading and showing AdMob interstitial: pre-check reachability if house ads enabled
        if (isHouseAdsEnabled()) {
            CoroutineScope(Dispatchers.Main).launch {
                val reachable = NetworkUtils.isAdServerReachable()
                if (!reachable && !activity.isFinishing && !activity.isDestroyed) {
                    adCallback?.onHouseAdShown("Ad server unreachable (possibly blocked network)")
                    houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                } else if (!activity.isFinishing && !activity.isDestroyed) {
                    executeShowInterstitial(activity, interstitialAdUnitId, null, timeoutMilliSecond, adCallback)
                }
            }
        } else {
            executeShowInterstitial(activity, interstitialAdUnitId, null, timeoutMilliSecond, adCallback)
        }
    }

    private fun executeShowInterstitial(
        activity: Activity,
        interstitialAdUnitId: String,
        interstitialAd: InterstitialAd?,
        timeoutMilliSecond: Int?,
        adCallback: InterstitialAdCallback?
    ) {
        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            if (isHouseAdsEnabled()) {
                runOnUiThread {
                    adCallback?.onHouseAdShown("AdMob disabled or consent missing")
                    houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                }
            } else {
                runOnUiThread { adCallback?.onAdClosed() }
            }
            return
        }

        // Check frequency and delay rules: fallback to House ad if blocked
        if (!adFrequencyManager.canShowInterstitial()) {
            if (isHouseAdsEnabled()) {
                runOnUiThread {
                    adCallback?.onHouseAdShown("Frequency capped")
                    houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                }
            } else {
                runOnUiThread { adCallback?.onAdClosed() }
            }
            return
        }

        if (interstitialAd == null) {
            val loadingAdsDialog = LoadingAdsDialog(activity)
            if (!activity.isFinishing && !activity.isDestroyed) {
                loadingAdsDialog.show()
            }
            loadInterstitial(activity, interstitialAdUnitId, timeoutMilliSecond, object : InterstitialAdCallback() {
                override fun onAdLoaded(loadedInterstitialAd: InterstitialAd) {
                    runOnUiThread {
                        if (loadingAdsDialog.isShowing) {
                            loadingAdsDialog.dismiss()
                        }
                        showInterstitial(activity, loadedInterstitialAd, adCallback)
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError?) {
                    runOnUiThread {
                        if (loadingAdsDialog.isShowing) {
                            loadingAdsDialog.dismiss()
                        }
                        if (isHouseAutoFallback() && !activity.isFinishing && !activity.isDestroyed) {
                            val reason = adError?.message ?: adError?.code?.toString() ?: "Unknown AdMob error"
                            adCallback?.onHouseAdShown(reason)
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
                }
            })
        } else {
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
                runOnUiThread {
                    adCallback?.onHouseAdShown("AdMob disabled or consent missing")
                    houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                }
            } else {
                runOnUiThread { adCallback?.onAdClosed() }
            }
            return
        }

        // Check frequency and delay rules: fallback to House ad if blocked
        if (!adFrequencyManager.canShowInterstitial()) {
            if (isHouseAdsEnabled()) {
                runOnUiThread {
                    adCallback?.onHouseAdShown("Frequency capped")
                    houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                }
            } else {
                runOnUiThread { adCallback?.onAdClosed() }
            }
            return
        }

        analyticsTracker.logEvent("aj_inters_show")
        interstitialAd.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                runOnUiThread {
                    adFrequencyManager.recordInterstitialShown()
                    adCallback?.onAdClosed()
                    analyticsTracker.logEvent("aj_inters_close")
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                runOnUiThread {
                    if (isHouseAutoFallback() && !activity.isFinishing && !activity.isDestroyed) {
                        val reason = error.message.ifBlank { error.code.toString() }
                        adCallback?.onHouseAdShown(reason)
                        houseInterstitialHelper.showHouseInterstitial(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                    } else {
                        adCallback?.onAdClosed()
                    }
                    analyticsTracker.logEvent("aj_inters_show_fail")
                }
            }

            override fun onAdShowedFullScreenContent() {
                runOnUiThread {
                    analyticsTracker.logEvent("aj_inters_show_success")
                }
            }

            override fun onAdClicked() {
                runOnUiThread {
                    adCallback?.onAdClicked()
                    analyticsTracker.logEvent("aj_inters_click")
                }
            }

            override fun onAdImpression() {
                runOnUiThread {
                    adCallback?.onAdImpression()
                }
            }

            override fun onAdPaid(adValue: AdValue) {
                analyticsTracker.trackAdMobRevenueEvent(
                    adValue,
                    interstitialAd.adUnitId,
                    interstitialAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "AdMob",
                    "Interstitial"
                )
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
            runOnUiThread { adCallback?.onAdFailedToLoad(null) }
            return
        }

        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            runOnUiThread { adCallback?.onAdFailedToLoad(null) }
            return
        }

        analyticsTracker.logEvent("aj_inters_load")

        val adUnitId = if (Constant.DEBUG_MODE) Constant.ADMOB_INTERSTITIAL_AD_UNIT_ID else interstitialAdUnitId
        if (!adMobRateLimiter.canRequest(adUnitId)) {
            Log.w(TAG, "Interstitial adUnitId $adUnitId is in NO_FILL cooldown")
            val noFillError = LoadAdError(
                LoadAdError.ErrorCode.NO_FILL,
                "در کولداون NO_FILL",
                null
            )
            runOnUiThread {
                adCallback?.onAdFailedToLoad(noFillError)
                analyticsTracker.logEvent("aj_inters_load_fail_cooldown")
            }
            return
        }

        val adRequest = getAdRequest(adUnitId)
        InterstitialAd.load(adRequest, object : AdLoadCallback<InterstitialAd> {
            override fun onAdLoaded(ad: InterstitialAd) {
                runOnUiThread {
                    adCallback?.onAdLoaded(ad)
                    analyticsTracker.logEvent("aj_inters_load_success")
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                if (adError.code == LoadAdError.ErrorCode.NO_FILL) {
                    adMobRateLimiter.recordNoFill(adUnitId)
                }
                runOnUiThread {
                    adCallback?.onAdFailedToLoad(adError)
                    analyticsTracker.logEvent("aj_inters_load_fail")
                }
            }
        })
    }
}
