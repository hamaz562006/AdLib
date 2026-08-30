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
import com.tqhit.adlib.sdk.ads.callback.admob.BannerAdCallback
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.utils.Constant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdaptiveBannerHelper @Inject constructor(
    private val preferencesHelper: PreferencesHelper,
    private val remoteConfigHelper: FirebaseRemoteConfigHelper
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
}
