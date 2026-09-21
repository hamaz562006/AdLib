package com.tqhit.adlib.sdk.ads.admob

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.tqhit.adlib.sdk.ads.AdFrequencyManager
import com.tqhit.adlib.sdk.ads.house.HouseAppOpenHelper
import com.tqhit.adlib.sdk.analytics.AnalyticsTracker
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.firebase.FirebaseRemoteConfigHelper
import com.tqhit.adlib.sdk.ui.dialog.LoadingAdsDialog
import com.tqhit.adlib.sdk.utils.Constant
import com.tqhit.adlib.sdk.utils.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenHelper @Inject constructor(
    private val admobConsentHelper: AdmobConsentHelper,
    private val analyticsTracker: AnalyticsTracker,
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val preferencesHelper: PreferencesHelper,
    private val adFrequencyManager: AdFrequencyManager,
    private val adMobRateLimiter: AdmobRateLimiter,
    private val houseAppOpenHelper: HouseAppOpenHelper
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun isAdEnabled(): Boolean {
        if (preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)) return false
        if (Constant.DEBUG_MODE) return true
        return remoteConfigHelper.getBoolean("aoa_enable")
    }

    private fun isHouseAdsEnabled() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_ENABLED)

    private fun isHouseAutoFallback() =
        remoteConfigHelper.getBoolean(Constant.RC_HOUSE_ADS_AUTO_FALLBACK)

    private fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private var loadTime: Long = 0
    private var adUnitId = ""
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false

    val adLoaded = MutableLiveData<Boolean>()

    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
        fun onHouseAdShown(reason: String) {}
        fun onDiagnosticInfo(message: String) {}
    }

    fun setAdUnitId(adUnitId: String) {
        this.adUnitId = adUnitId
    }

    fun loadAd(context: Context, onComplete: ((Boolean, String?) -> Unit)? = null) {
        if (!NetworkUtils.isNetworkAvailable(context)) {
            adLoaded.postValue(false)
            onComplete?.invoke(false, "Network unavailable")
            return
        }

        if (!isAdEnabled()) {
            adLoaded.postValue(false)
            onComplete?.invoke(false, "App Open disabled")
            return
        }

        if (!admobConsentHelper.canRequestAds()) {
            android.util.Log.d("AppOpenHelper", "AOA load skipped: consent not ready yet")
            adLoaded.postValue(false)
            onComplete?.invoke(false, "Consent not ready")
            return
        }

        if (isAdAvailable()) {
            adLoaded.postValue(true)
            onComplete?.invoke(true, null)
            return
        }

        val targetAdUnitId = if (Constant.DEBUG_MODE || adUnitId.isBlank()) Constant.ADMOB_AOA_AD_UNIT_ID else adUnitId
        if (!adMobRateLimiter.canRequest(targetAdUnitId)) {
            android.util.Log.w("AppOpenHelper", "AppOpen adUnitId $targetAdUnitId is in NO_FILL cooldown")
            adLoaded.postValue(false)
            analyticsTracker.logEvent("aj_app_open_load_fail_cooldown")
            onComplete?.invoke(false, "In NO_FILL cooldown")
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder(targetAdUnitId).build()
        analyticsTracker.logEvent("aj_app_open_load")
        AppOpenAd.load(
            request,
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    analyticsTracker.logEvent("aj_app_open_load_success")
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    adLoaded.postValue(true)
                    android.util.Log.d("ADLIB_DIAGNOSTIC", "AOA load SUCCESS, adUnitId=$targetAdUnitId")
                    onComplete?.invoke(true, null)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if (loadAdError.code == LoadAdError.ErrorCode.NO_FILL) {
                        adMobRateLimiter.recordNoFill(targetAdUnitId)
                    }
                    analyticsTracker.logEvent("aj_app_open_load_fail")
                    isLoadingAd = false
                    adLoaded.postValue(false)
                    val errorMsg = loadAdError.message.ifBlank { loadAdError.code.toString() }
                    android.util.Log.d("ADLIB_DIAGNOSTIC", "AOA load FAILED, adUnitId=$targetAdUnitId, error=$errorMsg")
                    onComplete?.invoke(false, errorMsg)
                }
            }
        )
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    /** Check if ad exists and can be shown.  */
    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    /** Shows the ad if one isn't already showing.  */
    fun showAdIfAvailable(activity: Activity, adCallback: OnShowAdCompleteListener) {
        if (!isAdEnabled() || isShowingAd) {
            runOnUiThread { adCallback.onShowAdComplete() }
            return
        }

        if (!NetworkUtils.isNetworkAvailable(activity)) {
            if (isHouseAdsEnabled()) {
                // App Open race condition fix: retry after 1.2s delay before falling back
                CoroutineScope(Dispatchers.Main).launch {
                    delay(1200)
                    if (activity.isFinishing || activity.isDestroyed) return@launch
                    if (!NetworkUtils.isNetworkAvailable(activity)) {
                        runOnUiThread {
                            adCallback.onHouseAdShown("Network unavailable (after retry)")
                            houseAppOpenHelper.showHouseAppOpen(
                                activity,
                                object : HouseAppOpenHelper.OnShowAdCompleteListener {
                                    override fun onShowAdComplete() {
                                        adCallback.onShowAdComplete()
                                    }
                                },
                                ignoreFrequencyCheck = true
                            )
                        }
                    } else {
                        // Network became available, proceed with available check
                        checkAndShowAppOpenAd(activity, adCallback)
                    }
                }
            } else {
                runOnUiThread { adCallback.onShowAdComplete() }
            }
            return
        }

        checkAndShowAppOpenAd(activity, adCallback)
    }

    private fun checkAndShowAppOpenAd(activity: Activity, adCallback: OnShowAdCompleteListener) {
        if (activity.isFinishing || activity.isDestroyed) return

        if (!isAdAvailable()) {
            val loadingAdsDialog = LoadingAdsDialog(activity)
            if (!activity.isFinishing && !activity.isDestroyed) {
                loadingAdsDialog.show()
            }
            loadAd(activity) { success, errorMsg ->
                runOnUiThread {
                    if (loadingAdsDialog.isShowing) {
                        loadingAdsDialog.dismiss()
                    }
                    if (success && isAdAvailable() && !activity.isFinishing && !activity.isDestroyed) {
                        checkAndShowAppOpenAd(activity, adCallback)
                    } else {
                        if (isHouseAutoFallback() && !activity.isFinishing && !activity.isDestroyed) {
                            val reason = errorMsg ?: "No AdMob ad available"
                            adCallback.onDiagnosticInfo("AOA load failed, falling back to House: $reason")
                            adCallback.onHouseAdShown(reason)
                            houseAppOpenHelper.showHouseAppOpen(
                                activity,
                                object : HouseAppOpenHelper.OnShowAdCompleteListener {
                                    override fun onShowAdComplete() {
                                        adCallback.onShowAdComplete()
                                    }
                                },
                                ignoreFrequencyCheck = true
                            )
                        } else {
                            adCallback.onShowAdComplete()
                        }
                    }
                }
            }
            return
        }

        // Frequency gating via AdFrequencyManager: fallback to House App Open if blocked
        if (!adFrequencyManager.canShowAppOpen()) {
            if (isHouseAdsEnabled()) {
                runOnUiThread {
                    adCallback.onHouseAdShown("Frequency capped")
                    houseAppOpenHelper.showHouseAppOpen(
                        activity,
                        object : HouseAppOpenHelper.OnShowAdCompleteListener {
                            override fun onShowAdComplete() {
                                adCallback.onShowAdComplete()
                            }
                        },
                        ignoreFrequencyCheck = true
                    )
                }
            } else {
                runOnUiThread { adCallback.onShowAdComplete() }
            }
            return
        }

        analyticsTracker.logEvent("aj_app_open_show")
        val currentAd = appOpenAd ?: run {
            runOnUiThread { adCallback.onShowAdComplete() }
            return
        }

        currentAd.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                runOnUiThread {
                    analyticsTracker.logEvent("aj_app_open_close")
                    appOpenAd = null
                    isShowingAd = false
                    adLoaded.postValue(false)
                    adFrequencyManager.recordAppOpenShown()
                    adCallback.onShowAdComplete()
                    loadAd(activity)
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                runOnUiThread {
                    analyticsTracker.logEvent("aj_app_open_show_fail")
                    appOpenAd = null
                    isShowingAd = false
                    adLoaded.postValue(false)
                    adFrequencyManager.recordAppOpenShown()
                    if (isHouseAutoFallback() && !activity.isFinishing && !activity.isDestroyed) {
                        val reason = error.message.ifBlank { error.code.toString() }
                        adCallback.onHouseAdShown(reason)
                        houseAppOpenHelper.showHouseAppOpen(
                            activity,
                            object : HouseAppOpenHelper.OnShowAdCompleteListener {
                                override fun onShowAdComplete() {
                                    adCallback.onShowAdComplete()
                                }
                            },
                            ignoreFrequencyCheck = true
                        )
                    } else {
                        adCallback.onShowAdComplete()
                    }
                    loadAd(activity)
                }
            }

            override fun onAdShowedFullScreenContent() {
                runOnUiThread {
                    analyticsTracker.logEvent("aj_app_open_show_success")
                }
            }

            override fun onAdClicked() {
                runOnUiThread {
                    analyticsTracker.logEvent("aj_app_open_click")
                }
            }

            override fun onAdImpression() {
                // impression logged automatically
            }

            override fun onAdPaid(adValue: AdValue) {
                analyticsTracker.trackAdMobRevenueEvent(
                    adValue,
                    currentAd.adUnitId,
                    currentAd.getResponseInfo().loadedAdSourceResponseInfo?.name ?: "AdMob",
                    "AOA"
                )
            }
        }
        isShowingAd = true
        currentAd.show(activity)
    }
}
