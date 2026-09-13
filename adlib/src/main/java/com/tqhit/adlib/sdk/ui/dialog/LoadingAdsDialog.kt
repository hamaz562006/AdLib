package com.tqhit.adlib.sdk.ui.dialog

import android.content.Context
import android.view.LayoutInflater
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
}
