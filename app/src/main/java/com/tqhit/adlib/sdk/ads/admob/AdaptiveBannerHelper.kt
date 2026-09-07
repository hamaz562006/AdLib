package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.view.WindowMetrics
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
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
            callback?.onAdFailedToLoad(null)
            return null
        }

        if (remoteConfigHelper.getBoolean("bn_enable").not()) {
            callback?.onAdFailedToLoad(null)
            return null
        }

        val adView = AdView(activity)
        adView.adUnitId = adUnitId
        val adSize = getAdaptiveAdSize(activity)
        adView.setAdSize(adSize)

        adView.adListener = object : com.google.android.gms.ads.AdListener() {
            override fun onAdLoaded() {
                container.removeAllViews()
                container.addView(adView)
                callback?.onAdLoaded(adView)
            }

            override fun onAdFailedToLoad(adError: com.google.android.gms.ads.LoadAdError) {
                callback?.onAdFailedToLoad(adError)
            }

            override fun onAdClicked() {
                callback?.onAdClicked()
            }

            override fun onAdOpened() {
                callback?.onAdOpened()
            }

            override fun onAdClosed() {
                callback?.onAdClosed()
            }

            override fun onAdImpression() {
                callback?.onAdImpression()
            }
        }

        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
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
                callback?.onHouseAdShown("Network unavailable")
                houseBannerHelper.loadHouseBanner(activity, container, createBridgedCallback(callback))
            } else {
                callback?.onAdFailedToLoad(null)
            }
            return
        }

        if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
            CoroutineScope(Dispatchers.Main).launch {
                val reachable = NetworkUtils.isAdServerReachable()
                if (!reachable && !activity.isFinishing && !activity.isDestroyed) {
                    callback?.onHouseAdShown("Ad server unreachable (possibly blocked network)")
                    houseBannerHelper.loadHouseBanner(activity, container, createBridgedCallback(callback))
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
            override fun onAdLoaded(adView: AdView) { callback?.onAdLoaded(adView) }
            override fun onAdFailedToLoad(adError: LoadAdError?) {
                if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                    val reason = adError?.message ?: adError?.code?.toString() ?: "Unknown AdMob error"
                    callback?.onHouseAdShown(reason)
                    houseBannerHelper.loadHouseBanner(activity, container, createBridgedCallback(callback))
                } else {
                    callback?.onAdFailedToLoad(adError)
                }
            }
            override fun onAdClicked() { callback?.onAdClicked() }
            override fun onAdOpened() { callback?.onAdOpened() }
            override fun onAdImpression() { callback?.onAdImpression() }
            override fun onAdClosed() { callback?.onAdClosed() }
        })
    }

    private fun createBridgedCallback(adCallback: BannerAdCallback?): HouseBannerAdCallback {
        return object : HouseBannerAdCallback() {
            override fun onAdImpression() { adCallback?.onAdImpression() }
            override fun onAdClicked() { adCallback?.onAdClicked() }
            override fun onAdClosed() { adCallback?.onAdClosed() }
            override fun onAdFailedToLoad(errorMessage: String) { adCallback?.onAdFailedToLoad(null) }
        }
    }
}
