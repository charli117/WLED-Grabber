package com.abrenoch.wledgrabber.tv.fragments.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.abrenoch.wledgrabber.tv.BuildConfig;
import com.abrenoch.wledgrabber.tv.R;

public class AboutDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.tv_about_title)
                .setMessage(getString(R.string.tv_about_details,
                        getString(R.string.tv_about_summary), getString(R.string.tv_about_author),
                        BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }
}
