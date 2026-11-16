package com.abrenoch.wledgrabber.common.util;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import com.abrenoch.wledgrabber.common.WledGrabberApp;

import java.util.Locale;

/**
 * Wraps an Activity base context with the user's language choice. Needed because
 * the TV activities are plain FragmentActivity, which AppCompat's per-app
 * locales do not cover.
 */
public final class LocaleHelper {
    private LocaleHelper() {
    }

    public static Context wrap(Context base) {
        String tag = WledGrabberApp.currentLanguage(base);
        if (WledGrabberApp.LANGUAGE_SYSTEM.equals(tag)) {
            return base;
        }
        Locale locale = Locale.forLanguageTag(tag);
        Locale.setDefault(locale);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(new LocaleList(locale));
        } else {
            configuration.setLocale(locale);
        }
        return base.createConfigurationContext(configuration);
    }
}
