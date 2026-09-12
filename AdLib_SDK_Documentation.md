# AdLib SDK Documentation for LLM Context

## Overview

AdLib is a comprehensive Android ad management SDK built on **AdMob (GMA Next-Gen SDK)**. It provides automatic ad preloading, a fully remote-controllable **House Ads** fallback system, granular frequency capping with persistence, Adjust attribution, and per-placement configuration via Firebase Remote Config.

> AppLovin MAX support has been fully removed. Every place that previously chose between AdMob and MAX (`useMax`) now chooses between AdMob and House Ads (`useHouseAd`).

## Architecture

### Core Components

1. **ActivityAdLoader** - Central ad preloading system with LiveData integration; decides AdMob vs. House Ad per placement and applies offline/frequency fallback before ever preloading
2. **AdmobHelper** - AdMob ad operations facade (init, Ad Inspector, network check)
3. **Per-format AdMob Helpers** - `InterstitialHelper`, `RewardHelper`, `BannerHelper`, `AdaptiveBannerHelper`, `NativeHelper`, `AppOpenHelper` — each is an `@Singleton` Hilt class that owns load/show logic, House Ads fallback wiring, and rate limiting for its format
4. **HouseAdHelper / HouseAdManager** - House Ads facade and content engine (Remote Config JSON parsing, round-robin selection, click handling)
5. **Per-format House Helpers** - `HouseInterstitialHelper`, `HouseRewardHelper`, `HouseBannerHelper`, `HouseNativeHelper`, `HouseAppOpenHelper`
6. **AdFrequencyManager** - granular, persisted frequency capping
7. **AdmobRateLimiter** - short cooldown after an AdMob `NO_FILL` to avoid repeat requests
8. **NetworkUtils** - connectivity check (`isNetworkAvailable`) and real ad-server reachability check at the HTTPS layer (`isAdServerReachable`)
9. **AnalyticsTracker** - revenue and impression tracking (`aj_*` event names, forwarded via Adjust/Firebase)
10. **AdjustAnalyticsHelper** - direct Adjust SDK integration
11. **FirebaseRemoteConfigHelper** - Remote Config fetch/activate wrapper
12. **PreferencesHelper** - generic `SharedPreferences` + Gson wrapper, injectable via Hilt
13. **AdmobConsentHelper** - UMP/GDPR consent flow
14. **AdLibHiltApplication / AdLibBaseApplication** - base Application class; wires App Open Ads, activity/fragment preload hooks, and the crash-recovery screen

### Ad Types Supported

- **Interstitial Ads** - full-screen ads between content
- **Rewarded Ads** - user-initiated ads with rewards
- **Banner Ads** - standard, collapsible, and adaptive banners
- **Native Ads** - custom-styled content ads (small and full layouts)
- **App Open Ads** - launch/foreground ads

## Remote Config Structure

### Activity/Fragment Preload Configuration
```
Key Pattern: PRELOAD_{ActivityName} or PRELOAD_{ActivityName}_{FragmentName}
Value: "MAIN_IV,MAIN_RV,MAIN_BN,MAIN_NT,MAIN_AOA"
```

### Per-Ad JSON Configuration
```json
{
  "useHouseAd": true,
  "customId": "optional_id",
  "autoFallback": true
}
```
- `useHouseAd` - if `true`, this placement always shows a House Ad and AdMob is never attempted
- `customId` - optional override of the default ad unit ID for this placement
- `autoFallback` - if `true` (and `house_ads_enabled` is also `true`), automatically fall back to a House Ad if AdMob is offline, fails to load, or is frequency-capped

### App Open Ad Configuration
```
Key: APP_AOA
Value: {"useHouseAd": true/false, "customId": "optional_custom_aoa_id"}
```

### Global / Format Switches
- `bn_enable`, `iv_enable`, `rv_enable`, `nt_enable`, `aoa_enable` — enable/disable each AdMob format
- `house_ads_enabled` — global House Ads master switch
- `house_ads_auto_fallback` — global default for automatic fallback (per-placement `autoFallback` can still be used)
- `house_ads_json` — the House Ads content array (see below)

### Fallback / Default Ad Unit ID Keys
`iv_ad_unit_id`, `rv_ad_unit_id`, `nt_ad_unit_id`, `bn_ad_unit_id`, `c_bn_ad_unit_id`, `RC_AOA_AD_UNIT_ID`

### Frequency Capping Keys
- `RC_IV_SHOW_FREQUENCY` (seconds) — cooldown between Interstitial impressions
- `RC_AOA_SHOW_FREQUENCY` (seconds) — cooldown between App Open impressions
- `RC_IV_DELAY_SHOW_AFTER_RV` (seconds) — delay before showing an Interstitial right after a Rewarded ad
- `RC_ENABLE_ACTIVITY_AD_LOADER` (boolean) — master switch for the whole `ActivityAdLoader` preloading system

### House Ads Content
```json
[
  {
    "id": "promo_app_x",
    "title": "Explore Pro Tools",
    "description": "Upgrade your experience with our high-performance utility suite.",
    "ctaText": "Install Now",
    "iconUrl": "https://.../icon.png",
    "mediaUrl": "https://.../banner.png",
    "targetUrl": "https://play.google.com/store/apps/details?id=...",
    "rating": 4.9,
    "enabled": true
  }
]
```
If this key is missing, empty, or fails to parse, `HouseAdManager` falls back to a small built-in default list so a slot is never left empty. Selection among enabled entries is round-robin. `iconUrl`/`mediaUrl` are loaded via Glide with a local drawable fallback (`iconResName`/`mediaResName`, resolved via `Resources.getIdentifier`). Clicks are debounced (1000ms) and open `targetUrl` via a `market://` intent first, falling back to a browser `Intent.ACTION_VIEW`.

## Key Classes and Methods

### AdLibHiltApplication

```kotlin
open class AdLibHiltApplication : AdLibBaseApplication() {
    @Inject lateinit var admobHelper: AdmobHelper
    @Inject lateinit var houseAdHelper: HouseAdHelper
    @Inject lateinit var analyticsTracker: AnalyticsTracker
    @Inject lateinit var adjustAnalyticsHelper: AdjustAnalyticsHelper
    @Inject lateinit var remoteConfigHelper: FirebaseRemoteConfigHelper
    @Inject lateinit var activityAdLoader: ActivityAdLoader

    fun initRemoteConfig(@XmlRes defaultConfig: Int, onFetchComplete: ((Boolean) -> Unit))
    fun initTracker(token: String) // Adjust
    fun initAOA() // reads APP_AOA config, loads AdMob or preps House App Open
    override fun showAOA() // routes to AdMob or House based on APP_AOA's useHouseAd
}
```

### AdLibBaseApplication

```kotlin
abstract class AdLibBaseApplication : Application(), Application.ActivityLifecycleCallbacks, LifecycleObserver {
    open fun isDebugMode(): Boolean = Constant.DEBUG_MODE
    open fun setupCAOC() // installs customactivityoncrash when isDebugMode() is true
    open fun customErrorActivity(): Class<out Activity> = AdLibCustomCrashActivity::class.java
    protected open fun showAOA() {}
    // Automatically drives ActivityAdLoader.onActivityResumed() and Fragment preload hooks
}
```

### ActivityAdLoader

```kotlin
@Singleton
class ActivityAdLoader @Inject constructor(
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val admobHelper: AdmobHelper,
    private val houseAdHelper: HouseAdHelper,
    private val adFrequencyManager: AdFrequencyManager
) {
    data class AdConfig(
        val useHouseAd: Boolean,
        val customId: String? = null,
        val autoFallback: Boolean = true
    )

    fun onActivityResumed(activity: Activity, fragmentName: String? = null)
    fun getAdLiveData(key: String): MutableLiveData<Any>?
    fun adShownAndShouldBeRemoved(key: String)
    fun getAdConfig(adKey: String): AdConfig?

    // preloadAdForKey routes to House immediately when:
    //   adConfig.useHouseAd == true, OR
    //   the ad key is a frequency-capped format (IV/AOA) and the cap currently blocks it, OR
    //   AdMob is not usable right now (NetworkUtils.isNetworkAvailable() == false)
    // Otherwise it loads from AdMob and falls back to House on load failure if autoFallback is true.
}
```

### AdmobHelper

```kotlin
@Singleton
class AdmobHelper @Inject constructor(...) {
    fun initAdmob(onComplete: () -> Unit, testDeviceIds: List<String>? = null) // runs on Dispatchers.IO
    fun isNetwork(context: Context?): Boolean // legacy connectivity check (superseded by NetworkUtils)
    fun launchAdInspector(context: Context, onComplete: ((error) -> Unit)? = null)
    fun setLocationCollectionEnabled(enabled: Boolean) // placeholder, see "Known limitations"

    // Interstitial / Rewarded / Banner / Native / App Open loading is delegated to
    // the per-format Helper classes below, not implemented directly on AdmobHelper.
}
```

### Per-format AdMob Helper pattern (e.g. `InterstitialHelper`)

```kotlin
@Singleton
class InterstitialHelper @Inject constructor(
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val houseInterstitialHelper: HouseInterstitialHelper,
    private val adFrequencyManager: AdFrequencyManager,
    private val adMobRateLimiter: AdmobRateLimiter
) {
    fun loadInterstitial(context: Context, adUnitId: String, timeout: Int?, listener: InterstitialAdCallback)

    fun showInterstitial(activity: Activity, interstitialAd: InterstitialAd, adCallback: InterstitialAdCallback?)
    fun showInterstitial(activity: Activity, adUnitId: String, interstitialAd: InterstitialAd?, timeoutMs: Int?, adCallback: InterstitialAdCallback?)
    // showInterstitial(activity, adUnitId, ...) flow:
    //   1. If NetworkUtils.isAdServerReachable() fails -> onHouseAdShown("Network unavailable") -> House (ignoreFrequencyCheck = true)
    //   2. Else if adFrequencyManager.canShowInterstitial() == false -> onHouseAdShown("Frequency capped") -> House (ignoreFrequencyCheck = true)
    //   3. Else try AdMob load; on success show it; on onAdFailedToLoad, if house_ads_auto_fallback ->
    //      onHouseAdShown(adError.message ?: adError.code.toString()) -> House (ignoreFrequencyCheck = true)
    //   4. adMobRateLimiter.canRequest(adUnitId) gates step 3 to avoid hammering AdMob right after a NO_FILL
}
```
`RewardHelper`, `BannerHelper`, `AdaptiveBannerHelper`, `NativeHelper`, and `AppOpenHelper` follow the same shape (minus frequency capping for Banner/Native/Rewarded, which don't have a frequency rule). `AppOpenHelper`'s initial network check retries once after a 1.2s delay before falling back, to avoid a false negative while a VPN/network connection is still coming up right as the app launches.

### HouseAdManager

```kotlin
@Singleton
class HouseAdManager @Inject constructor(
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val analyticsTracker: AnalyticsTracker
) {
    fun isHouseAdsEnabled(): Boolean
    fun getNextAd(): HouseAdItem // round-robin among enabled ads, falls back to built-in defaults if none configured
    fun recordClick(context: Context, adItem: HouseAdItem) // debounced (1000ms), market:// then browser fallback
    fun recordImpression(adItem: HouseAdItem)
}
```

### Callback pattern — `onHouseAdShown(reason: String)`

Every AdMob callback class (`InterstitialAdCallback`, `RewardAdCallback`, `BannerAdCallback`, `NativeAdCallback`, and `AppOpenHelper.OnShowAdCompleteListener`) exposes an additional, optional method:

```kotlin
open fun onHouseAdShown(reason: String) {}
```

`reason` is one of:
- the real AdMob `LoadAdError.message` (or `.code` if no message), when AdMob genuinely failed to load
- `"Network unavailable"` / `"Network unavailable (after retry)"` (App Open only), when the pre-flight reachability check failed
- `"Frequency capped"`, when a frequency rule blocked AdMob

This gives host apps (and analytics) visibility into *why* a House Ad appeared, instead of just that one did.

### AdFrequencyManager

```kotlin
@Singleton
class AdFrequencyManager @Inject constructor(
    private val remoteConfigHelper: FirebaseRemoteConfigHelper,
    private val preferencesHelper: PreferencesHelper
) {
    fun updateConfig(/* pulls RC_IV_SHOW_FREQUENCY, RC_AOA_SHOW_FREQUENCY, RC_IV_DELAY_SHOW_AFTER_RV */)
    fun canShowInterstitial(placementTag: String? = null): Boolean // checks cooldown + per-session cap + delay-after-rewarded
    fun canShowAppOpen(): Boolean
    fun canShowRewarded(): Boolean = true // Rewarded has no frequency rule by design (user-initiated)
    fun recordInterstitialShown(placementTag: String? = null)
    fun recordRewardedShown()
    fun recordAppOpenShown()
    // Last-shown timestamps (global + per-placement) are persisted through PreferencesHelper
    // so a killed/restarted process doesn't reset the cooldown. Per-session impression counts
    // are intentionally NOT persisted — they reset every app launch.
}
```

### AdmobRateLimiter

```kotlin
@Singleton
class AdmobRateLimiter @Inject constructor() {
    fun recordNoFill(adUnitId: String)
    fun canRequest(adUnitId: String): Boolean // false while within the post-NO_FILL cooldown for that ad unit
}
```

### NetworkUtils

```kotlin
object NetworkUtils {
    fun isNetworkAvailable(context: Context?): Boolean // ConnectivityManager-based connectivity check
    suspend fun isAdServerReachable(timeoutMs: Int = 3000): Boolean
    // Performs a lightweight HTTPS HEAD request (not a raw TCP connect) to a real Google
    // ad-serving host, because some network-level filtering (SNI/DPI-based) lets a raw TCP
    // handshake succeed while still blocking the actual HTTPS request — a socket-only check
    // would report a false positive there.
}
```

### PreferencesHelper

```kotlin
class PreferencesHelper @Inject constructor(...) {
    fun saveString/getString, saveInt/getInt, saveBoolean/getBoolean, saveFloat/getFloat, saveLong/getLong
    fun saveObject(key: String, value: Any) // Gson-serialized
    fun <T> getObject(key: String, clazz: Class<T>): T?
    fun clearPreferences()
}
```

## Usage Patterns

### 1. Application Setup

```kotlin
class MyApplication : AdLibHiltApplication() {
    override fun onCreateExt() {
        super.onCreateExt()

        initRemoteConfig(R.xml.remote_config_defaults) { success ->
            initAOA() // after Remote Config is ready
        }

        initTracker("YOUR_ADJUST_TOKEN")
    }
}
```

### 2. Activity Integration

```kotlin
class MainActivity : AppCompatActivity() {
    @Inject lateinit var activityAdLoader: ActivityAdLoader
    @Inject lateinit var interstitialHelper: InterstitialHelper

    override fun onResume() {
        super.onResume()
        activityAdLoader.onActivityResumed(this) // preloads per Remote Config
    }

    private fun showInterstitial() {
        interstitialHelper.showInterstitial(
            this, "iv_ad_unit_id", null, 15000,
            object : InterstitialAdCallback() {
                override fun onAdClosed() { /* ... */ }
                override fun onHouseAdShown(reason: String) {
                    Log.d("MainActivity", "Fell back to House Ad: $reason")
                }
            }
        )
    }
}
```

### 3. Fragment-Specific Ads
```kotlin
override fun onResume() {
    super.onResume()
    activityAdLoader.onActivityResumed(requireActivity(), "HomeFragment")
}
```

## Ad Loading Flow

1. **Activity/Fragment resumed** → `ActivityAdLoader.onActivityResumed()`
2. **Read Remote Config** → get preload keys for this screen (`PRELOAD_*`)
3. **Per key, parse `AdConfig`** → decide AdMob vs. House (honoring `useHouseAd`, frequency capping, and network reachability)
4. **Load** → AdMob via the relevant Helper, or House via `HouseAdManager`
5. **Store in LiveData** → observed by the Activity/Fragment when it's time to show

## App Open Ad Flow

1. **Remote Config ready** → `initAOA()`
2. **Read `APP_AOA`** → decide AdMob vs. House
3. **Load** accordingly
4. **On foreground** → `AdLibBaseApplication.showAOA()` → `AdLibHiltApplication.showAOA()` routes to `admobHelper.showAOA()` or `houseAdHelper.showAppOpen()`

## Revenue & Analytics Events

**Per-format lifecycle events** (`aj_` prefix, one set per format — interstitial, reward, banner, native, app_open):
`aj_*_load`, `aj_*_load_success`, `aj_*_load_fail`, `aj_*_show_success`, `aj_*_show_fail`, `aj_*_close`, `aj_*_click`

```kotlin
// AdMob revenue tracking
analyticsTracker.trackAdMobRevenueEvent(adValue, adUnitId, source, adType)
```

## Dependencies

```kotlin
implementation(libs.ads.mobile.sdk) // com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk
implementation(libs.hilt.android)
kapt(libs.hilt.compiler)
implementation(libs.firebase.analytics)
implementation(libs.firebase.config)
implementation(libs.adjust.android)
implementation(libs.glide) // House Ads remote icon/media loading
implementation(libs.lottie) // loading dialogs
implementation(libs.customactivityoncrash)
```

```kotlin
// app/build.gradle.kts — prevents the legacy AdMob SDK from being pulled in transitively
configurations.all {
    exclude(group = "com.google.android.gms", module = "play-services-ads")
    exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
}
```

## Best Practices

1. Always check for null when reading `getAdLiveData()`
2. Handle `onHouseAdShown(reason)` in every callback if you want visibility into fallback reasons
3. Call `adShownAndShouldBeRemoved()` for one-time-use ads
4. Configure `house_ads_json` before relying on House Ads in production — the built-in defaults exist only as a safety net
5. Initialize AOA after Remote Config is ready
6. Don't wrap ad-callback UI code assuming main-thread execution — see "Known limitations"

## Known Limitations

- **Coarse location collection**: Google has announced a future GMA SDK release will collect coarse location (only from apps that already hold location permission) unless disabled via a configuration flag. As of SDK `1.4.0`, this flag doesn't exist yet in the public API. `AdmobHelper.setLocationCollectionEnabled(Boolean)` is a placeholder for when it ships — **re-check on every SDK upgrade**.
- **Thread safety**: GMA Next-Gen SDK callbacks are not guaranteed to run on the main thread. All internal UI-touching code (loading dialogs, view updates) is wrapped in `runOnUiThread`/`Handler(Looper.getMainLooper())`. Any new UI code you add inside an ad callback must do the same.
- **Demo/library separation**: the ad SDK code and the demo app currently live in the same `app/` Gradle module; a library/demo module split is planned.

This documentation provides comprehensive context for implementing AdLib in any Android project, or for an LLM assisting with such integration.
