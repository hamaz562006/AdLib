package com.tqhit.adlib.sdk.ui

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import com.tqhit.adlib.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional SDK warmup overlay for apps that do NOT already have their own splash screen.
 * If your app already shows a splash screen while the app cold-starts, do NOT also call
 * show() here — showing both back-to-back doubles the perceived wait time for the user.
 * Only use this if your app has no splash screen and needs a short blocking wait to ensure
 * AdMob and Remote Config are ready before the first App Open / Banner request.
 */
@Singleton
class SdkWarmupOverlay @Inject constructor() {
    private var adMobReady = false
    private var remoteConfigReady = false
    private var overlayView: View? = null
    private var onReadyCallback: (() -> Unit)? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutMs = 4000L

    fun show(activity: Activity, onReady: () -> Unit) {
        // If already both ready, invoke onReady immediately without showing overlay
        if (adMobReady && remoteConfigReady) {
            onReady()
            return
        }

        this.onReadyCallback = onReady

        val rootContentView = activity.findViewById<ViewGroup>(android.R.id.content)
        if (rootContentView != null && overlayView == null) {
            val inflater = LayoutInflater.from(activity)
            val view = inflater.inflate(R.layout.overlay_loading_ads, rootContentView, false)
            val animLoading = view.findViewById<ImageView>(R.id.anim_loading)
            val pulseAnim = AnimationUtils.loadAnimation(activity, R.anim.anim_loading_pulse)
            animLoading?.startAnimation(pulseAnim)

            rootContentView.addView(view)
            overlayView = view
        }

        // Fail-safe: dismiss after timeoutMs even if ready signals haven't arrived
        timeoutHandler.postDelayed({
            dismiss()
        }, timeoutMs)
    }

    fun markAdMobReady() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            adMobReady = true
            checkAndDismiss()
        } else {
            timeoutHandler.post {
                adMobReady = true
                checkAndDismiss()
            }
        }
    }

    fun markRemoteConfigReady() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            remoteConfigReady = true
            checkAndDismiss()
        } else {
            timeoutHandler.post {
                remoteConfigReady = true
                checkAndDismiss()
            }
        }
    }

    private fun checkAndDismiss() {
        if (adMobReady && remoteConfigReady) {
            dismiss()
        }
    }

    private fun dismiss() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            timeoutHandler.post { dismiss() }
            return
        }
        timeoutHandler.removeCallbacksAndMessages(null)
        val view = overlayView
        overlayView = null
        if (view != null) {
            val animLoading = view.findViewById<ImageView>(R.id.anim_loading)
            animLoading?.clearAnimation()
            (view.parent as? ViewGroup)?.removeView(view)
        }
        val callback = onReadyCallback
        onReadyCallback = null
        callback?.invoke()
    }
}
