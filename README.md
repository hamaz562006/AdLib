# AdLib

A unified Android ad management SDK built on **AdMob (GMA Next-Gen SDK)** with a built-in, fully remote-controllable **House Ads** system, smart frequency capping, and Adjust attribution — all wired together with Hilt dependency injection.

## Project structure

```
AdLib/
├── adlib/   ← the library module (com.android.library, namespace com.tqhit.adlib)
└── demo/    ← a thin demo app (com.android.application) exercising every feature of :adlib
```

The demo app depends on the library exactly the way a consuming app would (`implementation(project(":adlib"))`), so it doubles as a living usage reference for every format and fallback scenario described below.

## Highlights

- **AdMob (Next-Gen SDK)**: Banner, Collapsible Banner, Adaptive Banner, Interstitial, Rewarded, Native (small & full layouts), App Open
- **House Ads**: a self-serve fallback ad system whose entire content (title, description, CTA, icon, image, click URL, rating) is defined and updated remotely via Firebase Remote Config JSON — no app release required
- **Automatic AdMob → House Ads fallback**, triggered independently by:
  - No network / ad servers unreachable (checked at the real HTTPS layer, not just a raw TCP probe — see [Network reachability](#network-reachability))
  - AdMob load failure (`NO_FILL` and other errors)
  - Frequency capping (Interstitial & App Open only)
- **Granular, persisted frequency capping**: per-placement cooldowns, per-session caps, a configurable delay after showing a Rewarded ad — all backed by `SharedPreferences` so state survives process death
- **AdMob NO_FILL rate limiter**: avoids hammering AdMob with repeat requests right after a `NO_FILL`
- **Remote Config driven everything**: enable/disable each format, ad unit IDs, House Ads content, frequency windows, and per-placement AdMob/House routing — all without shipping a new build
- **`ActivityAdLoader`**: automatic per-Activity/Fragment ad preloading driven by Remote Config
- **Adjust attribution & event tracking** (direct SDK dependency)
- **Granular ad lifecycle visibility**: every `*AdCallback` exposes `onHouseAdShown(reason: String)`, where `reason` is the *real* AdMob error message (or `"Frequency capped"` / `"Network unavailable"`) that triggered the fallback — no more guessing why House Ads appeared
- **UMP/GDPR consent** helper
- **Ad Inspector** launcher for on-device debugging
- **Built-in crash recovery screen** (via `customactivityoncrash`) instead of a raw app crash

## Requirements

- `minSdk 24`, `compileSdk 35`
- Kotlin + Hilt

## Installation

Add the `:adlib` module as a project dependency (e.g. as a git submodule, or once published via the included `jitpack.yml`, as a Maven coordinate from JitPack):

```kotlin
// your app's build.gradle.kts
dependencies {
    implementation(project(":adlib"))
    // or, once published:
    // implementation("com.github.hamaz562006:AdLib:<tag>")
}
```

The library brings in the Next-Gen AdMob SDK, Hilt, Firebase (Analytics + Remote Config), Adjust, Glide, and `customactivityoncrash` transitively — you don't need to declare them yourself.

> **If your app (or any other dependency) still pulls in the legacy AdMob SDK**, exclude it to avoid duplicate-class conflicts between the legacy and Next-Gen SDKs:
> ```kotlin
> configurations.all {
>     exclude(group = "com.google.android.gms", module = "play-services-ads")
>     exclude(group = "com.google.android.gms", module = "play-services-ads-lite")
> }
> ```

## Getting started

```kotlin
class MyApplication : AdLibHiltApplication() {
    override fun onCreateExt() {
        super.onCreateExt()

        initRemoteConfig(R.xml.remote_config_defaults) { success ->
            // Remote Config fetched & activated (or defaults applied if offline)
            initAOA() // App Open Ads are ready to preload once Remote Config is available
        }

        initTracker("<YOUR_ADJUST_APP_TOKEN>") // optional
    }
}
```

```kotlin
class MainActivity : AppCompatActivity() {
    @Inject lateinit var activityAdLoader: ActivityAdLoader

    override fun onResume() {
        super.onResume()
        activityAdLoader.onActivityResumed(this) // preloads ads per Remote Config rules
    }
}
```

### Optional: SDK warmup overlay (only if you have no splash screen)

If your app does **not** already have a splash screen and needs a brief blocking wait during cold start to guarantee AdMob and Firebase Remote Config are initialized before triggering the first App Open Ad or Banner request, you can use `SdkWarmupOverlay`:

> ⚠️ **Warning:** If your app already displays a native or custom splash screen while cold-starting, **do not** call `sdkWarmupOverlay.show()`. Showing both back-to-back doubles the perceived wait time for the user. This overlay is strictly opt-in and is never displayed automatically.

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var sdkWarmupOverlay: SdkWarmupOverlay
    @Inject lateinit var remoteConfigHelper: FirebaseRemoteConfigHelper
    @Inject lateinit var admobHelper: AdmobHelper
    @Inject lateinit var appOpenHelper: AppOpenHelper

    override fun setupData() {
        super.setupData()

        // Show overlay with safety timeout (4s fail-safe)
        sdkWarmupOverlay.show(this) {
            // Called when both ready or when timeout expires
        }

        remoteConfigHelper.fetchAndActivate({ success ->
            sdkWarmupOverlay.markRemoteConfigReady()
        }, R.xml.remote_config_defaults)

        admobHelper.initAdmob({
            sdkWarmupOverlay.markAdMobReady()
            appOpenHelper.setAdUnitId(Constant.ADMOB_AOA_AD_UNIT_ID)
            appOpenHelper.loadAd(applicationContext)
        })
    }
}
```

## How the AdMob ↔ House Ads fallback works

Each ad placement is configured with a small JSON object in Remote Config:

```json
{
  "useHouseAd": false,
  "customId": null,
  "autoFallback": true
}
```

- `useHouseAd: true` — always show a House Ad for this placement, skip AdMob entirely
- `useHouseAd: false` + `autoFallback: true` — try AdMob first; if it's unreachable, fails to load, or is blocked by frequency capping, fall back to a House Ad automatically
- `customId` — override the default ad unit ID for this specific placement

This decision happens per-placement, so different screens in the same app can route to different networks independently — mirroring the same `useMax`-style pattern this library originally used for network selection, just generalized to AdMob vs. House.

### Network reachability

Falling back purely on a raw TCP `Socket.connect()` can produce false positives in regions where filtering happens at the TLS/SNI layer (the handshake succeeds, but the actual ad request is still blocked). `NetworkUtils.isAdServerReachable()` instead performs a lightweight HTTPS `HEAD` request to a real Google ad-serving host, so the fallback decision reflects what an actual ad request would experience — typically resolving in a few seconds instead of waiting out AdMob's full connection timeout.

## House Ads content (Remote Config)

Key: `house_ads_json` — a JSON array, one object per ad:

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

Ads are selected round-robin among enabled entries. If the key is missing, empty, or fails to parse, a small set of built-in defaults is used instead so the app never shows an empty slot. Icon/media images are loaded remotely via Glide with a local drawable fallback. Clicks are debounced (1s) and open the target via `market://` first, falling back to a browser Intent if Play Store isn't available.

## Remote Config reference

| Key | Type | Description |
|---|---|---|
| `bn_enable`, `iv_enable`, `rv_enable`, `nt_enable`, `aoa_enable` | boolean | Enable/disable each AdMob format |
| `house_ads_enabled` | boolean | Global House Ads master switch |
| `house_ads_auto_fallback` | boolean | Auto-fallback to House Ads on AdMob failure/unavailability |
| `house_ads_json` | string (JSON array) | House Ads content, see above |
| `RC_IV_SHOW_FREQUENCY` | int (seconds) | Cooldown between Interstitial impressions |
| `RC_AOA_SHOW_FREQUENCY` | int (seconds) | Cooldown between App Open impressions |
| `RC_IV_DELAY_SHOW_AFTER_RV` | int (seconds) | Delay before showing an Interstitial after a Rewarded ad |
| `RC_ENABLE_ACTIVITY_AD_LOADER` | boolean | Master switch for `ActivityAdLoader` preloading |
| `RC_AOA_AD_UNIT_ID`, `iv_ad_unit_id`, `rv_ad_unit_id`, `nt_ad_unit_id`, `bn_ad_unit_id`, `c_bn_ad_unit_id` | string | Default ad unit IDs per format |
| `PRELOAD_{ActivityName}` / `PRELOAD_{ActivityName}_{FragmentName}` | string | Comma-separated ad keys to preload for that screen |
| `<PLACEMENT_KEY>` (e.g. `MAIN_IV`, `HOME_RV`) | JSON | Per-placement routing, see [above](#how-the-admob--house-ads-fallback-works) |

## Frequency capping & persistence

`AdFrequencyManager` tracks last-shown timestamps per format and per placement, plus a per-session impression cap. State is persisted through `PreferencesHelper` (a `SharedPreferences` + Gson wrapper) so a killed/restarted process doesn't reset the cooldown. Session counters are intentionally **not** persisted — they reset on every app launch.

## Crash recovery screen

`AdLibBaseApplication` automatically installs `customactivityoncrash` (showing `AdLibCustomCrashActivity` instead of a raw crash) whenever `isDebugMode()` returns `true` — which defaults to `Constant.DEBUG_MODE`. To control this in your own app, override `isDebugMode()` in your `Application` subclass, or set `Constant.DEBUG_MODE` directly. Set it to `false` for release builds if you'd rather let a tool like Crashlytics handle crashes with its default (non-intercepted) behavior.

## Ad Inspector

```kotlin
admobHelper.launchAdInspector(context) { error -> /* handle */ }
```

## Known limitations / pending items

- **Coarse location collection**: Google has announced that a future GMA SDK release will collect coarse location (when the host app already holds location permission) unless disabled via a configuration flag. As of SDK `1.4.0`, this flag does not yet exist in the public API (`RequestConfiguration` / `InitializationConfig` / `MobileAds`). A placeholder `setLocationCollectionEnabled(Boolean)` and a `TODO` are in place in `AdmobHelper.kt` / `AdLibHiltApplication.kt` — **this must be re-checked on every future SDK upgrade** and disabled by default once the flag ships.
- Thread safety: since the Next-Gen SDK no longer guarantees callbacks run on the main thread, all UI-touching code inside ad callbacks is wrapped internally (`runOnUiThread` / `Handler(Looper.getMainLooper())`). If you add new callback-driven UI code on top of this library, wrap it the same way.

## Architecture

Built with Hilt, split into the `:adlib` library module and a `:demo` app module (see [Project structure](#project-structure)). Each ad format has its own `@Singleton` Helper (`InterstitialHelper`, `BannerHelper`, `RewardHelper`, `AppOpenHelper`, `NativeHelper`, `AdaptiveBannerHelper`) that talks to a shared `HouseAdManager` for fallback content, `AdFrequencyManager` for capping, `AdmobRateLimiter` for NO_FILL cooldowns, and `FirebaseRemoteConfigHelper` for configuration — so nothing needs to be wired manually beyond `initRemoteConfig`.
