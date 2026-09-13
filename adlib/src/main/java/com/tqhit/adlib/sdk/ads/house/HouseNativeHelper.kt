package com.tqhit.adlib.sdk.ads.house

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.tqhit.adlib.R
import com.tqhit.adlib.databinding.LayoutHouseNativeBotBinding
import com.tqhit.adlib.databinding.LayoutHouseNativeFullBinding
import com.tqhit.adlib.sdk.ads.callback.house.HouseNativeAdCallback
import com.tqhit.adlib.sdk.ads.house.model.HouseAdType
import com.tqhit.adlib.sdk.data.local.PreferencesHelper
import com.tqhit.adlib.sdk.utils.Constant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HouseNativeHelper @Inject constructor(
    private val houseAdManager: HouseAdManager,
    private val preferencesHelper: PreferencesHelper
) {
    fun showHouseNativeSmall(
        context: Context,
        container: ViewGroup,
        callback: HouseNativeAdCallback? = null
    ): View? {
        if (preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)) {
            callback?.onAdFailedToLoad("User is premium")
            return null
        }

        val adItem = houseAdManager.getNextAd(HouseAdType.NATIVE)
        val binding = LayoutHouseNativeBotBinding.inflate(LayoutInflater.from(context), container, false)

        binding.tvHouseNativeTitle.text = adItem.title
        binding.tvHouseNativeDesc.text = adItem.description
        binding.btnHouseNativeCta.text = adItem.ctaText

        loadImage(context, adItem.iconUrl, adItem.iconResName, binding.ivHouseNativeIcon)

        val clickListener = View.OnClickListener {
            houseAdManager.recordClick(context, adItem)
            callback?.onAdClicked()
        }

        binding.btnHouseNativeCta.setOnClickListener(clickListener)
        binding.houseNativeRoot.setOnClickListener(clickListener)

        container.removeAllViews()
        container.addView(binding.root)

        houseAdManager.recordImpression(adItem)
        callback?.onAdLoaded(adItem)
        callback?.onAdImpression()

        return binding.root
    }

    fun showHouseNativeFull(
        context: Context,
        container: ViewGroup,
        callback: HouseNativeAdCallback? = null
    ): View? {
        if (preferencesHelper.getBoolean(Constant.IS_PREMIUM, false)) {
            callback?.onAdFailedToLoad("User is premium")
            return null
        }

        val adItem = houseAdManager.getNextAd(HouseAdType.NATIVE)
        val binding = LayoutHouseNativeFullBinding.inflate(LayoutInflater.from(context), container, false)

        binding.tvHouseNativeFullTitle.text = adItem.title
        binding.tvHouseNativeFullDesc.text = adItem.description
        binding.btnHouseNativeFullCta.text = adItem.ctaText

        loadImage(context, adItem.iconUrl, adItem.iconResName, binding.ivHouseNativeFullIcon)
        loadImage(context, adItem.mediaUrl, adItem.mediaResName, binding.ivHouseNativeFullMedia)

        val clickListener = View.OnClickListener {
            houseAdManager.recordClick(context, adItem)
            callback?.onAdClicked()
        }

        binding.btnHouseNativeFullCta.setOnClickListener(clickListener)
        binding.houseNativeFullRoot.setOnClickListener(clickListener)

        container.removeAllViews()
        container.addView(binding.root)

        houseAdManager.recordImpression(adItem)
        callback?.onAdLoaded(adItem)
        callback?.onAdImpression()

        return binding.root
    }

    private fun loadImage(context: Context, url: String?, resName: String?, imageView: ImageView) {
        if (!url.isNullOrBlank()) {
            Glide.with(context)
                .load(url)
                .placeholder(R.drawable.ads_icon)
                .error(R.drawable.ads_icon)
                .into(imageView)
        } else if (!resName.isNullOrBlank()) {
            val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)
            if (resId != 0) imageView.setImageResource(resId) else imageView.setImageResource(R.drawable.ads_icon)
        } else {
            imageView.setImageResource(R.drawable.ads_icon)
        }
    }
}
