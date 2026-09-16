package com.tqhit.adlib.sdk.ads.callback.admob

import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError

open class BannerAdCallback {
    open fun onAdClicked() {}

    open fun onAdClosed() {}

    open fun onAdFailedToLoad(adError: LoadAdError? = null) {}

    open fun onAdImpression() {}

    open fun onAdLoaded(adView: AdView) {}

    open fun onAdOpened() {}

    open fun onHouseAdShown(reason: String) {}

    open fun onDiagnosticInfo(message: String) {}

    fun onAdSwipeGestureClicked() {}
}