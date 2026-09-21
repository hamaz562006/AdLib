package com.tqhit.adlib.sdk

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.XmlRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.tqhit.adlib.sdk.adjust.AdjustAnalyticsHelper
import com.tqhit.adlib.sdk.ads.admob.AdmobHelper
import com.tqhit.adlib.sdk.ads.admob.AppOpenHelper
import com.tqhit.adlib.sdk.ads.house.HouseAdHelper
import com.tqhit.adlib.sdk.ads.house.HouseAppOpenHelper
import com.tqhit.adlib.sdk.ads.loader.ActivityAdLoader
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.base.AdLibBaseApplication
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import javax.inject.Inject

// TODO: Coarse Location Collection: Google announced that GMA Next-Gen SDK will collect coarse location by default unless disabled via a configuration flag. In version 1.4.0 (installed), this configuration flag is not yet present in the public API (RequestConfiguration / InitializationConfig / MobileAds). Re-check in subsequent SDK updates and configure accordingly.
open class AdLibHiltApplication : AdLibBaseApplication() {
    companion object {
        val diagnosticLog = MutableLiveData<String>()
    }

    protected val APP_AOA_CONFIG_KEY = "APP_AOA"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Inject lateinit var admobHelperLazy: dagger.Lazy<AdmobHelper>
    private val admobHelper: AdmobHelper get() = admobHelperLazy.get()

    @Inject lateinit var appOpenHelperLazy: dagger.Lazy<AppOpenHelper>
    private val appOpenHelper: AppOpenHelper get() = appOpenHelperLazy.get()

    @Inject lateinit var houseAdHelperLazy: dagger.Lazy<HouseAdHelper>
    private val houseAdHelper: HouseAdHelper get() = houseAdHelperLazy.get()

    @Inject lateinit var analyticsTrackerLazy: dagger.Lazy<AnalyticsTracker>
    private val analyticsTracker: AnalyticsTracker get() = analyticsTrackerLazy.get()

    @Inject lateinit var adjustAnalyticsHelper: AdjustAnalyticsHelper

    @Inject lateinit var remoteConfigHelperLazy: dagger.Lazy<FirebaseRemoteConfigHelper>
    private val remoteConfigHelper: FirebaseRemoteConfigHelper get() = remoteConfigHelperLazy.get()

    @Inject lateinit var activityAdLoaderLazy: dagger.Lazy<ActivityAdLoader>
    private val activityAdLoader: ActivityAdLoader get() = activityAdLoaderLazy.get()

    private var isAdMobReady = false
    private var isRemoteConfigReady = false
    private var pendingShowAOA = false

    private val adMobReadyListeners = mutableListOf<() -> Unit>()
    private val remoteConfigReadyListeners = mutableListOf<() -> Unit>()

    override fun onCreateExt() {
        super.onCreateExt()
    }

    fun isAdMobInitReady(): Boolean = isAdMobReady
    fun isRemoteConfigInitReady(): Boolean = isRemoteConfigReady

    fun addReadyListener(onAdMobReady: (() -> Unit)?, onRemoteConfigReady: (() -> Unit)?) {
        if (onAdMobReady != null) {
            if (isAdMobReady) {
                onAdMobReady()
            } else {
                adMobReadyListeners.add(onAdMobReady)
            }
        }
        if (onRemoteConfigReady != null) {
            if (isRemoteConfigReady) {
                onRemoteConfigReady()
            } else {
                remoteConfigReadyListeners.add(onRemoteConfigReady)
            }
        }
    }

    /**
     * @param testDeviceIds Hashed device IDs to register as AdMob test devices (physical devices
     * are NOT auto-registered the way emulators are — see Constant.TEST_DEVICE_IDS for how to
     * obtain a real device's ID from Logcat).
     */
    fun initAll(@XmlRes defaultConfig: Int, adjustToken: String? = null, testDeviceIds: List<String>? = null) {
        initRemoteConfig(defaultConfig) { success ->
            mainHandler.post {
                isRemoteConfigReady = true
                val listeners = remoteConfigReadyListeners.toList()
                remoteConfigReadyListeners.clear()
                listeners.forEach { it() }

                initAdmobAndAOA(testDeviceIds)
            }
        }
        adjustToken?.let { initTracker(it) }
    }

    fun initRemoteConfig(@XmlRes defaultConfig: Int,
                         onFetchComplete: ((Boolean) -> Unit)) {
        remoteConfigHelper.fetchAndActivate(onFetchComplete, defaultConfig)
    }

    fun initTracker(token: String) {
        adjustAnalyticsHelper.initAdjust(token)
    }

    fun initAdmobAndAOA(testDeviceIds: List<String>? = null) {
        admobHelper.initAdmob({
            mainHandler.post {
                isAdMobReady = true

                val listeners = adMobReadyListeners.toList()
                adMobReadyListeners.clear()
                listeners.forEach { it() }

                initAOA()

                // Do NOT resolve a pending showAOA() request here yet: SDK init finishing only
                // means MobileAds.initialize() completed, not that the App Open ad content itself
                // has finished loading over the network. Wait for appOpenHelper.adLoaded to emit
                // (success or failure) before actually attempting to show anything.
                if (pendingShowAOA) {
                    waitForAOALoadThenResolvePending()
                }
            }
        }, testDeviceIds)
    }

    private fun waitForAOALoadThenResolvePending() {
        mainHandler.post {
            var observer: Observer<Boolean>? = null
            observer = Observer<Boolean> {
                observer?.let { appOpenHelper.adLoaded.removeObserver(it) }
                if (pendingShowAOA) {
                    pendingShowAOA = false
                    showAOA()
                }
            }
            appOpenHelper.adLoaded.observeForever(observer)
        }
    }

    fun initAOA() {
        val adConfig = activityAdLoader.getAdConfig(APP_AOA_CONFIG_KEY)
        val useHouseAd = adConfig?.useHouseAd ?: false
        val customId = adConfig?.customId

        if (!useHouseAd) {
            val adUnitId = if (!customId.isNullOrBlank()) {
                customId
            } else {
                remoteConfigHelper.getString(ActivityAdLoader.RC_AOA_AD_UNIT_ID)
            }
            admobHelper.setAppOpenAdUnitId(adUnitId)
            admobHelper.loadAOA(applicationContext)
        }
    }

    override fun showAOA() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showAOA() }
            return
        }

        if (!isAdMobReady) {
            pendingShowAOA = true
            return
        }

        super.showAOA()

        if (currentActivity == null) return

        val adConfig = activityAdLoader.getAdConfig(APP_AOA_CONFIG_KEY)
        val useHouseAd = adConfig?.useHouseAd ?: false

        if (useHouseAd) {
            houseAdHelper.showAppOpen(
                currentActivity!!,
                object : HouseAppOpenHelper.OnShowAdCompleteListener {
                    override fun onShowAdComplete() {}
                }
            )
        } else {
            admobHelper.showAOA(
                currentActivity!!,
                object : AppOpenHelper.OnShowAdCompleteListener {
                    override fun onShowAdComplete() {
                        diagnosticLog.postValue("showAOA: onShowAdComplete")
                    }

                    override fun onHouseAdShown(reason: String) {
                        diagnosticLog.postValue("showAOA: onHouseAdShown, reason=$reason")
                    }

                    override fun onDiagnosticInfo(message: String) {
                        diagnosticLog.postValue("showAOA: $message")
                    }
                }
            )
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        super.onActivityCreated(activity, savedInstanceState)

        analyticsTracker.logEvent("view_${activity.javaClass.simpleName.lowercase()}")

        if (activity is FragmentActivity) {
            val fm: FragmentManager = activity.supportFragmentManager
            fm.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentCreated(fm: FragmentManager, f: androidx.fragment.app.Fragment, savedInstanceState: Bundle?) {
                    super.onFragmentCreated(fm, f, savedInstanceState)
                    analyticsTracker.logEvent("view_${f.javaClass.simpleName.lowercase()}")
                }

                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    super.onFragmentResumed(fm, f)
                    activityAdLoader.onActivityResumed(activity, f.javaClass.simpleName)
                }
            }, true)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)

        activityAdLoader.onActivityResumed(activity)
    }
}
