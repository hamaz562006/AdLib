package com.tqhit.adlib.sdk.ads.callback.admob

import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd

open class InterstitialAdCallback {
    open fun onAdClicked() {}

    open fun onAdClosed() {}

    open fun onAdFailedToLoad(adError: LoadAdError? = null) {}

    open fun onAdImpression() {}

    open fun onAdLoaded(interstitialAd: InterstitialAd) {}

    open fun onAdOpened() {}

    open fun onHouseAdShown(reason: String) {}

    fun onAdSwipeGestureClicked() {}
}