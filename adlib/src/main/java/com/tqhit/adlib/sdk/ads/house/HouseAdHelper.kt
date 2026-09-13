package com.tqhit.adlib.sdk.ads.house

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.tqhit.adlib.sdk.ads.callback.house.HouseAppOpenAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseBannerAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseInterstitialAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseNativeAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseRewardAdCallback
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseAdHelper @Inject constructor(
    val houseBannerHelper: HouseBannerHelper,
    val houseInterstitialHelper: HouseInterstitialHelper,
    val houseRewardHelper: HouseRewardHelper,
    val houseNativeHelper: HouseNativeHelper,
    val houseAppOpenHelper: HouseAppOpenHelper,
    val houseAdManager: HouseAdManager
) {
    fun showBanner(context: Context, container: ViewGroup, callback: HouseBannerAdCallback? = null): View? {
        return houseBannerHelper.loadHouseBanner(context, container, callback)
    }

    fun showInterstitial(activity: Activity, callback: HouseInterstitialAdCallback? = null) {
        houseInterstitialHelper.showHouseInterstitial(activity, callback)
    }

    fun showReward(activity: Activity, callback: HouseRewardAdCallback? = null) {
        houseRewardHelper.showHouseReward(activity, callback)
    }

    fun showNativeSmall(context: Context, container: ViewGroup, callback: HouseNativeAdCallback? = null): View? {
        return houseNativeHelper.showHouseNativeSmall(context, container, callback)
    }

    fun showNativeFull(context: Context, container: ViewGroup, callback: HouseNativeAdCallback? = null): View? {
        return houseNativeHelper.showHouseNativeFull(context, container, callback)
    }

    fun showAppOpen(activity: Activity, onComplete: HouseAppOpenHelper.OnShowAdCompleteListener? = null, callback: HouseAppOpenAdCallback? = null) {
        houseAppOpenHelper.showHouseAppOpen(activity, onComplete, callback)
    }
}
