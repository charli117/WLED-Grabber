package com.abrenoch.wledgrabber.tv.activities;

import androidx.fragment.app.FragmentActivity;

public abstract class LeanbackActivity extends FragmentActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(
                com.abrenoch.wledgrabber.common.util.LocaleHelper.wrap(newBase));
    }
    @Override
    public boolean onSearchRequested() {
        return false;
    }
}
