package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import android.view.ViewGroup
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.common.AdInspectorError
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.tqhit.adlib.sdk.ads.callback.admob.BannerAdCallback
import com.tqhit.adlib.sdk.ads.callback.admob.InterstitialAdCallback
import com.tqhit.adlib.sdk.ads.callback.admob.NativeAdCallback
import com.tqhit.adlib.sdk.ads.callback.admob.RewardAdCallback
import com.tqhit.adlib.sdk.utils.Constant
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdmobHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bannerHelper: BannerHelper,
    private val interstitialHelper: InterstitialHelper,
    private val rewardHelper: RewardHelper,
    private val nativeHelper: NativeHelper,
    private val appOpenHelper: AppOpenHelper
) {
    private val TAG: String = AdmobHelper::class.java.simpleName

    // Coarse Location Collection flag state
    private var isLocationCollectionEnabled: Boolean = false

    /**
     * Expose configuration for coarse location collection.
     * Note: In GMA Next-Gen SDK version 1.4.0, Google has not yet exposed the public API flag
     * in RequestConfiguration or InitializationConfig. This method tracks state and is prepared
     * to bind directly once exposed in upcoming SDK updates.
     */
    fun setLocationCollectionEnabled(enabled: Boolean) {
        isLocationCollectionEnabled = enabled
        Log.d(TAG, "Location collection enabled set to: $enabled (Pending Next-Gen SDK API flag availability)")
    }

    // TODO: Coarse Location Collection: Google announced that GMA Next-Gen SDK will collect coarse location by default unless disabled via a configuration flag. In version 1.4.0 (installed), this configuration flag is not yet present in the public API (RequestConfiguration / InitializationConfig / MobileAds). Re-check in subsequent SDK updates and configure accordingly.
    fun initAdmob(onComplete: () -> Unit, testDeviceIds: List<String>? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val appId = try {
                val ai = context.packageManager.getApplicationInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_META_DATA
                )
                ai.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID")
                    ?: "ca-app-pub-3940256099942544~3347511713"
            } catch (e: Exception) {
                "ca-app-pub-3940256099942544~3347511713"
            }

            val effectiveTestDevices = if (!testDeviceIds.isNullOrEmpty()) {
                testDeviceIds
            } else {
                Constant.TEST_DEVICE_IDS
            }

            val requestConfig = RequestConfiguration.Builder()
                .setTestDeviceIds(effectiveTestDevices)
                .build()

            MobileAds.setRequestConfiguration(requestConfig)

            val initConfig = InitializationConfig.Builder(appId)
                .setRequestConfiguration(requestConfig)
                .build()

            MobileAds.initialize(context, initConfig) { _ ->
                MobileAds.setRequestConfiguration(requestConfig)
                Log.d(TAG, "Admob initialized with test devices: $effectiveTestDevices")
                onComplete()
            }
        }
    }

    fun isNetwork(context: Context?): Boolean {
        if (context == null) return false
        val systemService = context.getSystemService(Context.CONNECTIVITY_SERVICE)
        val connectivityManager = systemService as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo?.isConnected == true
    }

    fun loadBanner(
        activity: Activity,
        bannerAdUnitId: String,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ): AdView? {
        return bannerHelper.loadBanner(
            activity,
            bannerAdUnitId,
            timeoutMilliSecond,
            adCallback
        )
    }

    fun loadCollapsibleBanner(
        activity: Activity,
        bannerAdUnitId: String,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ): AdView? {
        return bannerHelper.loadCollapsibleBanner(
            activity,
            bannerAdUnitId,
            timeoutMilliSecond,
            adCallback
        )
    }

    fun showCollapsibleBanner(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ) {
        bannerHelper.showCollapsibleBanner(
            activity,
            bannerAdUnitId,
            parent,
            timeoutMilliSecond,
            adCallback
        )
    }

    fun showCollapsibleBanner(
        adView: AdView,
        parent: ViewGroup
    ) {
        bannerHelper.showCollapsibleBanner(
            adView,
            parent
        )
    }

    fun showBanner(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ) {
        bannerHelper.showBanner(
            activity,
            bannerAdUnitId,
            parent,
            timeoutMilliSecond,
            adCallback
        )
    }

    fun showBanner(
        adView: AdView,
        parent: ViewGroup
    ) {
        bannerHelper.showBanner(
            adView,
            parent
        )
    }

    fun showBannerWithFallback(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ) {
        bannerHelper.showBannerWithFallback(
            activity,
            bannerAdUnitId,
            parent,
            timeoutMilliSecond,
            adCallback
        )
    }

    fun showCollapsibleBannerWithFallback(
        activity: Activity,
        bannerAdUnitId: String,
        parent: ViewGroup,
        timeoutMilliSecond: Int?,
        adCallback: BannerAdCallback?
    ) {
        bannerHelper.showCollapsibleBannerWithFallback(
            activity,
            bannerAdUnitId,
            parent,
            timeoutMilliSecond,
            adCallback
        )
    }

    fun loadInterstitial(
        context: Context,
        interstitialAdUnitId: String,
        timeoutMilliSecond: Int?,
        adCallback: InterstitialAdCallback?
    ) {
        interstitialHelper.loadInterstitial(
            context,
            interstitialAdUnitId,
            timeoutMilliSecond,
            adCallback
        )
    }

    fun showInterstitial(
        activity: Activity,
        interstitialAdUnitId: String,
        interstitialAd: InterstitialAd?,
        timeoutMilliSecond: Int?,
        adCallback: InterstitialAdCallback?
    ) {
        interstitialHelper.showInterstitial(
            activity,
            interstitialAdUnitId,
            interstitialAd,
            timeoutMilliSecond,
            adCallback
        )
    }

    fun showInterstitial(
        activity: Activity,
        interstitialAd: InterstitialAd,
        adCallback: InterstitialAdCallback?
    ) {
        interstitialHelper.showInterstitial(
            activity,
            interstitialAd,
            adCallback
        )
    }

    fun loadReward(
        context: Context,
        rewardAdUnitId: String,
        timeoutMilliSecond: Int?,
        adCallback: RewardAdCallback?
    ) {
        rewardHelper.loadReward(
            context,
            rewardAdUnitId,
            timeoutMilliSecond,
            adCallback
        )
    }

    fun showReward(
        activity: Activity,
        rewardAdUnitId: String,
        rewardedAd: RewardedAd?,
        timeoutMilliSecond: Int?,
        adCallback: RewardAdCallback?
    ) {
        rewardHelper.showReward(
            activity,
            rewardAdUnitId,
            rewardedAd,
            timeoutMilliSecond,
            adCallback
        )
    }

    fun showReward(
        activity: Activity,
        rewardedAd: RewardedAd,
        adCallback: RewardAdCallback?
    ) {
        rewardHelper.showReward(
            activity,
            rewardedAd,
            adCallback
        )
    }

    fun loadNative(
        context: Context,
        nativeAdUnitId: String,
        timeoutMilliSecond: Int?,
        adCallback: NativeAdCallback?
    ) {
        nativeHelper.loadNative(
            context,
            nativeAdUnitId,
            timeoutMilliSecond,
            adCallback
        )
    }

    fun loadNativeWithFallback(
        context: Context,
        nativeAdUnitId: String,
        timeoutMilliSecond: Int?,
        container: ViewGroup,
        useFullLayout: Boolean,
        adCallback: NativeAdCallback?
    ) {
        nativeHelper.loadNativeWithFallback(
            context,
            nativeAdUnitId,
            timeoutMilliSecond,
            container,
            useFullLayout,
            adCallback
        )
    }

    fun showNative(
        nativeAd: NativeAd,
        nativeAdView: NativeAdView
    ) {
        nativeHelper.showNative(
            nativeAd,
            nativeAdView
        )
    }

    fun setAppOpenAdUnitId(adUnitId: String) {
        appOpenHelper.setAdUnitId(adUnitId)
    }

    fun loadAOA(context: Context) {
        appOpenHelper.loadAd(context)
    }

    fun showAOA(
        activity: Activity,
        adCallback: AppOpenHelper.OnShowAdCompleteListener?
    ) {
        appOpenHelper.showAdIfAvailable(activity, object : AppOpenHelper.OnShowAdCompleteListener {
            override fun onShowAdComplete() {
                adCallback?.onShowAdComplete()
            }
            override fun onHouseAdShown(reason: String) {
                adCallback?.onHouseAdShown(reason)
            }
            override fun onDiagnosticInfo(message: String) {
                adCallback?.onDiagnosticInfo(message)
            }
        })
    }

    fun launchAdInspector(context: Context, onComplete: ((error: AdInspectorError?) -> Unit)? = null) {
        val requestConfig = RequestConfiguration.Builder()
            .setTestDeviceIds(Constant.TEST_DEVICE_IDS)
            .build()
        MobileAds.setRequestConfiguration(requestConfig)
        MobileAds.openAdInspector { error ->
            onComplete?.invoke(error)
        }
    }
}
