package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.tqhit.adlib.sdk.ads.AdFrequencyManager
import com.tqhit.adlib.sdk.ads.callback.admob.RewardAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseRewardAdCallback
import com.tqhit.adlib.sdk.ads.house.HouseRewardHelper
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.ui.dialog.LoadingAdsDialog
import com.tqhit.adlib.sdk.utils.Constant
import com.tqhit.adlib.sdk.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardHelper @Inject constructor(
    private val admobConsentHelper: AdmobConsentHelper,
    private val analyticsTracker: AnalyticsTracker,
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val preferencesHelper: PreferencesHelper,
    private val adFrequencyManager: AdFrequencyManager,
    private val adMobRateLimiter: AdmobRateLimiter,
    private val houseRewardHelper: HouseRewardHelper
) {
    private val TAG = RewardHelper::class.java.simpleName
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun isAdEnabled() =
        remoteConfigHelper.getBoolean("rv_enable")
                && !preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)

    private fun isHouseAdsEnabled() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)

    private fun isHouseAutoFallback() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun createBridgedHouseCallback(adCallback: RewardAdCallback?): HouseRewardAdCallback {
        return object : HouseRewardAdCallback() {
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
                runOnUiThread { adCallback?.onAdFailedToLoad(null) }
            }

            override fun onUserEarnedReward(rewardAmount: Int, rewardType: String) {
                runOnUiThread {
                    // Call standard Google reward item callback with null since RewardItem cannot be mocked
                    adCallback?.onUserEarnedReward(null)
                    // Also call the dedicated hook for house reward
                    adCallback?.onHouseRewardEarned(rewardAmount, rewardType)
                }
            }
        }
    }

    private fun getAdRequest(adUnitId: String): AdRequest {
        return AdRequest.Builder(adUnitId).build()
    }

    fun showReward(
        activity: Activity,
        rewardAdUnitId: String,
        rewardedAd: RewardedAd?,
        timeOutMilliSecond: Int?,
        adCallback: RewardAdCallback?
    ) {
        // If device is offline and House Ads are enabled, show House Reward immediately
        if (!NetworkUtils.isNetworkAvailable(activity)) {
            if (isHouseAdsEnabled()) {
                runOnUiThread {
                    adCallback?.onHouseAdShown("Network unavailable")
                    houseRewardHelper.showHouseReward(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                }
            } else {
                runOnUiThread { adCallback?.onAdFailedToLoad() }
            }
            return
        }

        if (rewardedAd != null) {
            showReward(activity, rewardedAd, adCallback)
            return
        }

        // For loading and showing AdMob reward: pre-check reachability if house ads enabled
        if (isHouseAdsEnabled()) {
            CoroutineScope(Dispatchers.Main).launch {
                val reachable = NetworkUtils.isAdServerReachable()
                if (!reachable && !activity.isFinishing && !activity.isDestroyed) {
                    adCallback?.onHouseAdShown("Ad server unreachable (possibly blocked network)")
                    houseRewardHelper.showHouseReward(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                } else if (!activity.isFinishing && !activity.isDestroyed) {
                    executeShowReward(activity, rewardAdUnitId, null, timeOutMilliSecond, adCallback)
                }
            }
        } else {
            executeShowReward(activity, rewardAdUnitId, null, timeOutMilliSecond, adCallback)
        }
    }

    private fun executeShowReward(
        activity: Activity,
        rewardAdUnitId: String,
        rewardedAd: RewardedAd?,
        timeOutMilliSecond: Int?,
        adCallback: RewardAdCallback?
    ) {
        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            if (isHouseAdsEnabled()) {
                runOnUiThread {
                    adCallback?.onHouseAdShown("AdMob disabled or consent missing")
                    houseRewardHelper.showHouseReward(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                }
            } else {
                runOnUiThread { adCallback?.onAdFailedToLoad() }
            }
            return
        }

        if (rewardedAd == null) {
            val loadingAdsDialog = LoadingAdsDialog(activity)
            if (!activity.isFinishing && !activity.isDestroyed) {
                loadingAdsDialog.show()
            }
            loadReward(activity, rewardAdUnitId, timeOutMilliSecond, object : RewardAdCallback() {
                override fun onAdLoaded(loadedRewardedAd: RewardedAd) {
                    runOnUiThread {
                        if (loadingAdsDialog.isShowing) {
                            loadingAdsDialog.dismiss()
                        }
                        showReward(activity, loadedRewardedAd, adCallback)
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError?) {
                    runOnUiThread {
                        if (loadingAdsDialog.isShowing) {
                            loadingAdsDialog.dismiss()
                        }
                        if (isHouseAutoFallback() && !activity.isFinishing && !activity.isDestroyed) {
                            val reason = adError?.message ?: adError?.code?.toString() ?: "Unknown AdMob error"
                            adCallback?.onHouseAdShown(reason)
                            houseRewardHelper.showHouseReward(
                                activity,
                                object : HouseRewardAdCallback() {
                                    override fun onAdImpression() { adCallback?.onAdImpression() }
                                    override fun onAdClicked() { adCallback?.onAdClicked() }
                                    override fun onAdClosed() { adCallback?.onAdClosed() }
                                    override fun onUserEarnedReward(rewardAmount: Int, rewardType: String) {
                                        adCallback?.onUserEarnedReward(null)
                                        adCallback?.onHouseRewardEarned(rewardAmount, rewardType)
                                    }
                                    override fun onAdFailedToLoad(errorMessage: String) {
                                        adCallback?.onAdFailedToLoad(adError)
                                    }
                                },
                                ignoreFrequencyCheck = true
                            )
                        } else {
                            adCallback?.onAdFailedToLoad(adError)
                        }
                    }
                }
            })
        } else {
            showReward(activity, rewardedAd, adCallback)
        }
    }

    fun showReward(
        activity: Activity,
        rewardedAd: RewardedAd,
        adCallback: RewardAdCallback?
    ) {
        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            if (isHouseAdsEnabled()) {
                runOnUiThread {
                    adCallback?.onHouseAdShown("AdMob disabled or consent missing")
                    houseRewardHelper.showHouseReward(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                }
            } else {
                runOnUiThread { adCallback?.onAdFailedToLoad() }
            }
            return
        }

        analyticsTracker.logEvent("aj_reward_show")
        rewardedAd.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                runOnUiThread {
                    adFrequencyManager.recordRewardedShown()
                    adCallback?.onAdClosed()
                    analyticsTracker.logEvent("aj_reward_close")
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                runOnUiThread {
                    if (isHouseAutoFallback() && !activity.isFinishing && !activity.isDestroyed) {
                        val reason = error.message.ifBlank { error.code.toString() }
                        adCallback?.onHouseAdShown(reason)
                        houseRewardHelper.showHouseReward(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                    } else {
                        adCallback?.onAdFailedToShowFullScreenContent(error)
                    }
                    analyticsTracker.logEvent("aj_reward_show_fail")
                }
            }

            override fun onAdShowedFullScreenContent() {
                runOnUiThread {
                    analyticsTracker.logEvent("aj_reward_show_success")
                }
            }

            override fun onAdImpression() {
                runOnUiThread {
                    adCallback?.onAdImpression()
                }
            }

            override fun onAdClicked() {
                runOnUiThread {
                    adCallback?.onAdClicked()
                    analyticsTracker.logEvent("aj_reward_click")
                }
            }

            override fun onAdPaid(adValue: AdValue) {
                analyticsTracker.trackAdMobRevenueEvent(
                    adValue,
                    rewardedAd.adUnitId,
                    rewardedAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "AdMob",
                    "Reward"
                )
            }
        }

        rewardedAd.show(activity, OnUserEarnedRewardListener { rewardItem ->
            runOnUiThread {
                adCallback?.onUserEarnedReward(rewardItem)
            }
        })
    }

    fun loadReward(
        context: Context,
        rewardAdUnitId: String,
        timeOutMilliSecond: Int?,
        adCallback: RewardAdCallback?
    ) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            runOnUiThread { adCallback?.onAdFailedToLoad(null) }
            return
        }

        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            runOnUiThread { adCallback?.onAdFailedToLoad(null) }
            return
        }

        analyticsTracker.logEvent("aj_reward_load")

        val adUnitId = if (Constant.DEBUG_MODE) Constant.ADMOB_REWARDED_AD_UNIT_ID else rewardAdUnitId
        if (!adMobRateLimiter.canRequest(adUnitId)) {
            Log.w(TAG, "Rewarded adUnitId $adUnitId is in NO_FILL cooldown")
            val noFillError = LoadAdError(
                LoadAdError.ErrorCode.NO_FILL,
                "در کولداون NO_FILL",
                null
            )
            runOnUiThread {
                adCallback?.onAdFailedToLoad(noFillError)
                analyticsTracker.logEvent("aj_reward_load_fail_cooldown")
            }
            return
        }

        val adRequest = getAdRequest(adUnitId)
        RewardedAd.load(adRequest, object : AdLoadCallback<RewardedAd> {
            override fun onAdLoaded(ad: RewardedAd) {
                runOnUiThread {
                    analyticsTracker.logEvent("aj_reward_load_success")
                    adCallback?.onAdLoaded(ad)
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                if (adError.code == LoadAdError.ErrorCode.NO_FILL) {
                    adMobRateLimiter.recordNoFill(adUnitId)
                }
                runOnUiThread {
                    analyticsTracker.logEvent("aj_reward_load_fail")
                    adCallback?.onAdFailedToLoad(adError)
                }
            }
        })
    }
}
