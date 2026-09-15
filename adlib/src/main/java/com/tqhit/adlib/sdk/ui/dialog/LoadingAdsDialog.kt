package com.tqhit.adlib.sdk.ui.dialog

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
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
        window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val layoutParams = WindowManager.LayoutParams()
        layoutParams.copyFrom(window?.attributes)
        layoutParams.dimAmount = 0.6f
        window?.attributes = layoutParams
    }
}
