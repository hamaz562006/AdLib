package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.tqhit.adlib.sdk.ads.callback.admob.BannerAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseBannerAdCallback
import com.tqhit.adlib.sdk.ads.house.HouseBannerHelper
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.utils.Constant
import com.tqhit.adlib.sdk.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BannerHelper @Inject constructor(
    private val admobConsentHelper: AdmobConsentHelper,
    private val analyticsTracker: AnalyticsTracker,
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val preferencesHelper: PreferencesHelper,
    private val houseBannerHelper: HouseBannerHelper
) {
    private fun isAdEnabled() =
        remoteConfigHelper.getBoolean("bn_enable")
                && !preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)

    private fun getAdSize(activity: Activity): AdSize {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        val i = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, i)
    }

    private fun getCollapsibleAdRequest(adUnitId: String, adSize: AdSize): BannerAdRequest {
        val bundle = Bundle()
        bundle.putString("collapsible", "bottom")
        bundle.putString("collapsible_request_id", UUID.randomUUID().toString())
        return BannerAdRequest.Builder(adUnitId, adSize)
            .setGoogleExtrasBundle(bundle)
            .build()
    }

    private fun getAdRequest(adUnitId: String, adSize: AdSize): BannerAdRequest {
        return BannerAdRequest.Builder(adUnitId, adSize).build()
    }

    fun showCollapsibleBanner(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ) {
        val adView = loadCollapsibleBanner(activity, bannerAdUnitId, timeoutMilliSecond, adCallback)
            ?: return
        parent.removeAllViews()
        parent.addView(adView)
    }

    fun showCollapsibleBanner(
        adView: AdView,
        parent: ViewGroup
    ) {
        parent.removeAllViews()
        parent.addView(adView)
    }

    fun showBanner(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ) {
        val adView = loadBanner(activity, bannerAdUnitId, timeoutMilliSecond, adCallback)
            ?: return
        parent.removeAllViews()
        parent.addView(adView)
    }

    fun showBanner(
        adView: AdView,
        parent: ViewGroup
    ) {
        parent.removeAllViews()
        parent.addView(adView)
    }

    fun showBannerWithFallback(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ) {
        if (!NetworkUtils.isNetworkAvailable(activity)) {
            if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
                activity.runOnUiThread {
                    adCallback?.onHouseAdShown("Network unavailable")
                    houseBannerHelper.loadHouseBanner(activity, parent, createBridgedBannerCallback(activity, adCallback))
                }
            } else {
                activity.runOnUiThread {
                    adCallback?.onAdFailedToLoad(null)
                }
            }
            return
        }

        if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
            CoroutineScope(Dispatchers.Main).launch {
                val reachable = NetworkUtils.isAdServerReachable()
                if (!reachable && !activity.isFinishing && !activity.isDestroyed) {
                    activity.runOnUiThread {
                        adCallback?.onHouseAdShown("Ad server unreachable (possibly blocked network)")
                        houseBannerHelper.loadHouseBanner(activity, parent, createBridgedBannerCallback(activity, adCallback))
                    }
                } else if (!activity.isFinishing && !activity.isDestroyed) {
                    executeLoadBannerWithFallback(activity, bannerAdUnitId, parent, timeoutMilliSecond, adCallback)
                }
            }
        } else {
            executeLoadBannerWithFallback(activity, bannerAdUnitId, parent, timeoutMilliSecond, adCallback)
        }
    }

    private fun executeLoadBannerWithFallback(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ) {
        loadBanner(activity, bannerAdUnitId, timeoutMilliSecond, object : BannerAdCallback() {
            override fun onAdLoaded(adView: AdView) {
                activity.runOnUiThread {
                    parent.removeAllViews()
                    parent.addView(adView)
                    adCallback?.onAdLoaded(adView)
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError?) {
                if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                    val reason = adError?.message ?: adError?.code?.toString() ?: "Unknown AdMob error"
                    activity.runOnUiThread {
                        adCallback?.onHouseAdShown(reason)
                        houseBannerHelper.loadHouseBanner(activity, parent, createBridgedBannerCallback(activity, adCallback))
                    }
                } else {
                    activity.runOnUiThread {
                        adCallback?.onAdFailedToLoad(adError)
                    }
                }
            }

            override fun onAdClicked() {
                activity.runOnUiThread { adCallback?.onAdClicked() }
            }

            override fun onAdImpression() {
                activity.runOnUiThread { adCallback?.onAdImpression() }
            }

            override fun onAdClosed() {
                activity.runOnUiThread { adCallback?.onAdClosed() }
            }

            override fun onDiagnosticInfo(message: String) {
                activity.runOnUiThread { adCallback?.onDiagnosticInfo(message) }
            }
        })
    }

    private fun createBridgedBannerCallback(activity: Activity, adCallback: BannerAdCallback?): HouseBannerAdCallback {
        return object : HouseBannerAdCallback() {
            override fun onAdImpression() {
                activity.runOnUiThread { adCallback?.onAdImpression() }
            }

            override fun onAdClicked() {
                activity.runOnUiThread { adCallback?.onAdClicked() }
            }

            override fun onAdClosed() {
                activity.runOnUiThread { adCallback?.onAdClosed() }
            }

            override fun onAdFailedToLoad(errorMessage: String) {
                activity.runOnUiThread { adCallback?.onAdFailedToLoad(null) }
            }
        }
    }

    fun showCollapsibleBannerWithFallback(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ) {
        if (!NetworkUtils.isNetworkAvailable(activity)) {
            if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
                activity.runOnUiThread {
                    adCallback?.onHouseAdShown("Network unavailable")
                    houseBannerHelper.loadHouseBanner(activity, parent, createBridgedBannerCallback(activity, adCallback))
                }
            } else {
                activity.runOnUiThread {
                    adCallback?.onAdFailedToLoad(null)
                }
            }
            return
        }

        if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
            CoroutineScope(Dispatchers.Main).launch {
                val reachable = NetworkUtils.isAdServerReachable()
                if (!reachable && !activity.isFinishing && !activity.isDestroyed) {
                    activity.runOnUiThread {
                        adCallback?.onHouseAdShown("Ad server unreachable (possibly blocked network)")
                        houseBannerHelper.loadHouseBanner(activity, parent, createBridgedBannerCallback(activity, adCallback))
                    }
                } else if (!activity.isFinishing && !activity.isDestroyed) {
                    executeLoadCollapsibleBannerWithFallback(activity, bannerAdUnitId, parent, timeoutMilliSecond, adCallback)
                }
            }
        } else {
            executeLoadCollapsibleBannerWithFallback(activity, bannerAdUnitId, parent, timeoutMilliSecond, adCallback)
        }
    }

    private fun executeLoadCollapsibleBannerWithFallback(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ) {
        loadCollapsibleBanner(activity, bannerAdUnitId, timeoutMilliSecond, object : BannerAdCallback() {
            override fun onAdLoaded(adView: AdView) {
                activity.runOnUiThread {
                    parent.removeAllViews()
                    parent.addView(adView)
                    adCallback?.onAdLoaded(adView)
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError?) {
                if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                    val reason = adError?.message ?: adError?.code?.toString() ?: "Unknown AdMob error"
                    activity.runOnUiThread {
                        adCallback?.onHouseAdShown(reason)
                        houseBannerHelper.loadHouseBanner(activity, parent, createBridgedBannerCallback(activity, adCallback))
                    }
                } else {
                    activity.runOnUiThread {
                        adCallback?.onAdFailedToLoad(adError)
                    }
                }
            }

            override fun onAdClicked() {
                activity.runOnUiThread { adCallback?.onAdClicked() }
            }

            override fun onAdImpression() {
                activity.runOnUiThread { adCallback?.onAdImpression() }
            }

            override fun onAdClosed() {
                activity.runOnUiThread { adCallback?.onAdClosed() }
            }

            override fun onDiagnosticInfo(message: String) {
                activity.runOnUiThread { adCallback?.onDiagnosticInfo(message) }
            }
        })
    }

    fun loadBanner(
        activity: Activity,
        bannerAdUnitId: String,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ): AdView? {
        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            activity.runOnUiThread { adCallback?.onAdFailedToLoad(null) }
            return null
        }
        analyticsTracker.logEvent("aj_banner_load")
        val adView = AdView(activity)
        val effectiveAdUnitId = if (Constant.DEBUG_MODE) Constant.ADMOB_BANNER_AD_UNIT_ID else bannerAdUnitId
        val adSize = getAdSize(activity)
        android.util.Log.d("ADLIB_DIAGNOSTIC", "Computed AdSize: width=${adSize.width}, height=${adSize.height}")
        adCallback?.onDiagnosticInfo("Computed AdSize: width=${adSize.width}, height=${adSize.height}")
        val adRequest = getAdRequest(effectiveAdUnitId, adSize)

        adView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        adView.loadAd(adRequest, object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(bannerAd: BannerAd) {
                bannerAd.adEventCallback = object : BannerAdEventCallback {
                    override fun onAdClicked() {
                        activity.runOnUiThread {
                            adCallback?.onAdClicked()
                            analyticsTracker.logEvent("aj_banner_click")
                        }
                    }

                    override fun onAdImpression() {
                        activity.runOnUiThread {
                            adCallback?.onAdImpression()
                        }
                    }

                    override fun onAdShowedFullScreenContent() {
                        activity.runOnUiThread {
                            adCallback?.onAdOpened()
                            analyticsTracker.logEvent("aj_banner_show_success")
                        }
                    }

                    override fun onAdDismissedFullScreenContent() {
                        activity.runOnUiThread {
                            adCallback?.onAdClosed()
                            analyticsTracker.logEvent("aj_banner_close")
                        }
                    }

                    override fun onAdPaid(adValue: AdValue) {
                        analyticsTracker.trackAdMobRevenueEvent(
                            adValue,
                            effectiveAdUnitId,
                            bannerAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "AdMob",
                            "Banner"
                        )
                    }
                }

                activity.runOnUiThread {
                    adCallback?.onAdLoaded(adView)
                    analyticsTracker.logEvent("aj_banner_load_success")
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                activity.runOnUiThread {
                    adCallback?.onAdFailedToLoad(loadAdError)
                    analyticsTracker.logEvent("aj_banner_load_fail", mapOf(
                        "ad_unit_id" to bannerAdUnitId,
                        "ad_error_message" to loadAdError.message
                    ))
                }
            }
        })

        return adView
    }

    fun loadCollapsibleBanner(
        activity: Activity,
        bannerAdUnitId: String,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ): AdView? {
        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            activity.runOnUiThread { adCallback?.onAdFailedToLoad(null) }
            return null
        }
        analyticsTracker.logEvent("aj_banner_load")
        val adView = AdView(activity)
        val effectiveAdUnitId = if (Constant.DEBUG_MODE) Constant.ADMOB_COLLAPSIBLE_BANNER_AD_UNIT_ID else bannerAdUnitId
        val adSize = getAdSize(activity)
        android.util.Log.d("ADLIB_DIAGNOSTIC", "Computed AdSize: width=${adSize.width}, height=${adSize.height}")
        adCallback?.onDiagnosticInfo("Computed AdSize: width=${adSize.width}, height=${adSize.height}")
        val adRequest = getCollapsibleAdRequest(effectiveAdUnitId, adSize)

        adView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        adView.loadAd(adRequest, object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(bannerAd: BannerAd) {
                bannerAd.adEventCallback = object : BannerAdEventCallback {
                    override fun onAdClicked() {
                        activity.runOnUiThread {
                            adCallback?.onAdClicked()
                            analyticsTracker.logEvent("aj_banner_click")
                        }
                    }

                    override fun onAdImpression() {
                        activity.runOnUiThread {
                            adCallback?.onAdImpression()
                        }
                    }

                    override fun onAdShowedFullScreenContent() {
                        activity.runOnUiThread {
                            adCallback?.onAdOpened()
                            analyticsTracker.logEvent("aj_banner_show_success")
                        }
                    }

                    override fun onAdDismissedFullScreenContent() {
                        activity.runOnUiThread {
                            adCallback?.onAdClosed()
                            analyticsTracker.logEvent("aj_banner_close")
                        }
                    }

                    override fun onAdPaid(adValue: AdValue) {
                        analyticsTracker.trackAdMobRevenueEvent(
                            adValue,
                            effectiveAdUnitId,
                            bannerAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "AdMob",
                            "Banner"
                        )
                    }
                }

                activity.runOnUiThread {
                    adCallback?.onAdLoaded(adView)
                    analyticsTracker.logEvent("aj_banner_load_success")
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                activity.runOnUiThread {
                    adCallback?.onAdFailedToLoad(loadAdError)
                    analyticsTracker.logEvent("aj_banner_load_fail", mapOf(
                        "ad_unit_id" to bannerAdUnitId,
                        "ad_error_message" to loadAdError.message
                    ))
                }
            }
        })

        return adView
    }
}
