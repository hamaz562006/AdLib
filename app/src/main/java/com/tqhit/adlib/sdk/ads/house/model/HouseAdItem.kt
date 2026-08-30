package com.tqhit.adlib.sdk.ads.house.model

data class HouseAdItem(
    val id: String = "default_house_ad",
    val title: String = "Explore Pro Tools",
    val description: String = "Upgrade your experience with our high-performance utility suite.",
    val iconResName: String? = null,
    val mediaResName: String? = null,
    val iconUrl: String? = null,
    val mediaUrl: String? = null,
    val ctaText: String = "Install Now",
    val targetUrl: String = "https://play.google.com/store",
    val targetPackageName: String? = null,
    val rating: Float = 4.9f,
    val rewardAmount: Int = 20,
    val rewardType: String = "Coins",
    val adType: HouseAdType = HouseAdType.BANNER,
    val countdownSeconds: Int = 5
)
