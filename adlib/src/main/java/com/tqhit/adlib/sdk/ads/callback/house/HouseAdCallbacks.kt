package com.tqhit.adlib.sdk.ads.callback.house

import com.tqhit.adlib.sdk.ads.house.model.HouseAdItem

open class HouseAdCallback {
    open fun onAdLoaded(houseAdItem: HouseAdItem) {}
    open fun onAdFailedToLoad(errorMessage: String) {}
    open fun onAdImpression() {}
    open fun onAdClicked() {}
    open fun onAdClosed() {}
}

open class HouseBannerAdCallback : HouseAdCallback()

open class HouseInterstitialAdCallback : HouseAdCallback()

open class HouseNativeAdCallback : HouseAdCallback()

open class HouseRewardAdCallback : HouseAdCallback() {
    open fun onUserEarnedReward(rewardAmount: Int, rewardType: String) {}
}

open class HouseAppOpenAdCallback : HouseAdCallback()
