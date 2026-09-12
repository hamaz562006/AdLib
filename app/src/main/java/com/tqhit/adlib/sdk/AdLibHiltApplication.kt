package com.tqhit.adlib.sdk

import android.app.Activity
import android.os.Bundle
import androidx.annotation.XmlRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.tqhit.adlib.sdk.adjust.AdjustAnalyticsHelper
import com.tqhit.adlib.sdk.ads.admob.AdmobHelper
import com.tqhit.adlib.sdk.ads.admob.AppOpenHelper
import com.tqhit.adlib.sdk.ads.house.HouseAdHelper
import com.tqhit.adlib.sdk.ads.house.HouseAppOpenHelper
import com.tqhit.adlib.sdk.ads.loader.ActivityAdLoader
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.base.AdLibBaseApplication
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// TODO: Coarse Location Collection: Google announced that GMA Next-Gen SDK will collect coarse location by default unless disabled via a configuration flag. In version 1.4.0 (installed), this configuration flag is not yet present in the public API (RequestConfiguration / InitializationConfig / MobileAds). Re-check in subsequent SDK updates and configure accordingly.
@HiltAndroidApp
open class AdLibHiltApplication : AdLibBaseApplication() {
    protected val APP_AOA_CONFIG_KEY = "APP_AOA"

    @Inject lateinit var admobHelper: AdmobHelper
    @Inject lateinit var houseAdHelper: HouseAdHelper
    @Inject lateinit var analyticsTracker: AnalyticsTracker
    @Inject lateinit var adjustAnalyticsHelper: AdjustAnalyticsHelper
    @Inject lateinit var remoteConfigHelper: FirebaseRemoteConfigHelper
    @Inject lateinit var activityAdLoader: ActivityAdLoader

    override fun onCreateExt() {
        super.onCreateExt()
    }

    fun initRemoteConfig(@XmlRes defaultConfig: Int,
                         onFetchComplete: ((Boolean) -> Unit)) {
        remoteConfigHelper.fetchAndActivate(onFetchComplete, defaultConfig)
    }

    fun initTracker(token: String) {
        adjustAnalyticsHelper.initAdjust(token)
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
                    override fun onShowAdComplete() {}
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
