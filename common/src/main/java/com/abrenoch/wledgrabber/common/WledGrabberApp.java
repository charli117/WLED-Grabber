package com.abrenoch.wledgrabber.common;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Hosts the language preference. Applying it is done per activity through
 * {@code LocaleHelper} because the TV activities are not AppCompat activities.
 */
public class WledGrabberApp extends Application {
    public static final String LANGUAGE_KEY = "pref_key_app_language";
    public static final String LANGUAGE_SYSTEM = "system";
    public static final String LANGUAGE_ZH = "zh-CN";
    public static final String LANGUAGE_EN = "en";

    public static String currentLanguage(Context context) {
        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context);
        String value = preferences.getString(LANGUAGE_KEY, LANGUAGE_SYSTEM);
        return value == null ? LANGUAGE_SYSTEM : value;
    }

    public static void setLanguage(Context context, String tag) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(LANGUAGE_KEY, tag)
                .apply();
    }
}
