package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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
    private fun isAdEnabled() =
        remoteConfigHelper.getBoolean("rv_enable")
                && !preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)

    private fun isHouseAdsEnabled() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)

    private fun isHouseAutoFallback() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)

    private fun createBridgedHouseCallback(adCallback: RewardAdCallback?): HouseRewardAdCallback {
        return object : HouseRewardAdCallback() {
            override fun onAdImpression() {
                adCallback?.onAdImpression()
            }

            override fun onAdClicked() {
                adCallback?.onAdClicked()
            }

            override fun onAdClosed() {
                adCallback?.onAdClosed()
            }

            override fun onAdFailedToLoad(errorMessage: String) {
                adCallback?.onAdFailedToLoad(null)
            }

            override fun onUserEarnedReward(rewardAmount: Int, rewardType: String) {
                // Call standard Google reward item callback with null since RewardItem cannot be mocked
                adCallback?.onUserEarnedReward(null)
                // Also call the dedicated hook for house reward
                adCallback?.onHouseRewardEarned(rewardAmount, rewardType)
            }
        }
    }

    private fun getAdRequest(timeout: Int = 60000): AdRequest {
        return AdRequest.Builder().setHttpTimeoutMillis(timeout).build()
    }

    fun showReward(
        activity: Activity,
        rewardAdUnitId: String,
        rewardedAd: RewardedAd?,
        timeOutMilliSecond: Int?,
        adCallback: RewardAdCallback?
    ) {
        // If device is offline and House Ads are enabled, show House Reward immediately
        if (!NetworkUtils.isNetworkAvailable(activity) && isHouseAdsEnabled()) {
            adCallback?.onHouseAdShown()
            houseRewardHelper.showHouseReward(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
            return
        }

        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            if (isHouseAdsEnabled()) {
                adCallback?.onHouseAdShown()
                houseRewardHelper.showHouseReward(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
            } else {
                adCallback?.onAdFailedToLoad()
            }
            return
        }

        if (rewardedAd == null) {
            val loadingAdsDialog = LoadingAdsDialog(activity)
            if (!activity.isFinishing && !activity.isDestroyed)
                loadingAdsDialog.show()
            loadReward(activity, rewardAdUnitId, timeOutMilliSecond, object : RewardAdCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    showReward(activity, rewardedAd, adCallback)
                    if (loadingAdsDialog.isShowing) {
                        loadingAdsDialog.dismiss()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError?) {
                    if (loadingAdsDialog.isShowing) {
                        loadingAdsDialog.dismiss()
                    }
                    if (isHouseAutoFallback() && !activity.isFinishing && !activity.isDestroyed) {
                        adCallback?.onHouseAdShown()
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
            })
        }
        else {
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
                adCallback?.onHouseAdShown()
                houseRewardHelper.showHouseReward(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
            } else {
                adCallback?.onAdFailedToLoad()
            }
            return
        }

        analyticsTracker.logEvent("aj_reward_show")
        rewardedAd.apply {
            onPaidEventListener = OnPaidEventListener { adValue: AdValue ->
                analyticsTracker.trackAdMobRevenueEvent(
                    adValue,
                    rewardedAd.adUnitId,
                    rewardedAd.responseInfo.loadedAdapterResponseInfo?.adSourceName
                        ?: "AdMob",
                    "Reward"
                )
            }
            fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent()
                    adFrequencyManager.recordRewardedShown()
                    adCallback?.onAdClosed()
                    analyticsTracker.logEvent("aj_reward_close")
                }

                override fun onAdFailedToShowFullScreenContent(var0: AdError) {
                    super.onAdFailedToShowFullScreenContent(var0)
                    if (isHouseAutoFallback() && !activity.isFinishing && !activity.isDestroyed) {
                        adCallback?.onHouseAdShown()
                        houseRewardHelper.showHouseReward(activity, createBridgedHouseCallback(adCallback), ignoreFrequencyCheck = true)
                    } else {
                        adCallback?.onAdFailedToShowFullScreenContent(var0)
                    }
                    analyticsTracker.logEvent("aj_reward_show_fail")
                }

                override fun onAdShowedFullScreenContent() {
                    super.onAdShowedFullScreenContent()
                    analyticsTracker.logEvent("aj_reward_show_success")
                }

                override fun onAdImpression() {
                    super.onAdImpression()
                    adCallback?.onAdImpression()
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    adCallback?.onAdClicked()
                    analyticsTracker.logEvent("aj_reward_click")
                }
            }
        }
        rewardedAd.show(activity, { rewardItem ->
            adCallback?.onUserEarnedReward(rewardItem)
        })
    }
    

    fun loadReward(
        context: Context,
        rewardAdUnitId: String,
        timeOutMilliSecond: Int?,
        adCallback: RewardAdCallback?
    ) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            adCallback?.onAdFailedToLoad(null)
            return
        }

        if (!isAdEnabled() || !admobConsentHelper.canRequestAds()) {
            adCallback?.onAdFailedToLoad(null)
            return
        }

        analyticsTracker.logEvent("aj_reward_load")

        val adUnitId = if (Constant.DEBUG_MODE) Constant.ADMOB_REWARDED_AD_UNIT_ID else rewardAdUnitId
        if (!adMobRateLimiter.canRequest(adUnitId)) {
            Log.w(TAG, "Rewarded adUnitId $adUnitId is in NO_FILL cooldown")
            val noFillError = LoadAdError(
                com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL,
                "در کولداون NO_FILL",
                "com.google.android.gms.ads",
                null,
                null
            )
            adCallback?.onAdFailedToLoad(noFillError)
            analyticsTracker.logEvent("aj_reward_load_fail_cooldown")
            return
        }

        RewardedAd.load(context, adUnitId, getAdRequest(timeOutMilliSecond ?: 60000), object: RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                if (adError.code == com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL) {
                    adMobRateLimiter.recordNoFill(adUnitId)
                }
                analyticsTracker.logEvent("aj_reward_load_fail")
                adCallback?.onAdFailedToLoad(adError)
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                analyticsTracker.logEvent("aj_reward_load_success")
                adCallback?.onAdLoaded(rewardedAd)
            }
        })
    }
}