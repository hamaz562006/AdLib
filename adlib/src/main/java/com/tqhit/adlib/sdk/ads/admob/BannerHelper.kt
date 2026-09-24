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
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import com.facebook.shimmer.ShimmerFrameLayout
import com.tqhit.adlib.R
import com.tqhit.adlib.sdk.ads.callback.admob.BannerAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseBannerAdCallback
import com.tqhit.adlib.sdk.ads.house.HouseBannerHelper
import com.tqhit.adlib.sdk.ads.house.model.HouseAdItem
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.utils.Constant
import com.tqhit.adlib.sdk.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BannerHelper @Inject constructor(
    private val admobConsentHelper: AdmobConsentHelper,
    private val analyticsTracker: AnalyticsTracker,
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val preferencesHelper: PreferencesHelper,
    private val adMobRateLimiter: AdmobRateLimiter,
    private val houseBannerHelper: HouseBannerHelper
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun showShimmer(activity: Activity, parent: ViewGroup): View? {
        return try {
            val shimmerView = LayoutInflater.from(activity).inflate(R.layout.ad_banner_shimmer, parent, false)
            val shimmer = shimmerView.findViewById<ShimmerFrameLayout>(R.id.shimmer_container_banner)
                ?: (shimmerView as? ShimmerFrameLayout)
            parent.removeAllViews()
            parent.addView(shimmerView)
            shimmer?.startShimmer()
            shimmerView
        } catch (e: Exception) {
            null
        }
    }

    private fun stopAndRemoveShimmer(parent: ViewGroup, shimmerView: View?) {
        shimmerView?.let { view ->
            val shimmer = view.findViewById<ShimmerFrameLayout>(R.id.shimmer_container_banner)
                ?: (view as? ShimmerFrameLayout)
            shimmer?.stopShimmer()
            parent.removeView(view)
        }
    }
    private fun isAdEnabled(): Boolean {
        if (preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)) return false
        if (Constant.DEBUG_MODE) return true
        return remoteConfigHelper.getBoolean("bn_enable")
    }

    /**
     * Resolves the effective ad unit ID: a matching Remote Config key takes priority (so a
     * production ad unit ID can be swapped after publishing without a new release); falls back
     * to the ID passed in code if the RC key is missing/blank.
     */
    private fun resolveAdUnitId(rcKey: String, fallback: String): String {
        if (Constant.DEBUG_MODE) {
            return fallback
        }
        val rcValue = remoteConfigHelper.getString(rcKey)
        return if (rcValue.isNotBlank()) rcValue else fallback
    }

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
        val shimmerView = showShimmer(activity, parent)

        if (!NetworkUtils.isNetworkAvailable(activity)) {
            if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
                activity.runOnUiThread {
                    adCallback?.onHouseAdShown("Network unavailable")
                    houseBannerHelper.loadHouseBanner(
                        activity,
                        parent,
                        createBridgedBannerCallback(activity, parent, shimmerView, adCallback)
                    )
                }
            } else {
                activity.runOnUiThread {
                    stopAndRemoveShimmer(parent, shimmerView)
                    adCallback?.onAdFailedToLoad(null)
                }
            }
            return
        }

        executeLoadBannerWithFallback(activity, bannerAdUnitId, parent, timeoutMilliSecond, adCallback, shimmerView)
    }

    private fun executeLoadBannerWithFallback(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?,
        shimmerView: View?
    ) {
        val effectiveAdUnitId = resolveAdUnitId(Constant.RC_BN_AD_UNIT_ID, bannerAdUnitId)
        if (!adMobRateLimiter.canRequest(effectiveAdUnitId)) {
            if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                activity.runOnUiThread {
                    adCallback?.onHouseAdShown("In NO_FILL cooldown")
                    houseBannerHelper.loadHouseBanner(
                        activity,
                        parent,
                        createBridgedBannerCallback(activity, parent, shimmerView, adCallback)
                    )
                }
            } else {
                activity.runOnUiThread {
                    stopAndRemoveShimmer(parent, shimmerView)
                    val cooldownError = LoadAdError(LoadAdError.ErrorCode.NO_FILL, "In NO_FILL cooldown", null)
                    adCallback?.onAdFailedToLoad(cooldownError)
                }
            }
            return
        }

        loadBanner(activity, bannerAdUnitId, timeoutMilliSecond, object : BannerAdCallback() {
            override fun onAdLoaded(adView: AdView) {
                activity.runOnUiThread {
                    stopAndRemoveShimmer(parent, shimmerView)
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
                        houseBannerHelper.loadHouseBanner(
                            activity,
                            parent,
                            createBridgedBannerCallback(activity, parent, shimmerView, adCallback)
                        )
                    }
                } else {
                    activity.runOnUiThread {
                        stopAndRemoveShimmer(parent, shimmerView)
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

    private fun createBridgedBannerCallback(
        activity: Activity,
        parent: ViewGroup,
        shimmerView: View?,
        adCallback: BannerAdCallback?
    ): HouseBannerAdCallback {
        return object : HouseBannerAdCallback() {
            override fun onAdLoaded(houseAdItem: HouseAdItem) {
                activity.runOnUiThread {
                    stopAndRemoveShimmer(parent, shimmerView)
                }
            }

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
                activity.runOnUiThread {
                    stopAndRemoveShimmer(parent, shimmerView)
                    adCallback?.onAdFailedToLoad(null)
                }
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
        val shimmerView = showShimmer(activity, parent)

        if (!NetworkUtils.isNetworkAvailable(activity)) {
            if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
                activity.runOnUiThread {
                    adCallback?.onHouseAdShown("Network unavailable")
                    houseBannerHelper.loadHouseBanner(
                        activity,
                        parent,
                        createBridgedBannerCallback(activity, parent, shimmerView, adCallback)
                    )
                }
            } else {
                activity.runOnUiThread {
                    stopAndRemoveShimmer(parent, shimmerView)
                    adCallback?.onAdFailedToLoad(null)
                }
            }
            return
        }

        executeLoadCollapsibleBannerWithFallback(activity, bannerAdUnitId, parent, timeoutMilliSecond, adCallback, shimmerView)
    }

    private fun executeLoadCollapsibleBannerWithFallback(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?,
        shimmerView: View?
    ) {
        val effectiveAdUnitId = resolveAdUnitId(Constant.RC_C_BN_AD_UNIT_ID, bannerAdUnitId)
        if (!adMobRateLimiter.canRequest(effectiveAdUnitId)) {
            if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                activity.runOnUiThread {
                    adCallback?.onHouseAdShown("In NO_FILL cooldown")
                    houseBannerHelper.loadHouseBanner(
                        activity,
                        parent,
                        createBridgedBannerCallback(activity, parent, shimmerView, adCallback)
                    )
                }
            } else {
                activity.runOnUiThread {
                    stopAndRemoveShimmer(parent, shimmerView)
                    val cooldownError = LoadAdError(LoadAdError.ErrorCode.NO_FILL, "In NO_FILL cooldown", null)
                    adCallback?.onAdFailedToLoad(cooldownError)
                }
            }
            return
        }

        loadCollapsibleBanner(activity, bannerAdUnitId, timeoutMilliSecond, object : BannerAdCallback() {
            override fun onAdLoaded(adView: AdView) {
                activity.runOnUiThread {
                    stopAndRemoveShimmer(parent, shimmerView)
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
                        houseBannerHelper.loadHouseBanner(
                            activity,
                            parent,
                            createBridgedBannerCallback(activity, parent, shimmerView, adCallback)
                        )
                    }
                } else {
                    activity.runOnUiThread {
                        stopAndRemoveShimmer(parent, shimmerView)
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
        val effectiveAdUnitId = resolveAdUnitId(Constant.RC_BN_AD_UNIT_ID, bannerAdUnitId)
        val adSize = getAdSize(activity)
        android.util.Log.d("ADLIB_DIAGNOSTIC", "Computed AdSize: width=${adSize.width}, height=${adSize.height}")
        adCallback?.onDiagnosticInfo("Computed AdSize: width=${adSize.width}, height=${adSize.height}")
        val adRequest = getAdRequest(effectiveAdUnitId, adSize)

        val isCompleted = AtomicBoolean(false)
        val timeoutRunnable = if (timeoutMilliSecond != null && timeoutMilliSecond > 0) {
            Runnable {
                if (isCompleted.compareAndSet(false, true)) {
                    val timeoutError = LoadAdError(LoadAdError.ErrorCode.NETWORK_ERROR, "Banner load timeout", null)
                    activity.runOnUiThread {
                        adCallback?.onAdFailedToLoad(timeoutError)
                        analyticsTracker.logEvent("aj_banner_load_fail_timeout")
                    }
                }
            }
        } else null

        timeoutRunnable?.let { mainHandler.postDelayed(it, timeoutMilliSecond!!.toLong()) }

        adView.loadAd(adRequest, object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(bannerAd: BannerAd) {
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                if (!isCompleted.compareAndSet(false, true)) return

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
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                if (!isCompleted.compareAndSet(false, true)) return

                if (loadAdError.code == LoadAdError.ErrorCode.NO_FILL) {
                    adMobRateLimiter.recordNoFill(effectiveAdUnitId)
                }

                activity.runOnUiThread {
                    adCallback?.onAdFailedToLoad(loadAdError)
                    analyticsTracker.logEvent("aj_banner_load_fail", mapOf(
                        "ad_unit_id" to effectiveAdUnitId,
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
        val effectiveAdUnitId = resolveAdUnitId(Constant.RC_C_BN_AD_UNIT_ID, bannerAdUnitId)
        val adSize = getAdSize(activity)
        android.util.Log.d("ADLIB_DIAGNOSTIC", "Computed AdSize: width=${adSize.width}, height=${adSize.height}")
        adCallback?.onDiagnosticInfo("Computed AdSize: width=${adSize.width}, height=${adSize.height}")
        val adRequest = getCollapsibleAdRequest(effectiveAdUnitId, adSize)

        val isCompleted = AtomicBoolean(false)
        val timeoutRunnable = if (timeoutMilliSecond != null && timeoutMilliSecond > 0) {
            Runnable {
                if (isCompleted.compareAndSet(false, true)) {
                    val timeoutError = LoadAdError(LoadAdError.ErrorCode.NETWORK_ERROR, "Collapsible banner load timeout", null)
                    activity.runOnUiThread {
                        adCallback?.onAdFailedToLoad(timeoutError)
                        analyticsTracker.logEvent("aj_banner_load_fail_timeout")
                    }
                }
            }
        } else null

        timeoutRunnable?.let { mainHandler.postDelayed(it, timeoutMilliSecond!!.toLong()) }

        adView.loadAd(adRequest, object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(bannerAd: BannerAd) {
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                if (!isCompleted.compareAndSet(false, true)) return

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
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                if (!isCompleted.compareAndSet(false, true)) return

                if (loadAdError.code == LoadAdError.ErrorCode.NO_FILL) {
                    adMobRateLimiter.recordNoFill(effectiveAdUnitId)
                }

                activity.runOnUiThread {
                    adCallback?.onAdFailedToLoad(loadAdError)
                    analyticsTracker.logEvent("aj_banner_load_fail", mapOf(
                        "ad_unit_id" to effectiveAdUnitId,
                        "ad_error_message" to loadAdError.message
                    ))
                }
            }
        })

        return adView
    }
}
