package com.tqhit.adlib.sdk.ads.house

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.tqhit.adlib.R
import com.tqhit.adlib.databinding.DialogHouseInterstitialBinding
import com.tqhit.adlib.sdk.ads.AdFrequencyManager
import com.tqhit.adlib.sdk.ads.callback.house.HouseInterstitialAdCallback
import com.tqhit.adlib.sdk.ads.house.model.HouseAdType
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.utils.Constant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseInterstitialHelper @Inject constructor(
    private val houseAdManager: HouseAdManager,
    private val preferencesHelper: PreferencesHelper,
    private val adFrequencyManager: AdFrequencyManager
) {
    fun showHouseInterstitial(
        activity: Activity,
        callback: HouseInterstitialAdCallback? = null,
        ignoreFrequencyCheck: Boolean = false
    ) {
        if (preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)) {
            callback?.onAdFailedToLoad("User is premium")
            return
        }

        if (!ignoreFrequencyCheck && !adFrequencyManager.canShowInterstitial()) {
            callback?.onAdFailedToLoad("Frequency limit reached")
            return
        }

        val adItem = houseAdManager.getNextAd(HouseAdType.INTERSTITIAL)

        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogHouseInterstitialBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
        }
        dialog.setCancelable(false)

        binding.tvHouseAdTitle.text = adItem.title
        binding.tvHouseAdDesc.text = adItem.description
        binding.btnHouseAdCta.text = adItem.ctaText
        binding.tvHouseAdRating.text = "★ ${adItem.rating} • Free Download"

        loadImage(activity, adItem.iconUrl, adItem.iconResName, binding.ivHouseAdIcon)
        loadImage(activity, adItem.mediaUrl, adItem.mediaResName, binding.ivHouseMediaImage)

        var countDownTimer: CountDownTimer? = null

        // 3 seconds countdown skip timer
        binding.ivHouseClose.alpha = 0.3f
        binding.ivHouseClose.isEnabled = false

        countDownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                binding.tvHouseCountdown.text = "Skip in ${seconds}s"
                binding.tvHouseCountdown.visibility = android.view.View.VISIBLE
            }

            override fun onFinish() {
                binding.tvHouseCountdown.visibility = android.view.View.GONE
                binding.ivHouseClose.alpha = 1.0f
                binding.ivHouseClose.isEnabled = true
            }
        }.start()

        binding.ivHouseClose.setOnClickListener {
            countDownTimer?.cancel()
            dialog.dismiss()
            adFrequencyManager.onInterstitialDismissed()
            callback?.onAdClosed()
        }

        binding.btnHouseAdCta.setOnClickListener {
            houseAdManager.recordClick(activity, adItem)
            callback?.onAdClicked()
        }

        dialog.setOnDismissListener {
            countDownTimer?.cancel()
            adFrequencyManager.onInterstitialDismissed()
        }

        try {
            dialog.show()
            houseAdManager.recordImpression(adItem)
            adFrequencyManager.onInterstitialShown()
            callback?.onAdLoaded(adItem)
            callback?.onAdImpression()
        } catch (e: Exception) {
            callback?.onAdFailedToLoad(e.message ?: "Failed to show dialog")
        }
    }

    private fun loadImage(activity: Activity, url: String?, resName: String?, imageView: ImageView) {
        if (!url.isNullOrBlank()) {
            Glide.with(activity)
                .load(url)
                .placeholder(R.drawable.ads_icon)
                .error(R.drawable.ads_icon)
                .into(imageView)
        } else if (!resName.isNullOrBlank()) {
            val resId = activity.resources.getIdentifier(resName, "drawable", activity.packageName)
            if (resId != 0) imageView.setImageResource(resId) else imageView.setImageResource(R.drawable.ads_icon)
        } else {
            imageView.setImageResource(R.drawable.ads_icon)
        }
    }
}
