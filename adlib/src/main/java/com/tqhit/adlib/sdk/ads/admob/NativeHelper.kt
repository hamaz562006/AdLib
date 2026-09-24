package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.tqhit.adlib.R
import com.tqhit.adlib.sdk.ads.callback.admob.NativeAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseNativeAdCallback
import com.tqhit.adlib.sdk.ads.house.HouseNativeHelper
import com.tqhit.adlib.sdk.ads.house.model.HouseAdItem
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.utils.Constant
import com.tqhit.adlib.sdk.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeHelper @Inject constructor(
    private val admobConsentHelper: AdmobConsentHelper,
    private val analyticsTracker: AnalyticsTracker,
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val preferencesHelper: PreferencesHelper,
    private val adMobRateLimiter: AdmobRateLimiter,
    private val houseNativeHelper: HouseNativeHelper
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun showShimmer(context: Context, container: ViewGroup, useFullLayout: Boolean): View? {
        return try {
            val layoutRes = if (useFullLayout) R.layout.ad_native_shimmer_full else R.layout.ad_native_shimmer
            val shimmerView = LayoutInflater.from(context).inflate(layoutRes, container, false)
            val shimmer = shimmerView.findViewById<ShimmerFrameLayout>(R.id.shimmer_container_native)
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
            val shimmer = view.findViewById<ShimmerFrameLayout>(R.id.shimmer_container_native)
                ?: (view as? ShimmerFrameLayout)
            shimmer?.stopShimmer()
            container.removeView(view)
        }
    }

    private fun isAdEnabled() =
        remoteConfigHelper.getBoolean("nt_enable")
                && !preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)

    /**
     * Resolves the effective ad unit ID: a matching Remote Config key takes priority (so a
     * production ad unit ID can be swapped after publishing without a new release); falls back
     * to the ID passed in code if the RC key is missing/blank.
     */
    private fun resolveAdUnitId(rcKey: String, fallback: String): String {
        val rcValue = remoteConfigHelper.getString(rcKey)
        return if (rcValue.isNotBlank()) rcValue else fallback
    }

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun getNativeAdRequest(adUnitId: String): NativeAdRequest {
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .build()
        return NativeAdRequest.Builder(adUnitId, listOf(NativeAd.NativeAdType.NATIVE))
            .setVideoOptions(videoOptions)
            .build()
    }

    fun loadNativeWithFallback(
        context: Context,
        nativeAdUnitId: String,
        timeOutMilliSecond: Int?,
        container: ViewGroup,
        useFullLayout: Boolean,
        adCallback: NativeAdCallback?
    ) {
        val shimmerView = showShimmer(context, container, useFullLayout)

        if (!NetworkUtils.isNetworkAvailable(context)) {
            if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
                runOnUiThread {
                    adCallback?.onHouseAdShown("Network unavailable")
                    showHouseFallback(context, container, useFullLayout, adCallback, shimmerView)
                }
            } else {
                runOnUiThread {
                    stopAndRemoveShimmer(container, shimmerView)
                    adCallback?.onAdFailedToLoad(null)
                }
            }
            return
        }

        if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)) {
            CoroutineScope(Dispatchers.Main).launch {
                val reachable = NetworkUtils.isAdServerReachable()
                if (!reachable) {
                    runOnUiThread {
                        adCallback?.onHouseAdShown("Ad server unreachable (possibly blocked network)")
                        showHouseFallback(context, container, useFullLayout, adCallback, shimmerView)
                    }
                } else {
                    executeLoadNativeWithFallback(context, nativeAdUnitId, timeOutMilliSecond, container, useFullLayout, adCallback, shimmerView)
                }
            }
        } else {
            executeLoadNativeWithFallback(context, nativeAdUnitId, timeOutMilliSecond, container, useFullLayout, adCallback, shimmerView)
        }
    }

    private fun executeLoadNativeWithFallback(
        context: Context,
        nativeAdUnitId: String,
        timeOutMilliSecond: Int?,
        container: ViewGroup,
        useFullLayout: Boolean,
        adCallback: NativeAdCallback?,
        shimmerView: View?
    ) {
        val adUnitId = resolveAdUnitId(Constant.RC_NT_AD_UNIT_ID, nativeAdUnitId)
        if (!adMobRateLimiter.canRequest(adUnitId)) {
            if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                runOnUiThread {
                    adCallback?.onHouseAdShown("In NO_FILL cooldown")
                    showHouseFallback(context, container, useFullLayout, adCallback, shimmerView)
                }
            } else {
                runOnUiThread {
                    stopAndRemoveShimmer(container, shimmerView)
                    val cooldownError = LoadAdError(LoadAdError.ErrorCode.NO_FILL, "In NO_FILL cooldown", null)
                    adCallback?.onAdFailedToLoad(cooldownError)
                }
            }
            return
        }

        loadNative(context, nativeAdUnitId, timeOutMilliSecond, object : NativeAdCallback() {
            override fun onAdLoaded(nativeAd: NativeAd) {
                runOnUiThread {
                    stopAndRemoveShimmer(container, shimmerView)
                    adCallback?.onAdLoaded(nativeAd)
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError?) {
                if (remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)) {
                    val reason = adError?.message ?: adError?.code?.toString() ?: "Unknown AdMob error"
                    runOnUiThread {
                        adCallback?.onHouseAdShown(reason)
                        showHouseFallback(context, container, useFullLayout, adCallback, shimmerView)
                    }
                } else {
                    runOnUiThread {
                        stopAndRemoveShimmer(container, shimmerView)
                        adCallback?.onAdFailedToLoad(adError)
                    }
                }
            }

            override fun onAdClicked() {
                runOnUiThread { adCallback?.onAdClicked() }
            }

            override fun onAdImpression() {
                runOnUiThread { adCallback?.onAdImpression() }
            }

            override fun onAdClosed() {
                runOnUiThread { adCallback?.onAdClosed() }
            }
        })
    }

    private fun showHouseFallback(
        context: Context,
        container: ViewGroup,
        useFullLayout: Boolean,
        adCallback: NativeAdCallback?,
        shimmerView: View? = null
    ) {
        val bridge = object : HouseNativeAdCallback() {
            override fun onAdLoaded(houseAdItem: HouseAdItem) {
                runOnUiThread {
                    stopAndRemoveShimmer(container, shimmerView)
                }
            }

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
                runOnUiThread {
                    stopAndRemoveShimmer(container, shimmerView)
                    adCallback?.onAdFailedToLoad(null)
                }
            }
        }
        if (useFullLayout) {
            houseNativeHelper.showHouseNativeFull(context, container, bridge)
        } else {
            houseNativeHelper.showHouseNativeSmall(context, container, bridge)
        }
    }

    fun loadNative(
        context: Context,
        nativeAdUnitId: String,
        timeOutMilliSecond: Int?,
        adCallback: NativeAdCallback?
    ) {
        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            runOnUiThread { adCallback?.onAdFailedToLoad() }
            return
        }

        analyticsTracker.logEvent("aj_native_load")

        val adUnitId = resolveAdUnitId(Constant.RC_NT_AD_UNIT_ID, nativeAdUnitId)

        val isCompleted = AtomicBoolean(false)
        val timeoutRunnable = if (timeOutMilliSecond != null && timeOutMilliSecond > 0) {
            Runnable {
                if (isCompleted.compareAndSet(false, true)) {
                    val timeoutError = LoadAdError(LoadAdError.ErrorCode.NETWORK_ERROR, "Native load timeout", null)
                    runOnUiThread {
                        adCallback?.onAdFailedToLoad(timeoutError)
                        analyticsTracker.logEvent("aj_native_load_fail_timeout")
                    }
                }
            }
        } else null

        timeoutRunnable?.let { mainHandler.postDelayed(it, timeOutMilliSecond!!.toLong()) }

        val nativeAdRequest = getNativeAdRequest(adUnitId)

        NativeAdLoader.load(nativeAdRequest, object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) {
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                if (!isCompleted.compareAndSet(false, true)) return

                nativeAd.adEventCallback = object : NativeAdEventCallback {
                    override fun onAdImpression() {
                        runOnUiThread {
                            adCallback?.onAdImpression()
                        }
                    }

                    override fun onAdClicked() {
                        runOnUiThread {
                            adCallback?.onAdClicked()
                            analyticsTracker.logEvent("aj_native_click")
                        }
                    }

                    override fun onAdDismissedFullScreenContent() {
                        runOnUiThread {
                            adCallback?.onAdClosed()
                            analyticsTracker.logEvent("aj_native_close")
                        }
                    }

                    override fun onAdPaid(adValue: AdValue) {
                        analyticsTracker.trackAdMobRevenueEvent(
                            adValue,
                            adUnitId,
                            nativeAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "AdMob",
                            "Native"
                        )
                    }
                }

                runOnUiThread {
                    adCallback?.onAdLoaded(nativeAd)
                    analyticsTracker.logEvent("aj_native_load_success")
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                if (!isCompleted.compareAndSet(false, true)) return

                if (adError.code == LoadAdError.ErrorCode.NO_FILL) {
                    adMobRateLimiter.recordNoFill(adUnitId)
                }

                runOnUiThread {
                    adCallback?.onAdFailedToLoad(adError)
                    analyticsTracker.logEvent("aj_native_load_fail")
                }
            }
        })
    }

    fun showNative(
        nativeAd: NativeAd,
        nativeAdView: NativeAdView
    ) {
        val mediaView = nativeAdView.findViewById<MediaView>(R.id.ad_media)
        nativeAdView.headlineView = nativeAdView.findViewById(R.id.ad_headline)
        nativeAdView.bodyView = nativeAdView.findViewById(R.id.ad_body)
        nativeAdView.callToActionView = nativeAdView.findViewById(R.id.ad_call_to_action)
        nativeAdView.iconView = nativeAdView.findViewById(R.id.ad_app_icon)
        nativeAdView.advertiserView = nativeAdView.findViewById(R.id.ad_advertiser)

        nativeAdView.headlineView?.let {
            (it as TextView).text = nativeAd.headline
        }

        nativeAdView.bodyView?.let {
            if (nativeAd.body == null) {
                it.visibility = View.INVISIBLE
            } else {
                it.visibility = View.VISIBLE
                (it as TextView).text = nativeAd.body
            }
        }

        nativeAdView.callToActionView?.let {
            if (nativeAd.callToAction == null) {
                it.visibility = View.INVISIBLE
            } else {
                it.visibility = View.VISIBLE
                if (it is TextView) {
                    it.text = nativeAd.callToAction
                }
                if (it is AppCompatButton) {
                    it.text = nativeAd.callToAction
                }
            }
        }

        nativeAdView.iconView?.let {
            if (nativeAd.icon == null) {
                it.visibility = View.GONE
            } else {
                it.visibility = View.VISIBLE
                (it as ImageView).setImageDrawable(nativeAd.icon?.drawable)
            }
        }

        nativeAdView.advertiserView?.let {
            if (nativeAd.advertiser == null) {
                it.visibility = View.GONE
            } else {
                it.visibility = View.VISIBLE
                (it as TextView).text = nativeAd.advertiser
            }
        }

        nativeAdView.priceView?.let {
            if (nativeAd.price == null) {
                it.visibility = View.GONE
            } else {
                it.visibility = View.VISIBLE
                (it as TextView).text = nativeAd.price
            }
        }

        nativeAdView.storeView?.let {
            if (nativeAd.store == null) {
                it.visibility = View.GONE
            } else {
                it.visibility = View.VISIBLE
                (it as TextView).text = nativeAd.store
            }
        }

        nativeAdView.starRatingView?.let {
            if (nativeAd.starRating == null) {
                it.visibility = View.GONE
            } else {
                it.visibility = View.VISIBLE
                (it as RatingBar).rating = nativeAd.starRating!!.toFloat()
            }
        }

        nativeAdView.registerNativeAd(nativeAd, mediaView)
        analyticsTracker.logEvent("aj_native_show_success")
    }
}
