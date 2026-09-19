package com.tqhit.adlib.sdk.utils

object Constant {
    var DEBUG_MODE = false

    const val IS_PREMIUM = "is_premium"
    const val RC_HOUSE_ADS_ENABLED = "house_ads_enabled"
    const val RC_HOUSE_ADS_AUTO_FALLBACK = "house_ads_auto_fallback"
    const val RC_HOUSE_ADS_JSON = "house_ads_json"

    // AdMob Next-Gen test ad unit IDs
    const val ADMOB_BANNER_AD_UNIT_ID = "/21775744923/example/adaptive-banner"
    const val ADMOB_COLLAPSIBLE_BANNER_AD_UNIT_ID = "/21775744923/example/adaptive-banner"
    const val ADMOB_INTERSTITIAL_AD_UNIT_ID = "/21775744923/example/interstitial"
    const val ADMOB_REWARDED_AD_UNIT_ID = "/21775744923/example/rewarded"
    const val ADMOB_NATIVE_AD_UNIT_ID = "/21775744923/example/native"
    const val ADMOB_AOA_AD_UNIT_ID = "/21775744923/example/app-open"

    /**
     * Real (physical) test devices are NOT automatically treated as test devices by AdMob —
     * only emulators are. To register a physical device, run the app once, find the line in
     * Logcat that looks like:
     *   "Use RequestConfiguration.Builder.setTestDeviceIds(Arrays.asList("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"))
     *    to get test ads on this device."
     * and add that hashed device ID string here. Without this, Ad Inspector will fail with
     * NOT_IN_TEST_MODE on a real device, and some real (non-guaranteed) ad units may not
     * reliably serve test creative to it either.
     */
    val TEST_DEVICE_IDS: List<String> = listOf(
        // "PASTE_YOUR_DEVICE_HASHED_ID_FROM_LOGCAT_HERE"
    )
}
