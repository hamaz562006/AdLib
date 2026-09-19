package com.tqhit.adlib.demo

import com.tqhit.adlib.R
import com.tqhit.adlib.sdk.AdLibHiltApplication
import com.tqhit.adlib.sdk.utils.Constant
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DemoApplication : AdLibHiltApplication() {
    override fun onCreateExt() {
        super.onCreateExt()
        initAll(R.xml.remote_config_defaults, testDeviceIds = Constant.TEST_DEVICE_IDS)
    }
}
