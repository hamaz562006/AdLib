package com.tqhit.adlib.sdk.base

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import cat.ereza.customactivityoncrash.config.CaocConfig
import com.google.android.libraries.ads.mobile.sdk.common.AdActivity
import com.tqhit.adlib.sdk.ui.crash.AdLibCustomCrashActivity
import com.tqhit.adlib.sdk.utils.Constant

abstract class AdLibBaseApplication : Application(), Application.ActivityLifecycleCallbacks, LifecycleObserver {
    protected var currentActivity: Activity? = null

    open fun onCreateExt() {}

    open fun isDebugMode(): Boolean {
        return Constant.DEBUG_MODE
    }

    protected fun isMainProcess(): Boolean {
        val currentProcessName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            val pid = Process.myPid()
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            manager?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }
        return currentProcessName == packageName
    }

    open fun setupCAOC() {
        CaocConfig.Builder.create()
            .enabled(true) // default: true
            .showErrorDetails(true) // default: true
            .showRestartButton(true) // default: true
            .logErrorOnRestart(true) // default: true
            .trackActivities(true) // default: false
            .minTimeBetweenCrashesMs(3000) //default: 3000
            .errorActivity(customErrorActivity()) //default: null (default error activity)
            .apply()
    }

    open fun customErrorActivity(): Class<out Activity> {
        return AdLibCustomCrashActivity::class.java
    }

    override fun onCreate() {
        super.onCreate()
        setupCAOC()
        if (isMainProcess()) {
            registerActivityLifecycleCallbacks(this)
            onCreateExt()
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is AdActivity) return
        currentActivity = activity
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    protected open fun showAOA() {
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    protected open fun onMoveToForeground() {
        showAOA()
    }
}