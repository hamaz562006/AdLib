package com.tqhit.adlib.sdk.ads.house

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.tqhit.adlib.R
import com.tqhit.adlib.databinding.LayoutHouseBannerBinding
import com.tqhit.adlib.sdk.ads.callback.house.HouseBannerAdCallback
import com.tqhit.adlib.sdk.ads.house.model.HouseAdType
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.utils.Constant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseBannerHelper @Inject constructor(
    private val houseAdManager: HouseAdManager,
    private val preferencesHelper: PreferencesHelper
) {
    fun loadHouseBanner(
        context: Context,
        container: ViewGroup,
        callback: HouseBannerAdCallback? = null
    ): View? {
        if (preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)) {
            callback?.onAdFailedToLoad("User is premium")
            return null
        }

        val adItem = houseAdManager.getNextAd(HouseAdType.BANNER)
        val binding = LayoutHouseBannerBinding.inflate(LayoutInflater.from(context), container, false)

        binding.tvHouseBannerTitle.text = adItem.title
        binding.tvHouseBannerDesc.text = adItem.description
        binding.btnHouseBannerCta.text = adItem.ctaText

        loadIcon(context, adItem.iconUrl, adItem.iconResName, binding.ivHouseBannerIcon)

        binding.btnHouseBannerCta.setOnClickListener {
            houseAdManager.recordClick(context, adItem)
            callback?.onAdClicked()
        }

        binding.houseBannerRoot.setOnClickListener {
            houseAdManager.recordClick(context, adItem)
            callback?.onAdClicked()
        }

        binding.ivHouseBannerClose.setOnClickListener {
            container.removeView(binding.root)
            callback?.onAdClosed()
        }

        container.removeAllViews()
        container.addView(binding.root)

        houseAdManager.recordImpression(adItem)
        callback?.onAdLoaded(adItem)
        callback?.onAdImpression()

        return binding.root
    }

    private fun loadIcon(context: Context, iconUrl: String?, iconResName: String?, imageView: ImageView) {
        if (!iconUrl.isNullOrBlank()) {
            Glide.with(context)
                .load(iconUrl)
                .placeholder(R.drawable.ads_icon)
                .error(R.drawable.ads_icon)
                .into(imageView)
        } else if (!iconResName.isNullOrBlank()) {
            val resId = context.resources.getIdentifier(iconResName, "drawable", context.packageName)
            if (resId != 0) imageView.setImageResource(resId) else imageView.setImageResource(R.drawable.ads_icon)
        } else {
            imageView.setImageResource(R.drawable.ads_icon)
        }
    }
}
