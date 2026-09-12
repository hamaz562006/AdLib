package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.os.Build
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.view.WindowMetrics
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.tqhit.adlib.sdk.ads.callback.admob.BannerAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseBannerAdCallback
import com.tqhit.adlib.sdk.ads.house.HouseBannerHelper
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
    private val houseBannerHelper: HouseBannerHelper
) {
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

        if (remoteConfigHelper.getBoolean("bn_enable").not()) {
            activity.runOnUiThread { callback?.onAdFailedToLoad(null) }
            return null
        }

        val adView = AdView(activity)
        val adSize = getAdaptiveAdSize(activity)
        val effectiveAdUnitId = if (Constant.DEBUG_MODE) Constant.ADMOB_BANNER_AD_UNIT_ID else adUnitId
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
        if (!NetworkUtils.isNetworkAvailable(activity)) {
            if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
                activity.runOnUiThread {
                    callback?.onHouseAdShown("Network unavailable")
                    houseBannerHelper.loadHouseBanner(activity, container, createBridgedCallback(activity, callback))
                }
            } else {
                activity.runOnUiThread {
                    callback?.onAdFailedToLoad(null)
                }
            }
            return
        }

        if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
            CoroutineScope(Dispatchers.Main).launch {
                val reachable = NetworkUtils.isAdServerReachable()
                if (!reachable && !activity.isFinishing && !activity.isDestroyed) {
                    activity.runOnUiThread {
                        callback?.onHouseAdShown("Ad server unreachable (possibly blocked network)")
                        houseBannerHelper.loadHouseBanner(activity, container, createBridgedCallback(activity, callback))
                    }
                } else if (!activity.isFinishing && !activity.isDestroyed) {
                    executeLoadAdaptiveBannerWithFallback(activity, adUnitId, container, callback)
                }
            }
        } else {
            executeLoadAdaptiveBannerWithFallback(activity, adUnitId, container, callback)
        }
    }

    private fun executeLoadAdaptiveBannerWithFallback(
        activity: Activity,
        adUnitId: String,
        container: ViewGroup,
        callback: BannerAdCallback? = null
    ) {
        loadAdaptiveBanner(activity, adUnitId, container, object : BannerAdCallback() {
            override fun onAdLoaded(adView: AdView) {
                activity.runOnUiThread {
                    callback?.onAdLoaded(adView)
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError?) {
                if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                    val reason = adError?.message ?: adError?.code?.toString() ?: "Unknown AdMob error"
                    activity.runOnUiThread {
                        callback?.onHouseAdShown(reason)
                        houseBannerHelper.loadHouseBanner(activity, container, createBridgedCallback(activity, callback))
                    }
                } else {
                    activity.runOnUiThread {
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

    private fun createBridgedCallback(activity: Activity, adCallback: BannerAdCallback?): HouseBannerAdCallback {
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
}
