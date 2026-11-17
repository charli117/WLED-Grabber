package com.abrenoch.wledgrabber.tv.fragments.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.leanback.widget.GuidedAction;
import androidx.leanback.widget.GuidanceStylist;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigFieldLabels;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.ValidationResult;
import com.abrenoch.wledgrabber.tv.R;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TV-2 advanced options that are not part of the live geometry editor: RGBW
 * white mode, average color, capture size and the optional quality switches.
 * Geometry and live preview live in {@code LayoutEditorActivity}.
 */
public class WledLayoutStepFragment extends SettingsStepBaseFragment {
    private static final long ID_BASE = 3000L;
    private static final long WHITE_MODE = 3102L;
    private static final long AVG_COLOR = 3103L;
    private static final long QUALITY_CROP = 3112L;
    private static final long QUALITY_LINEAR = 3113L;
    private static final long SET_WHITE_MODE = 3202L;

    private static final String[] NUMERIC_FIELDS = {
            ConfigFields.CAPTURE_MAX_DIMENSION,
            ConfigFields.QUALITY_SATURATION,
            ConfigFields.QUALITY_SMOOTHING
    };

    private final Map<String, Long> ids = new LinkedHashMap<>();
    private SettingsDraftController controller;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        // GuidedStepSupportFragment.onCreate() invokes onCreateActions(), so the
        // controller and action-id map must exist before super.onCreate().
        controller = new SettingsDraftController(new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(requireContext())),
                new ConfigValidator()));
        controller.load();
        for (int i = 0; i < NUMERIC_FIELDS.length; i++) {
            ids.put(NUMERIC_FIELDS[i], ID_BASE + i);
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public GuidanceStylist.Guidance onCreateGuidance(@Nullable Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(getString(R.string.layout_editor_advanced_title),
                getString(R.string.layout_editor_advanced_guidance),
                null, null);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, @Nullable Bundle savedInstanceState) {
        for (String field : NUMERIC_FIELDS) {
            actions.add(unSignedNumberAction(ids.get(field), title(field),
                    controller.draft().get(field)));
        }
        if (isRgbw()) {
            String[] whiteValues = {"min_subtract", "preserve"};
            String[] whiteLabels = {
                    getString(com.abrenoch.wledgrabber.common.R.string.wled_layout_white_min_subtract),
                    getString(com.abrenoch.wledgrabber.common.R.string.wled_layout_white_preserve)};
            actions.add(radioListAction(WHITE_MODE,
                    getString(com.abrenoch.wledgrabber.common.R.string.wled_layout_white_mode),
                    null, (int) SET_WHITE_MODE, whiteLabels, whiteValues,
                    controller.draft().get(ConfigFields.WLED_RGBW_WHITE_MODE)));
        }
        actions.add(new GuidedAction.Builder(requireContext())
                .id(AVG_COLOR)
                .title(getString(com.abrenoch.wledgrabber.common.R.string.wled_layout_avg_color))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(Boolean.parseBoolean(
                        controller.draft().get(ConfigFields.USE_AVG_COLOR)))
                .build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(QUALITY_CROP)
                .title(getString(com.abrenoch.wledgrabber.common.R.string.field_quality_crop))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(Boolean.parseBoolean(
                        controller.draft().get(ConfigFields.QUALITY_CROP_BLACK_BARS)))
                .build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(QUALITY_LINEAR)
                .title(getString(com.abrenoch.wledgrabber.common.R.string.field_quality_linear))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(Boolean.parseBoolean(
                        controller.draft().get(ConfigFields.QUALITY_LINEAR_AVERAGE)))
                .build());
    }

    @Override
    public void onCreateButtonActions(List<GuidedAction> actions, @Nullable Bundle savedInstanceState) {
        actions.add(continueAction());
        actions.add(backAction());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == CONTINUE) {
            save();
            return;
        }
        super.onGuidedActionClicked(action);
    }

    private void save() {
        try {
            for (String field : NUMERIC_FIELDS) {
                controller.set(field, Integer.toString(assertIntValue(ids.get(field))));
            }
            if (isRgbw()) {
                controller.set(ConfigFields.WLED_RGBW_WHITE_MODE,
                        assertSubActionValue(WHITE_MODE, String.class));
            }
            controller.set(ConfigFields.USE_AVG_COLOR,
                    Boolean.toString(findActionById(AVG_COLOR).isChecked()));
            controller.set(ConfigFields.QUALITY_CROP_BLACK_BARS,
                    Boolean.toString(findActionById(QUALITY_CROP).isChecked()));
            controller.set(ConfigFields.QUALITY_LINEAR_AVERAGE,
                    Boolean.toString(findActionById(QUALITY_LINEAR).isChecked()));
            ValidationResult<ConfigSnapshot> result = controller.save();
            if (!result.isValid()) {
                showToast(getString(com.abrenoch.wledgrabber.common.R.string.wled_setup_invalid)
                        + " " + result.errors().get(0).field()
                        + ":" + result.errors().get(0).code());
                return;
            }
            showToast(getString(com.abrenoch.wledgrabber.common.R.string.wled_setup_saved));
            requireActivity().setResult(android.app.Activity.RESULT_OK);
            finishGuidedStepSupportFragments();
        } catch (AssertionError error) {
            showToast(getString(com.abrenoch.wledgrabber.common.R.string.wled_setup_invalid));
        }
    }

    private boolean isRgbw() {
        return "rgbw".equalsIgnoreCase(
                controller.draft().get(ConfigFields.WLED_RGBW_MODE));
    }

    private String title(String field) {
        return ConfigFieldLabels.label(requireContext(), field);
    }
}
