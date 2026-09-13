package com.tqhit.adlib.sdk.di

import android.content.Context
import com.tqhit.adlib.sdk.ads.AdFrequencyManager
import com.tqhit.adlib.sdk.ads.admob.AdaptiveBannerHelper
import com.tqhit.adlib.sdk.ads.admob.AdmobRateLimiter
import com.tqhit.adlib.sdk.ads.cache.AdCacheManager
import com.tqhit.adlib.sdk.ads.admob.AdmobConsentHelper
import com.tqhit.adlib.sdk.ads.admob.AdmobHelper
import com.tqhit.adlib.sdk.ads.admob.AppOpenHelper
import com.tqhit.adlib.sdk.ads.admob.BannerHelper
import com.tqhit.adlib.sdk.ads.admob.InterstitialHelper
import com.tqhit.adlib.sdk.ads.admob.NativeHelper
import com.tqhit.adlib.sdk.ads.admob.RewardHelper
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
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdmobModule {

    @Provides
    @Singleton
    fun provideAdCacheManager(): AdCacheManager {
        return AdCacheManager()
    }

    @Provides
    @Singleton
    fun provideAdmobRateLimiter(remoteConfigHelper: FirebaseRemoteConfigHelper): AdmobRateLimiter {
        return AdmobRateLimiter(remoteConfigHelper)
    }

    @Provides
    @Singleton
    fun provideAdaptiveBannerHelper(
        preferencesHelper: PreferencesHelper,
        remoteConfigHelper: FirebaseRemoteConfigHelper,
        houseBannerHelper: HouseBannerHelper
    ): AdaptiveBannerHelper {
        return AdaptiveBannerHelper(preferencesHelper, remoteConfigHelper, houseBannerHelper)
    }

    @Provides
    @Singleton
    fun provideAdmobConsentHelper(@ApplicationContext context: Context): AdmobConsentHelper {
        return AdmobConsentHelper(context)
    }

    @Provides
    @Singleton
    fun provideAdmobHelper(
        @ApplicationContext context: Context,
        bannerHelper: BannerHelper,
        interstitialHelper: InterstitialHelper,
        rewardHelper: RewardHelper,
        nativeHelper: NativeHelper,
        appOpenHelper: AppOpenHelper
    ): AdmobHelper {
        return AdmobHelper(context, bannerHelper, interstitialHelper, rewardHelper, nativeHelper, appOpenHelper)
    }

    @Provides
    @Singleton
    fun provideBannerHelper(
        admobConsentHelper: AdmobConsentHelper,
        analyticsTracker: AnalyticsTracker,
        remoteConfigHelper: FirebaseRemoteConfigHelper,
        preferencesHelper: PreferencesHelper,
        houseBannerHelper: HouseBannerHelper
    ): BannerHelper {
        return BannerHelper(admobConsentHelper, analyticsTracker, remoteConfigHelper, preferencesHelper, houseBannerHelper)
    }

    @Provides
    @Singleton
    fun provideInterstitialHelper(
        admobConsentHelper: AdmobConsentHelper,
        analyticsTracker: AnalyticsTracker,
        remoteConfigHelper: FirebaseRemoteConfigHelper,
        preferencesHelper: PreferencesHelper,
        adFrequencyManager: AdFrequencyManager,
        adMobRateLimiter: AdmobRateLimiter,
        houseInterstitialHelper: HouseInterstitialHelper
    ): InterstitialHelper {
        return InterstitialHelper(admobConsentHelper, analyticsTracker, remoteConfigHelper, preferencesHelper, adFrequencyManager, adMobRateLimiter, houseInterstitialHelper)
    }

    @Provides
    @Singleton
    fun provideNativeHelper(
        admobConsentHelper: AdmobConsentHelper,
        analyticsTracker: AnalyticsTracker,
        remoteConfigHelper: FirebaseRemoteConfigHelper,
        preferencesHelper: PreferencesHelper,
        houseNativeHelper: HouseNativeHelper
    ): NativeHelper {
        return NativeHelper(admobConsentHelper, analyticsTracker, remoteConfigHelper, preferencesHelper, houseNativeHelper)
    }

    @Provides
    @Singleton
    fun provideRewardHelper(
        admobConsentHelper: AdmobConsentHelper,
        analyticsTracker: AnalyticsTracker,
        remoteConfigHelper: FirebaseRemoteConfigHelper,
        preferencesHelper: PreferencesHelper,
        adFrequencyManager: AdFrequencyManager,
        adMobRateLimiter: AdmobRateLimiter,
        houseRewardHelper: HouseRewardHelper
    ): RewardHelper {
        return RewardHelper(admobConsentHelper, analyticsTracker, remoteConfigHelper, preferencesHelper, adFrequencyManager, adMobRateLimiter, houseRewardHelper)
    }

    @Provides
    @Singleton
    fun provideAppOpenHelper(
        admobConsentHelper: AdmobConsentHelper,
        analyticsTracker: AnalyticsTracker,
        remoteConfigHelper: FirebaseRemoteConfigHelper,
        preferencesHelper: PreferencesHelper,
        adFrequencyManager: AdFrequencyManager,
        adMobRateLimiter: AdmobRateLimiter,
        houseAppOpenHelper: HouseAppOpenHelper
    ): AppOpenHelper {
        return AppOpenHelper(admobConsentHelper, analyticsTracker, remoteConfigHelper, preferencesHelper, adFrequencyManager, adMobRateLimiter, houseAppOpenHelper)
    }
}