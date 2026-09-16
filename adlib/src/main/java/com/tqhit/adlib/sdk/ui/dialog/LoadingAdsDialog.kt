package com.tqhit.adlib.sdk.ui.dialog

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.core.graphics.drawable.toDrawable
import com.tqhit.adlib.R
import com.tqhit.adlib.databinding.DialogPrepairLoadingAdsBinding
import com.tqhit.adlib.sdk.base.ui.AdLibBaseDialog

class LoadingAdsDialog(context: Context) : AdLibBaseDialog<DialogPrepairLoadingAdsBinding>(context) {
    override val binding: DialogPrepairLoadingAdsBinding by lazy {
        DialogPrepairLoadingAdsBinding.inflate(LayoutInflater.from(context))
    }

    init {
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    override fun initWindow() {
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        window?.attributes?.windowAnimations = R.style.DialogAnimation

        val displayMetrics = context.resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels
        val density = displayMetrics.density
        val minWidthPx = (200 * density).toInt()
        val maxWidthPx = (240 * density).toInt()
        val targetWidthPx = (screenWidthPx * 0.60f).toInt().coerceIn(minWidthPx, maxWidthPx)

        window?.setLayout(targetWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(window?.attributes)
        layoutParams.dimAmount = 0.6f
        window?.attributes = layoutParams
    }

    override fun onStart() {
        super.onStart()
        val pulseAnim = AnimationUtils.loadAnimation(context, R.anim.anim_loading_pulse)
        binding.animLoading.startAnimation(pulseAnim)
    }

    override fun onStop() {
        binding.animLoading.clearAnimation()
        super.onStop()
    }
}
