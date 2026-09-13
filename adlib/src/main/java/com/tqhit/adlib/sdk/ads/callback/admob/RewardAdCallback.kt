package com.tqhit.adlib.sdk.ads.callback.admob

import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd

open class RewardAdCallback {
    open fun onAdClicked() {}

    open fun onAdClosed() {}

    open fun onAdFailedToLoad(adError: LoadAdError? = null) {}

    open fun onAdFailedToShowFullScreenContent(var0: FullScreenContentError? = null) {}

    open fun onAdImpression() {}

    open fun onAdLoaded(rewardedAd: RewardedAd) {}

    open fun onUserEarnedReward(rewardItem: RewardItem?) {}

    open fun onHouseRewardEarned(amount: Int, type: String) {}

    open fun onHouseAdShown(reason: String) {}

    open fun onAdOpened() {}

    fun onAdSwipeGestureClicked() {}
}