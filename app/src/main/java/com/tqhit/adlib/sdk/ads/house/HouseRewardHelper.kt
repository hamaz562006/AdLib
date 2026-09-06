package com.tqhit.adlib.sdk.ads.house

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.tqhit.adlib.R
import com.tqhit.adlib.databinding.DialogHouseRewardBinding
import com.tqhit.adlib.sdk.ads.AdFrequencyManager
import com.tqhit.adlib.sdk.ads.callback.house.HouseRewardAdCallback
import com.tqhit.adlib.sdk.ads.house.model.HouseAdType
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.utils.Constant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseRewardHelper @Inject constructor(
    private val houseAdManager: HouseAdManager,
    private val preferencesHelper: PreferencesHelper,
    private val adFrequencyManager: AdFrequencyManager
) {
    fun showHouseReward(
        activity: Activity,
        callback: HouseRewardAdCallback? = null,
        ignoreFrequencyCheck: Boolean = false
    ) {
        if (preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)) {
            // Even if premium, users might want to claim rewards in some apps, or skip ad and grant reward
            callback?.onUserEarnedReward(10, "Coins")
            callback?.onAdClosed()
            return
        }

        val adItem = houseAdManager.getNextAd(HouseAdType.REWARDED)

        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogHouseRewardBinding.inflate(LayoutInflater.from(activity))
        dialog.setContentView(binding.root)

        dialog.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
        }
        dialog.setCancelable(false)

        binding.tvRewardAdTitle.text = adItem.title
        binding.tvRewardAdDesc.text = adItem.description
        binding.btnRewardAdCta.text = adItem.ctaText
        binding.tvRewardValue.text = "🎁 Claim +${adItem.rewardAmount} ${adItem.rewardType}"

        loadImage(activity, adItem.iconUrl, adItem.iconResName, binding.ivRewardAdIcon)
        loadImage(activity, adItem.mediaUrl, adItem.mediaResName, binding.ivRewardMediaImage)

        val totalDurationSeconds = adItem.countdownSeconds.coerceAtLeast(4)
        var userEarnedReward = false
        var countDownTimer: CountDownTimer? = null

        binding.ivRewardClose.visibility = View.GONE
        binding.pbRewardProgress.max = totalDurationSeconds * 1000

        countDownTimer = object : CountDownTimer((totalDurationSeconds * 1000).toLong(), 100) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                binding.tvRewardTimer.text = "Reward in ${secondsLeft}s"
                val progress = (totalDurationSeconds * 1000) - millisUntilFinished.toInt()
                binding.pbRewardProgress.progress = progress
            }

            override fun onFinish() {
                userEarnedReward = true
                binding.pbRewardProgress.progress = binding.pbRewardProgress.max
                binding.tvRewardTimer.text = "✓ Reward Claimed!"
                binding.tvRewardTimer.setTextColor(Color.parseColor("#34D399"))
                binding.ivRewardClose.visibility = View.VISIBLE
                callback?.onUserEarnedReward(adItem.rewardAmount, adItem.rewardType)
            }
        }.start()

        binding.ivRewardClose.setOnClickListener {
            countDownTimer?.cancel()
            dialog.dismiss()
            adFrequencyManager.onRewardedAdClosed()
            callback?.onAdClosed()
        }

        binding.btnRewardAdCta.setOnClickListener {
            houseAdManager.recordClick(activity, adItem)
            callback?.onAdClicked()
        }

        dialog.setOnDismissListener {
            countDownTimer?.cancel()
            adFrequencyManager.onRewardedAdClosed()
        }

        try {
            dialog.show()
            houseAdManager.recordImpression(adItem)
            adFrequencyManager.onRewardedAdShown()
            callback?.onAdLoaded(adItem)
            callback?.onAdImpression()
        } catch (e: Exception) {
            callback?.onAdFailedToLoad(e.message ?: "Failed to display reward ad")
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
