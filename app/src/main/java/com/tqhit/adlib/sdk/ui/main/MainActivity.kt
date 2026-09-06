package com.tqhit.adlib.sdk.ui.main

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Toast
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.ump.FormError
import com.tqhit.adlib.R
import com.tqhit.adlib.databinding.ActivityMainBinding
import com.tqhit.adlib.sdk.ads.AdFrequencyManager
import com.tqhit.adlib.sdk.ads.admob.AdmobConsentHelper
import com.tqhit.adlib.sdk.ads.admob.AdmobHelper
import com.tqhit.adlib.sdk.ads.admob.AppOpenHelper
import com.tqhit.adlib.sdk.ads.admob.BannerHelper
import com.tqhit.adlib.sdk.ads.admob.InterstitialHelper
import com.tqhit.adlib.sdk.ads.admob.NativeHelper
import com.tqhit.adlib.sdk.ads.admob.RewardHelper
import com.tqhit.adlib.sdk.ads.callback.admob.BannerAdCallback
import com.tqhit.adlib.sdk.ads.callback.admob.InterstitialAdCallback
import com.tqhit.adlib.sdk.ads.callback.admob.NativeAdCallback
import com.tqhit.adlib.sdk.ads.callback.admob.RewardAdCallback
import com.tqhit.adlib.sdk.ads.callback.common.IAdmobConsentCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseAppOpenAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseBannerAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseInterstitialAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseNativeAdCallback
import com.tqhit.adlib.sdk.ads.callback.house.HouseRewardAdCallback
import com.tqhit.adlib.sdk.ads.house.HouseAdHelper
import com.tqhit.adlib.sdk.ads.house.HouseAppOpenHelper
import com.tqhit.adlib.sdk.ads.house.model.HouseAdItem
import com.tqhit.adlib.sdk.ads.loader.ActivityAdLoader
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.base.ui.AdLibBaseActivity
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.utils.Constant
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AdLibBaseActivity<ActivityMainBinding>() {
    override val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    @Inject lateinit var admobConsentHelper: AdmobConsentHelper
    @Inject lateinit var admobHelper: AdmobHelper
    @Inject lateinit var bannerHelper: BannerHelper
    @Inject lateinit var interstitialHelper: InterstitialHelper
    @Inject lateinit var nativeHelper: NativeHelper
    @Inject lateinit var rewardHelper: RewardHelper
    @Inject lateinit var appOpenHelper: AppOpenHelper
    @Inject lateinit var houseAdHelper: HouseAdHelper
    @Inject lateinit var remoteConfigHelper: FirebaseRemoteConfigHelper
    @Inject lateinit var analyticsTracker: AnalyticsTracker
    @Inject lateinit var preferencesHelper: PreferencesHelper
    @Inject lateinit var adFrequencyManager: AdFrequencyManager
    @Inject lateinit var activityAdLoader: ActivityAdLoader

    private var preloadedInterstitialAd: InterstitialAd? = null
    private var currentNativeAd: NativeAd? = null
    private var currentBannerAdView: AdView? = null
    private var rewardTokens: Int = 0

    private val logHistory = StringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun setupUI() {
        super.setupUI()
        setupHeaderAndControls()
        setupRemoteConfigSection()
        setupAdMobSection()
        setupHouseAdsSection()
        setupFrequencyAndAnalyticsSection()
    }

    override fun setupData() {
        super.setupData()

        logMessage("AdLib SDK Initializing...", "INIT")
        updateNetworkStatus()
        refreshRemoteConfigDisplay()
        refreshFrequencyStatus()

        // Initialize Remote Config & AdMob on start
        remoteConfigHelper.fetchAndActivate({ success ->
            logMessage("Remote Config fetch result: $success", if (success) "SUCCESS" else "WARN")
            runOnUiThread {
                refreshRemoteConfigDisplay()
            }
        }, R.xml.remote_config_defaults)

        admobHelper.initAdmob({
            logMessage("AdMob SDK initialized successfully", "SUCCESS")
            appOpenHelper.setAdUnitId(Constant.ADMOB_AOA_AD_UNIT_ID)
            appOpenHelper.loadAd(applicationContext)
        })

        // Request UMP Consent
        Handler(Looper.getMainLooper()).postDelayed({
            requestConsent()
        }, 1500)
    }

    private fun setupHeaderAndControls() {
        binding.switchPremium.isChecked = preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)
        binding.switchPremium.setOnCheckedChangeListener { _, isChecked ->
            preferencesHelper.saveBoolean(Constant.IS_PREMIUM, isChecked)
            logMessage("Premium Mode set to: $isChecked (Ads blocked: $isChecked)", "CONFIG")
            Toast.makeText(this, "Premium Mode: $isChecked", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRemoteConfigSection() {
        binding.btnFetchRemoteConfig.setOnClickListener {
            logMessage("Fetching Remote Config from Firebase...", "CONFIG")
            remoteConfigHelper.fetchAndActivate({ success ->
                runOnUiThread {
                    logMessage("Remote Config sync complete: $success", if (success) "SUCCESS" else "FAIL")
                    refreshRemoteConfigDisplay()
                    refreshFrequencyStatus()
                }
            }, R.xml.remote_config_defaults)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun refreshRemoteConfigDisplay() {
        val bn = remoteConfigHelper.getBoolean("bn_enable")
        val iv = remoteConfigHelper.getBoolean("iv_enable")
        val nt = remoteConfigHelper.getBoolean("nt_enable")
        val rv = remoteConfigHelper.getBoolean("rv_enable")
        val aoa = remoteConfigHelper.getBoolean("aoa_enable")
        val ivFreq = remoteConfigHelper.getLong("RC_IV_SHOW_FREQUENCY")
        val ivDelayRv = remoteConfigHelper.getLong("RC_IV_DELAY_SHOW_AFTER_RV")
        val aoaFreq = remoteConfigHelper.getLong("RC_AOA_SHOW_FREQUENCY")

        binding.tvRemoteConfigValues.text = """
            • bn_enable (Banner): $bn
            • iv_enable (Interstitial): $iv
            • nt_enable (Native): $nt
            • rv_enable (Rewarded): $rv
            • aoa_enable (App Open): $aoa
            • IV Frequency: ${ivFreq}s | Delay after RV: ${ivDelayRv}s | AOA Freq: ${aoaFreq}s
        """.trimIndent()
    }

    private fun setupAdMobSection() {
        // Standard Banner
        binding.btnLoadBanner.setOnClickListener {
            logMessage("Loading AdMob Standard Banner...", "BANNER")
            binding.flBannerContainer.removeAllViews()
            bannerHelper.showBannerWithFallback(
                this,
                Constant.ADMOB_BANNER_AD_UNIT_ID,
                binding.flBannerContainer,
                60000,
                object : BannerAdCallback() {
                    override fun onHouseAdShown() {
                        logMessage("Falling back to HOUSE Ad (AdMob unavailable)", "HOUSE_FALLBACK")
                    }

                    override fun onAdLoaded(adView: AdView) {
                        currentBannerAdView = adView
                        logMessage("AdMob Standard Banner loaded", "SUCCESS")
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError?) {
                        logMessage("AdMob Banner failed: ${adError?.message ?: "Unknown"}", "ERROR")
                    }

                    override fun onAdClicked() {
                        logMessage("AdMob Banner clicked", "CLICK")
                    }

                    override fun onAdClosed() {
                        logMessage("Banner closed", "INFO")
                    }
                }
            )
        }

        // Collapsible Banner
        binding.btnLoadCollapsibleBanner.setOnClickListener {
            logMessage("Loading AdMob Collapsible Banner...", "BANNER")
            binding.flBannerContainer.removeAllViews()
            val adView = bannerHelper.loadCollapsibleBanner(
                this,
                Constant.ADMOB_COLLAPSIBLE_BANNER_AD_UNIT_ID,
                60000,
                object : BannerAdCallback() {
                    override fun onAdLoaded(adView: AdView) {
                        logMessage("AdMob Collapsible Banner loaded", "SUCCESS")
                        binding.flBannerContainer.removeAllViews()
                        binding.flBannerContainer.addView(adView)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError?) {
                        logMessage("Collapsible Banner failed: ${adError?.message ?: "Unknown"}", "ERROR")
                    }
                }
            )
            if (adView != null) {
                binding.flBannerContainer.removeAllViews()
                binding.flBannerContainer.addView(adView)
            }
        }

        // Clear Banner
        binding.btnClearBanner.setOnClickListener {
            binding.flBannerContainer.removeAllViews()
            currentBannerAdView?.destroy()
            currentBannerAdView = null
            logMessage("Banner container cleared", "INFO")
        }

        // Show Interstitial
        binding.btnShowInterstitial.setOnClickListener {
            logMessage("Requesting AdMob Interstitial Ad...", "INTERSTITIAL")
            if (preloadedInterstitialAd != null) {
                interstitialHelper.showInterstitial(
                    this,
                    preloadedInterstitialAd!!,
                    object : InterstitialAdCallback() {
                        override fun onHouseAdShown() {
                            logMessage("Falling back to HOUSE Ad (AdMob unavailable)", "HOUSE_FALLBACK")
                        }

                        override fun onAdClosed() {
                            logMessage("Interstitial Ad closed", "INFO")
                            preloadedInterstitialAd = null
                            refreshFrequencyStatus()
                        }

                        override fun onAdClicked() {
                            logMessage("Interstitial Ad clicked", "CLICK")
                        }
                    }
                )
            } else {
                interstitialHelper.showInterstitial(
                    this,
                    Constant.ADMOB_INTERSTITIAL_AD_UNIT_ID,
                    null,
                    60000,
                    object : InterstitialAdCallback() {
                        override fun onHouseAdShown() {
                            logMessage("Falling back to HOUSE Ad (AdMob unavailable)", "HOUSE_FALLBACK")
                        }

                        override fun onAdClosed() {
                            logMessage("Interstitial Ad closed", "INFO")
                            refreshFrequencyStatus()
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError?) {
                            logMessage("Interstitial failed to load: ${loadAdError?.message}", "ERROR")
                        }

                        override fun onAdClicked() {
                            logMessage("Interstitial clicked", "CLICK")
                        }
                    }
                )
            }
        }

        // Preload Interstitial
        binding.btnPreloadInterstitial.setOnClickListener {
            logMessage("Preloading AdMob Interstitial in background...", "PRELOAD")
            interstitialHelper.loadInterstitial(
                this,
                Constant.ADMOB_INTERSTITIAL_AD_UNIT_ID,
                60000,
                object : InterstitialAdCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        preloadedInterstitialAd = interstitialAd
                        logMessage("Interstitial preloaded & cached in memory", "SUCCESS")
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError?) {
                        logMessage("Preload Interstitial failed: ${loadAdError?.message}", "ERROR")
                    }
                }
            )
        }

        // Native Small
        binding.btnLoadNativeSmall.setOnClickListener {
            logMessage("Loading AdMob Native Small Ad...", "NATIVE")
            binding.flNativeContainer.removeAllViews()
            nativeHelper.loadNativeWithFallback(
                this,
                Constant.ADMOB_NATIVE_AD_UNIT_ID,
                60000,
                binding.flNativeContainer,
                useFullLayout = false,
                object : NativeAdCallback() {
                    override fun onHouseAdShown() {
                        logMessage("Falling back to HOUSE Ad (AdMob unavailable)", "HOUSE_FALLBACK")
                    }

                    override fun onAdLoaded(nativeAd: NativeAd) {
                        currentNativeAd = nativeAd
                        logMessage("AdMob Native Small loaded. Populating views...", "SUCCESS")
                        val nativeView = LayoutInflater.from(this@MainActivity)
                            .inflate(R.layout.ad_native_bot, binding.flNativeContainer, false) as NativeAdView
                        nativeHelper.showNative(nativeAd, nativeView)
                        binding.flNativeContainer.removeAllViews()
                        binding.flNativeContainer.addView(nativeView)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError?) {
                        logMessage("Native Small failed: ${adError?.message}", "ERROR")
                    }

                    override fun onAdClicked() {
                        logMessage("Native Small clicked", "CLICK")
                    }

                    override fun onAdClosed() {
                        logMessage("Native Small closed", "INFO")
                    }
                }
            )
        }

        // Native Full
        binding.btnLoadNativeFull.setOnClickListener {
            logMessage("Loading AdMob Native Full Ad...", "NATIVE")
            binding.flNativeContainer.removeAllViews()
            nativeHelper.loadNativeWithFallback(
                this,
                Constant.ADMOB_NATIVE_AD_UNIT_ID,
                60000,
                binding.flNativeContainer,
                useFullLayout = true,
                object : NativeAdCallback() {
                    override fun onHouseAdShown() {
                        logMessage("Falling back to HOUSE Ad (AdMob unavailable)", "HOUSE_FALLBACK")
                    }

                    override fun onAdLoaded(nativeAd: NativeAd) {
                        currentNativeAd = nativeAd
                        logMessage("AdMob Native Full loaded. Populating views...", "SUCCESS")
                        val nativeView = LayoutInflater.from(this@MainActivity)
                            .inflate(R.layout.ad_native_full, binding.flNativeContainer, false) as NativeAdView
                        nativeHelper.showNative(nativeAd, nativeView)
                        binding.flNativeContainer.removeAllViews()
                        binding.flNativeContainer.addView(nativeView)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError?) {
                        logMessage("Native Full failed: ${adError?.message}", "ERROR")
                    }

                    override fun onAdClicked() {
                        logMessage("Native Full clicked", "CLICK")
                    }

                    override fun onAdClosed() {
                        logMessage("Native Full closed", "INFO")
                    }
                }
            )
        }

        // Clear Native
        binding.btnClearNative.setOnClickListener {
            binding.flNativeContainer.removeAllViews()
            currentNativeAd?.destroy()
            currentNativeAd = null
            logMessage("Native container cleared", "INFO")
        }

        // Rewarded Video Ad
        binding.btnShowRewarded.setOnClickListener {
            logMessage("Loading & Showing Rewarded Video Ad...", "REWARDED")
            rewardHelper.showReward(
                this,
                Constant.ADMOB_REWARDED_AD_UNIT_ID,
                null,
                60000,
                object : RewardAdCallback() {
                    override fun onHouseAdShown() {
                        logMessage("Falling back to HOUSE Ad (AdMob unavailable)", "HOUSE_FALLBACK")
                    }

                    override fun onUserEarnedReward(rewardItem: RewardItem?) {
                        val amount = rewardItem?.amount ?: 10
                        val type = rewardItem?.type ?: "coins"
                        rewardTokens += amount
                        logMessage("USER EARNED REWARD! +$amount ($type)", "REWARD")
                        runOnUiThread {
                            binding.tvRewardPoints.text = "Tokens: $rewardTokens"
                        }
                    }

                    override fun onAdClosed() {
                        logMessage("Rewarded Video closed", "INFO")
                        refreshFrequencyStatus()
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError?) {
                        logMessage("Rewarded ad failed: ${adError?.message}", "ERROR")
                    }
                }
            )
        }

        // App Open Ad
        binding.btnShowAOA.setOnClickListener {
            logMessage("Triggering App Open Ad...", "AOA")
            appOpenHelper.showAdIfAvailable(
                this,
                object : AppOpenHelper.OnShowAdCompleteListener {
                    override fun onHouseAdShown() {
                        logMessage("Falling back to HOUSE Ad (AdMob unavailable)", "HOUSE_FALLBACK")
                    }

                    override fun onShowAdComplete() {
                        logMessage("App Open Ad display completed", "INFO")
                        refreshFrequencyStatus()
                    }
                }
            )
        }

        // UMP Consent
        binding.btnRequestConsent.setOnClickListener {
            requestConsent()
        }

        // Ad Inspector
        binding.btnAdInspector.setOnClickListener {
            logMessage("Opening Ad Inspector...", "INSPECTOR")
            admobHelper.launchAdInspector(this) { error ->
                if (error != null) {
                    logMessage("Ad Inspector closed with error: ${error.message} (code: ${error.code})", "ERROR")
                } else {
                    logMessage("Ad Inspector closed successfully", "SUCCESS")
                }
            }
        }
    }

    private fun setupHouseAdsSection() {
        binding.btnHouseBanner.setOnClickListener {
            logMessage("Displaying House Banner Ad...", "HOUSE_AD")
            houseAdHelper.showBanner(
                this,
                binding.flBannerContainer,
                object : HouseBannerAdCallback() {
                    override fun onAdLoaded(houseAdItem: HouseAdItem) {
                        logMessage("House Banner loaded: '${houseAdItem.title}'", "SUCCESS")
                    }

                    override fun onAdClicked() {
                        logMessage("House Banner clicked!", "CLICK")
                    }

                    override fun onAdClosed() {
                        logMessage("House Banner closed", "INFO")
                    }
                }
            )
        }

        binding.btnHouseInterstitial.setOnClickListener {
            logMessage("Launching House Interstitial Dialog...", "HOUSE_AD")
            houseAdHelper.showInterstitial(
                this,
                object : HouseInterstitialAdCallback() {
                    override fun onAdImpression() {
                        logMessage("House Interstitial impression recorded", "SUCCESS")
                    }

                    override fun onAdClicked() {
                        logMessage("House Interstitial CTA clicked", "CLICK")
                    }

                    override fun onAdClosed() {
                        logMessage("House Interstitial dismissed", "INFO")
                        refreshFrequencyStatus()
                    }
                }
            )
        }

        binding.btnHouseRewarded.setOnClickListener {
            logMessage("Launching House Rewarded Ad Dialog...", "HOUSE_AD")
            houseAdHelper.showReward(
                this,
                object : HouseRewardAdCallback() {
                    override fun onUserEarnedReward(rewardAmount: Int, rewardType: String) {
                        rewardTokens += rewardAmount
                        logMessage("House Reward Claimed: +$rewardAmount $rewardType", "REWARD")
                        runOnUiThread {
                            binding.tvRewardPoints.text = "Tokens: $rewardTokens"
                        }
                    }

                    override fun onAdClosed() {
                        logMessage("House Rewarded Ad closed", "INFO")
                        refreshFrequencyStatus()
                    }
                }
            )
        }

        binding.btnHouseAppOpen.setOnClickListener {
            logMessage("Triggering House App Open Ad Dialog...", "HOUSE_AD")
            houseAdHelper.showAppOpen(
                this,
                object : HouseAppOpenHelper.OnShowAdCompleteListener {
                    override fun onShowAdComplete() {
                        logMessage("House App Open finished", "INFO")
                        refreshFrequencyStatus()
                    }
                },
                object : HouseAppOpenAdCallback() {
                    override fun onAdClicked() {
                        logMessage("House App Open CTA clicked", "CLICK")
                    }
                }
            )
        }

        binding.btnHouseNativeSmall.setOnClickListener {
            logMessage("Rendering House Native Small View...", "HOUSE_AD")
            houseAdHelper.showNativeSmall(
                this,
                binding.flNativeContainer,
                object : HouseNativeAdCallback() {
                    override fun onAdLoaded(houseAdItem: HouseAdItem) {
                        logMessage("House Native Small populated: ${houseAdItem.title}", "SUCCESS")
                    }

                    override fun onAdClicked() {
                        logMessage("House Native Small clicked", "CLICK")
                    }
                }
            )
        }

        binding.btnHouseNativeFull.setOnClickListener {
            logMessage("Rendering House Native Full View...", "HOUSE_AD")
            houseAdHelper.showNativeFull(
                this,
                binding.flNativeContainer,
                object : HouseNativeAdCallback() {
                    override fun onAdLoaded(houseAdItem: HouseAdItem) {
                        logMessage("House Native Full populated: ${houseAdItem.title}", "SUCCESS")
                    }

                    override fun onAdClicked() {
                        logMessage("House Native Full clicked", "CLICK")
                    }
                }
            )
        }
    }

    private fun setupFrequencyAndAnalyticsSection() {
        binding.btnCheckFrequency.setOnClickListener {
            refreshFrequencyStatus()
        }

        binding.btnLogEvent.setOnClickListener {
            val eventName = "demo_test_event_${System.currentTimeMillis() % 1000}"
            analyticsTracker.logEvent(eventName, mapOf("source" to "demo_activity", "platform" to "android"))
            logMessage("Sent Analytics Event: '$eventName' to Firebase & Adjust", "ANALYTICS")
            Toast.makeText(this, "Analytics event sent", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestCrash.setOnClickListener {
            logMessage("Simulating App Crash to verify CAOC error screen...", "WARN")
            throw RuntimeException("AdLib SDK Test Crash: Verifying Custom Activity On Crash Handler")
        }

        binding.btnClearLogs.setOnClickListener {
            logHistory.clear()
            binding.tvEventLogs.text = "[Console cleared]\n"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun refreshFrequencyStatus() {
        val canShowIv = adFrequencyManager.canShowInterstitial()
        val canShowAoa = adFrequencyManager.canShowAppOpen()

        binding.tvFrequencyStatus.text = """
            • Can Show Interstitial now: $canShowIv
            • Can Show App Open now: $canShowAoa
        """.trimIndent()
    }

    private fun requestConsent() {
        logMessage("Gathering Google UMP Consent...", "CONSENT")
        admobConsentHelper.gatherConsent(this, object : IAdmobConsentCallback {
            override fun consentGatheringComplete(error: FormError?) {
                if (error != null) {
                    logMessage("Consent Gathering Error: ${error.message}", "WARN")
                } else {
                    logMessage("Consent Gathering Complete. Can request ads: ${admobConsentHelper.canRequestAds()}", "SUCCESS")
                }
            }
        })
    }

    private fun updateNetworkStatus() {
        val isNet = admobHelper.isNetwork(this)
        binding.tvNetworkStatus.text = if (isNet) "● Network: Online" else "● Network: Offline"
        binding.tvNetworkStatus.setTextColor(if (isNet) getColor(R.color.accent_green) else getColor(R.color.accent_red))
    }

    private fun logMessage(message: String, tag: String = "INFO") {
        val timestamp = timeFormat.format(Date())
        val logLine = "[$timestamp][$tag] $message\n"
        logHistory.insert(0, logLine)
        runOnUiThread {
            binding.tvEventLogs.text = logHistory.toString()
        }
    }

    override fun onDestroy() {
        currentBannerAdView?.destroy()
        currentNativeAd?.destroy()
        super.onDestroy()
    }
}
