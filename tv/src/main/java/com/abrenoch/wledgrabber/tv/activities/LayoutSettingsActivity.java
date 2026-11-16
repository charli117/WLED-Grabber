package com.abrenoch.wledgrabber.tv.activities;

import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;
import androidx.leanback.app.GuidedStepSupportFragment;

import com.abrenoch.wledgrabber.tv.fragments.settings.WledLayoutStepFragment;

/** TV-2 host for full layout settings and preview. */
public class LayoutSettingsActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(this, new WledLayoutStepFragment(),
                    android.R.id.content);
        }
    }
}
