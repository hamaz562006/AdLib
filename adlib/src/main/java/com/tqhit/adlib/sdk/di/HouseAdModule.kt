package com.tqhit.adlib.sdk.di

import com.tqhit.adlib.sdk.ads.AdFrequencyManager
import com.tqhit.adlib.sdk.ads.house.HouseAdHelper
import com.tqhit.adlib.sdk.ads.house.HouseAdManager
import com.tqhit.adlib.sdk.ads.house.HouseAppOpenHelper
import com.tqhit.adlib.sdk.ads.house.HouseBannerHelper
import com.tqhit.adlib.sdk.ads.house.HouseInterstitialHelper
import com.tqhit.adlib.sdk.ads.house.HouseNativeHelper
import com.tqhit.adlib.sdk.ads.house.HouseRewardHelper
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HouseAdModule {

    @Provides
    @Singleton
    fun provideHouseAdManager(
        remoteConfigHelper: FirebaseRemoteConfigHelper,
        analyticsTracker: AnalyticsTracker
    ): HouseAdManager {
        return HouseAdManager(remoteConfigHelper, analyticsTracker)
    }

    @Provides
    @Singleton
    fun provideHouseBannerHelper(
        houseAdManager: HouseAdManager,
        preferencesHelper: PreferencesHelper
    ): HouseBannerHelper {
        return HouseBannerHelper(houseAdManager, preferencesHelper)
    }

    @Provides
    @Singleton
    fun provideHouseInterstitialHelper(
        houseAdManager: HouseAdManager,
        preferencesHelper: PreferencesHelper,
        adFrequencyManager: AdFrequencyManager
    ): HouseInterstitialHelper {
        return HouseInterstitialHelper(houseAdManager, preferencesHelper, adFrequencyManager)
    }

    @Provides
    @Singleton
    fun provideHouseRewardHelper(
        houseAdManager: HouseAdManager,
        preferencesHelper: PreferencesHelper,
        adFrequencyManager: AdFrequencyManager
    ): HouseRewardHelper {
        return HouseRewardHelper(houseAdManager, preferencesHelper, adFrequencyManager)
    }

    @Provides
    @Singleton
    fun provideHouseNativeHelper(
        houseAdManager: HouseAdManager,
        preferencesHelper: PreferencesHelper
    ): HouseNativeHelper {
        return HouseNativeHelper(houseAdManager, preferencesHelper)
    }

    @Provides
    @Singleton
    fun provideHouseAppOpenHelper(
        houseAdManager: HouseAdManager,
        preferencesHelper: PreferencesHelper,
        adFrequencyManager: AdFrequencyManager
    ): HouseAppOpenHelper {
        return HouseAppOpenHelper(houseAdManager, preferencesHelper, adFrequencyManager)
    }

    @Provides
    @Singleton
    fun provideHouseAdHelper(
        houseBannerHelper: HouseBannerHelper,
        houseInterstitialHelper: HouseInterstitialHelper,
        houseRewardHelper: HouseRewardHelper,
        houseNativeHelper: HouseNativeHelper,
        houseAppOpenHelper: HouseAppOpenHelper,
        houseAdManager: HouseAdManager
    ): HouseAdHelper {
        return HouseAdHelper(
            houseBannerHelper,
            houseInterstitialHelper,
            houseRewardHelper,
            houseNativeHelper,
            houseAppOpenHelper,
            houseAdManager
        )
    }
}
