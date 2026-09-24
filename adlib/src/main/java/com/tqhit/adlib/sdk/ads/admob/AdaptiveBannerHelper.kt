package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowMetrics
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.tqhit.adlib.R
import com.tqhit.adlib.sdk.ads.callback.admob.BannerAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseBannerAdCallback
import com.tqhit.adlib.sdk.ads.house.HouseBannerHelper
import com.tqhit.adlib.sdk.ads.house.model.HouseAdItem
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.utils.Constant
import com.tqhit.adlib.sdk.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdaptiveBannerHelper @Inject constructor(
    private val preferencesHelper: PreferencesHelper,
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val adMobRateLimiter: AdmobRateLimiter,
    private val houseBannerHelper: HouseBannerHelper
) {
    private fun showShimmer(activity: Activity, container: ViewGroup): View? {
        return try {
            val shimmerView = LayoutInflater.from(activity).inflate(R.layout.ad_banner_shimmer, container, false)
            val shimmer = shimmerView.findViewById<ShimmerFrameLayout>(R.id.shimmer_container_banner)
                ?: (shimmerView as? ShimmerFrameLayout)
            container.removeAllViews()
            container.addView(shimmerView)
            shimmer?.startShimmer()
            shimmerView
        } catch (e: Exception) {
            null
        }
    }

    private fun stopAndRemoveShimmer(container: ViewGroup, shimmerView: View?) {
        shimmerView?.let { view ->
            val shimmer = view.findViewById<ShimmerFrameLayout>(R.id.shimmer_container_banner)
                ?: (view as? ShimmerFrameLayout)
            shimmer?.stopShimmer()
            container.removeView(view)
        }
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

    fun getAdaptiveAdSize(activity: Activity): AdSize {
        val widthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            bounds.width()
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
            displayMetrics.widthPixels
        }

        val density = activity.resources.displayMetrics.density
        val adWidth = (widthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    fun loadAdaptiveBanner(
        activity: Activity,
        adUnitId: String,
        container: ViewGroup,
        callback: BannerAdCallback? = null
    ): AdView? {
        if (preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)) {
            activity.runOnUiThread { callback?.onAdFailedToLoad(null) }
            return null
        }

        if (!Constant.DEBUG_MODE && remoteConfigHelper.getBoolean("bn_enable").not()) {
            activity.runOnUiThread { callback?.onAdFailedToLoad(null) }
            return null
        }

        val adView = AdView(activity)
        val adSize = getAdaptiveAdSize(activity)
        val effectiveAdUnitId = resolveAdUnitId(Constant.RC_BN_AD_UNIT_ID, adUnitId)
        val adRequest = BannerAdRequest.Builder(effectiveAdUnitId, adSize).build()

        adView.loadAd(adRequest, object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) {
                ad.adEventCallback = object : BannerAdEventCallback {
                    override fun onAdClicked() {
                        activity.runOnUiThread { callback?.onAdClicked() }
                    }

                    override fun onAdShowedFullScreenContent() {
                        activity.runOnUiThread { callback?.onAdOpened() }
                    }

                    override fun onAdDismissedFullScreenContent() {
                        activity.runOnUiThread { callback?.onAdClosed() }
                    }

                    override fun onAdImpression() {
                        activity.runOnUiThread { callback?.onAdImpression() }
                    }
                }

                activity.runOnUiThread {
                    container.removeAllViews()
                    container.addView(adView)
                    callback?.onAdLoaded(adView)
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                if (adError.code == LoadAdError.ErrorCode.NO_FILL) {
                    adMobRateLimiter.recordNoFill(effectiveAdUnitId)
                }
                activity.runOnUiThread {
                    callback?.onAdFailedToLoad(adError)
                }
            }
        })

        return adView
    }

    fun loadAdaptiveBannerWithFallback(
        activity: Activity,
        adUnitId: String,
        container: ViewGroup,
        callback: BannerAdCallback? = null
    ) {
        val shimmerView = showShimmer(activity, container)

        if (!NetworkUtils.isNetworkAvailable(activity)) {
            if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
                activity.runOnUiThread {
                    callback?.onHouseAdShown("Network unavailable")
                    houseBannerHelper.loadHouseBanner(
                        activity,
                        container,
                        createBridgedCallback(activity, container, shimmerView, callback)
                    )
                }
            } else {
                activity.runOnUiThread {
                    stopAndRemoveShimmer(container, shimmerView)
                    callback?.onAdFailedToLoad(null)
                }
            }
            return
        }

        executeLoadAdaptiveBannerWithFallback(activity, adUnitId, container, callback, shimmerView)
    }

    private fun executeLoadAdaptiveBannerWithFallback(
        activity: Activity,
        adUnitId: String,
        container: ViewGroup,
        callback: BannerAdCallback? = null,
        shimmerView: View?
    ) {
        val effectiveAdUnitId = resolveAdUnitId(Constant.RC_BN_AD_UNIT_ID, adUnitId)
        if (!adMobRateLimiter.canRequest(effectiveAdUnitId)) {
            if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                activity.runOnUiThread {
                    callback?.onHouseAdShown("In NO_FILL cooldown")
                    houseBannerHelper.loadHouseBanner(
                        activity,
                        container,
                        createBridgedCallback(activity, container, shimmerView, callback)
                    )
                }
            } else {
                activity.runOnUiThread {
                    stopAndRemoveShimmer(container, shimmerView)
                    val cooldownError = LoadAdError(LoadAdError.ErrorCode.NO_FILL, "In NO_FILL cooldown", null)
                    callback?.onAdFailedToLoad(cooldownError)
                }
            }
            return
        }

        loadAdaptiveBanner(activity, adUnitId, container, object : BannerAdCallback() {
            override fun onAdLoaded(adView: AdView) {
                activity.runOnUiThread {
                    stopAndRemoveShimmer(container, shimmerView)
                    container.removeAllViews()
                    container.addView(adView)
                    callback?.onAdLoaded(adView)
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError?) {
                if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                    val reason = adError?.message ?: adError?.code?.toString() ?: "Unknown AdMob error"
                    activity.runOnUiThread {
                        callback?.onHouseAdShown(reason)
                        houseBannerHelper.loadHouseBanner(
                            activity,
                            container,
                            createBridgedCallback(activity, container, shimmerView, callback)
                        )
                    }
                } else {
                    activity.runOnUiThread {
                        stopAndRemoveShimmer(container, shimmerView)
                        callback?.onAdFailedToLoad(adError)
                    }
                }
            }

            override fun onAdClicked() {
                activity.runOnUiThread { callback?.onAdClicked() }
            }

            override fun onAdOpened() {
                activity.runOnUiThread { callback?.onAdOpened() }
            }

            override fun onAdImpression() {
                activity.runOnUiThread { callback?.onAdImpression() }
            }

            override fun onAdClosed() {
                activity.runOnUiThread { callback?.onAdClosed() }
            }
        })
    }

    private fun createBridgedCallback(
        activity: Activity,
        container: ViewGroup,
        shimmerView: View?,
        adCallback: BannerAdCallback?
    ): HouseBannerAdCallback {
        return object : HouseBannerAdCallback() {
            override fun onAdLoaded(houseAdItem: HouseAdItem) {
                activity.runOnUiThread {
                    stopAndRemoveShimmer(container, shimmerView)
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
                    stopAndRemoveShimmer(container, shimmerView)
                    adCallback?.onAdFailedToLoad(null)
                }
            }
        }
    }
}
