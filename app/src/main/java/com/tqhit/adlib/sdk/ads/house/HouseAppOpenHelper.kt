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
import com.tqhit.adlib.databinding.DialogHouseAppOpenBinding
import com.tqhit.adlib.sdk.ads.AdFrequencyManager
import com.tqhit.adlib.sdk.ads.callback.house.HouseAppOpenAdCallback
import com.tqhit.adlib.sdk.ads.house.model.HouseAdType
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.utils.Constant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseAppOpenHelper @Inject constructor(
    private val houseAdManager: HouseAdManager,
    private val preferencesHelper: PreferencesHelper,
    private val adFrequencyManager: AdFrequencyManager
) {
    interface OnShowAdCompleteListener {
        fun onShowAdComplete()
    }

    fun showHouseAppOpen(
        activity: Activity,
        onShowAdCompleteListener: OnShowAdCompleteListener? = null,
        callback: HouseAppOpenAdCallback? = null,
        ignoreFrequencyCheck: Boolean = false
    ) {
        if (preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)) {
            onShowAdCompleteListener?.onShowAdComplete()
            callback?.onAdFailedToLoad("User is premium")
            return
        }

        if (!ignoreFrequencyCheck && !adFrequencyManager.canShowAppOpen()) {
            onShowAdCompleteListener?.onShowAdComplete()
            callback?.onAdFailedToLoad("Frequency limit reached")
            return
        }

        val adItem = houseAdManager.getNextAd(HouseAdType.APP_OPEN)

        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogHouseAppOpenBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
        }
        dialog.setCancelable(false)

        binding.tvHouseAoaTitle.text = adItem.title
        binding.tvHouseAoaDesc.text = adItem.description
        binding.btnHouseAoaCta.text = adItem.ctaText

        loadImage(activity, adItem.iconUrl, adItem.iconResName, binding.ivHouseAoaIcon)
        loadImage(activity, adItem.mediaUrl, adItem.mediaResName, binding.ivHouseAoaMedia)

        var countDownTimer: CountDownTimer? = null

        countDownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                binding.btnHouseAoaDismiss.text = "Skip (${seconds}s)"
            }

            override fun onFinish() {
                binding.btnHouseAoaDismiss.text = "Skip"
            }
        }.start()

        binding.btnHouseAoaDismiss.setOnClickListener {
            countDownTimer?.cancel()
            dialog.dismiss()
            adFrequencyManager.onAppOpenDismissed()
            onShowAdCompleteListener?.onShowAdComplete()
            callback?.onAdClosed()
        }

        binding.btnHouseAoaCta.setOnClickListener {
            houseAdManager.recordClick(activity, adItem)
            callback?.onAdClicked()
        }

        dialog.setOnDismissListener {
            countDownTimer?.cancel()
            adFrequencyManager.onAppOpenDismissed()
            onShowAdCompleteListener?.onShowAdComplete()
        }

        try {
            dialog.show()
            houseAdManager.recordImpression(adItem)
            adFrequencyManager.onAppOpenShown()
            callback?.onAdLoaded(adItem)
            callback?.onAdImpression()
        } catch (e: Exception) {
            onShowAdCompleteListener?.onShowAdComplete()
            callback?.onAdFailedToLoad(e.message ?: "Failed to show house open ad")
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
