package com.abrenoch.wledgrabber.tv.fragments.settings

import androidx.leanback.widget.GuidanceStylist
import com.abrenoch.wledgrabber.tv.R

class SettingsStepStylist : GuidanceStylist() {
    override fun onProvideLayoutId(): Int {
        return R.layout.settings_guidance
    }
}