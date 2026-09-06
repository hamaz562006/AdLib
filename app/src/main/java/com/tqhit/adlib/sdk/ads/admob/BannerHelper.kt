package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.tqhit.adlib.sdk.ads.callback.admob.BannerAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseBannerAdCallback
import com.tqhit.adlib.sdk.ads.house.HouseBannerHelper
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.utils.Constant
import com.tqhit.adlib.sdk.utils.NetworkUtils
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
    

    private fun getCollapsibleAdRequest(timeout: Int = 60000): AdRequest {
        val builder = AdRequest.Builder().setHttpTimeoutMillis(timeout)
        val bundle = Bundle()
        bundle.putString("collapsible", "bottom")
        bundle.putString("collapsible_request_id", UUID.randomUUID().toString())
        builder.addNetworkExtrasBundle(AdMobAdapter::class.java, bundle)
        return builder.build()
    }

    private fun getAdRequest(timeout: Int = 60000): AdRequest {
        return AdRequest.Builder().setHttpTimeoutMillis(timeout).build()
    }

    private fun getAdSize(activity: Activity): AdSize {
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        val i = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, i)
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
        if (!NetworkUtils.isNetworkAvailable(activity) && remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
            adCallback?.onHouseAdShown()
            houseBannerHelper.loadHouseBanner(activity, parent, createBridgedBannerCallback(adCallback))
            return
        }
        val adView = loadBanner(activity, bannerAdUnitId, timeoutMilliSecond, object : BannerAdCallback() {
            override fun onAdLoaded(adView: AdView) {
                parent.removeAllViews()
                parent.addView(adView)
                adCallback?.onAdLoaded(adView)
            }
            override fun onAdFailedToLoad(adError: LoadAdError?) {
                if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                    adCallback?.onHouseAdShown()
                    houseBannerHelper.loadHouseBanner(activity, parent, createBridgedBannerCallback(adCallback))
                } else {
                    adCallback?.onAdFailedToLoad(adError)
                }
            }
            override fun onAdClicked() { adCallback?.onAdClicked() }
            override fun onAdImpression() { adCallback?.onAdImpression() }
            override fun onAdClosed() { adCallback?.onAdClosed() }
        })
    }

    private fun createBridgedBannerCallback(adCallback: BannerAdCallback?): HouseBannerAdCallback {
        return object : HouseBannerAdCallback() {
            override fun onAdImpression() { adCallback?.onAdImpression() }
            override fun onAdClicked() { adCallback?.onAdClicked() }
            override fun onAdClosed() { adCallback?.onAdClosed() }
            override fun onAdFailedToLoad(errorMessage: String) { adCallback?.onAdFailedToLoad(null) }
        }
    }

    fun showCollapsibleBannerWithFallback(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ) {
        if (!NetworkUtils.isNetworkAvailable(activity) && remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
            adCallback?.onHouseAdShown()
            houseBannerHelper.loadHouseBanner(activity, parent, createBridgedBannerCallback(adCallback))
            return
        }
        val adView = loadCollapsibleBanner(activity, bannerAdUnitId, timeoutMilliSecond, object : BannerAdCallback() {
            override fun onAdLoaded(adView: AdView) {
                parent.removeAllViews()
                parent.addView(adView)
                adCallback?.onAdLoaded(adView)
            }
            override fun onAdFailedToLoad(adError: LoadAdError?) {
                if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                    adCallback?.onHouseAdShown()
                    houseBannerHelper.loadHouseBanner(activity, parent, createBridgedBannerCallback(adCallback))
                } else {
                    adCallback?.onAdFailedToLoad(adError)
                }
            }
            override fun onAdClicked() { adCallback?.onAdClicked() }
            override fun onAdImpression() { adCallback?.onAdImpression() }
            override fun onAdClosed() { adCallback?.onAdClosed() }
        })
    }

    fun loadBanner(
        activity: Activity,
        bannerAdUnitId: String,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ): AdView? {
        if (!isAdEnabled() || !admobConsentHelper.canRequestAds())
        {
            adCallback?.onAdFailedToLoad();
            return null
        }
        analyticsTracker.logEvent("aj_banner_load")
        val adView = AdView(activity)
        val adRequest = getAdRequest(timeoutMilliSecond ?: 60000)
        adView.apply {
            adUnitId = if (Constant.DEBUG_MODE) Constant.ADMOB_BANNER_AD_UNIT_ID else bannerAdUnitId
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    adCallback?.onAdFailedToLoad(loadAdError)
                    analyticsTracker.logEvent("aj_banner_load_fail", mapOf(
                        "ad_unit_id" to bannerAdUnitId,
                        "ad_error_message" to loadAdError.message
                    ))
                }

                override fun onAdLoaded() {
                    super.onAdLoaded()
                    adCallback?.onAdLoaded(this@apply)
                    analyticsTracker.logEvent("aj_banner_load_success")
                }

                override fun onAdClosed() {
                    super.onAdClosed()
                    adCallback?.onAdClosed()
                    analyticsTracker.logEvent("aj_banner_close")
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    adCallback?.onAdClicked()
                    analyticsTracker.logEvent("aj_banner_click")
                }


                override fun onAdImpression() {
                    super.onAdImpression()
                    adCallback?.onAdImpression()
                }

                override fun onAdOpened() {
                    super.onAdOpened()
                    analyticsTracker.logEvent("aj_banner_show_success")
                }
            }
            onPaidEventListener = OnPaidEventListener { adValue ->
                analyticsTracker.trackAdMobRevenueEvent(
                    adValue,
                    adView.adUnitId,
                    adView.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "AdMob",
                    "Banner"
                )
            }
        }
        adView.setAdSize(getAdSize(activity))
        adView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        adView.loadAd(adRequest)
        return adView
    }

    fun loadCollapsibleBanner(
        activity: Activity,
        bannerAdUnitId: String,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ): AdView? {
        if (!isAdEnabled() || !admobConsentHelper.canRequestAds())
        {
            adCallback?.onAdFailedToLoad();
            return null
        }
        analyticsTracker.logEvent("aj_banner_load")
        val adView = AdView(activity)
        val adRequest = getCollapsibleAdRequest(timeoutMilliSecond ?: 60000)
        adView.apply {
            adUnitId = if (Constant.DEBUG_MODE) Constant.ADMOB_COLLAPSIBLE_BANNER_AD_UNIT_ID else bannerAdUnitId
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                    adCallback?.onAdFailedToLoad(loadAdError)
                    analyticsTracker.logEvent("aj_banner_load_fail", mapOf(
                        "ad_unit_id" to bannerAdUnitId,
                        "ad_error_message" to loadAdError.message
                    ))
                }

                override fun onAdLoaded() {
                    super.onAdLoaded()
                    adCallback?.onAdLoaded(this@apply)
                    analyticsTracker.logEvent("aj_banner_load_success")
                }

                override fun onAdClosed() {
                    super.onAdClosed()
                    adCallback?.onAdClosed()
                    analyticsTracker.logEvent("aj_banner_close")
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    adCallback?.onAdClicked()
                    analyticsTracker.logEvent("aj_banner_click")
                }

                override fun onAdImpression() {
                    super.onAdImpression()
                    adCallback?.onAdImpression()
                }

                override fun onAdOpened() {
                    super.onAdOpened()
                    analyticsTracker.logEvent("aj_banner_show_success")
                }
            }
            onPaidEventListener = OnPaidEventListener { adValue ->
                analyticsTracker.trackAdMobRevenueEvent(
                    adValue,
                    adView.adUnitId,
                        adView.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "AdMob",
                    "Banner"
                )
            }
        }
        adView.setAdSize(getAdSize(activity))
        adView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        adView.loadAd(adRequest)
        return adView
    }
}
