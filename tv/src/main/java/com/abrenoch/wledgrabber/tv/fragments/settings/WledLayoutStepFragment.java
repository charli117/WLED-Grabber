package com.abrenoch.wledgrabber.tv.fragments.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.leanback.widget.GuidedAction;
import androidx.leanback.widget.GuidanceStylist;

import com.abrenoch.wledgrabber.common.R;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.ValidationResult;
import com.abrenoch.wledgrabber.common.views.LayoutPreviewActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TV-2 full Basic/Advanced layout and white-mode settings. */
public class WledLayoutStepFragment extends SettingsStepBaseFragment {
    private static final long ID_BASE = 3000L;
    private static final long REVERSE = 3100L;
    private static final long GAP_MODE = 3101L;
    private static final long WHITE_MODE = 3102L;
    private static final long AVG_COLOR = 3103L;
    private static final long PREVIEW = 3104L;
    private static final long PRESET_274 = 3105L;
    private static final long TEST_PATTERN = 3106L;
    private static final long SET_GAP_MODE = 3201L;
    private static final long SET_WHITE_MODE = 3202L;

    private static final String[] NUMERIC_FIELDS = {
            ConfigFields.LED_TOP, ConfigFields.LED_BOTTOM,
            ConfigFields.LED_LEFT, ConfigFields.LED_RIGHT,
            ConfigFields.LED_GAP_LENGTH, ConfigFields.LED_GAP_POSITION,
            ConfigFields.LED_INPUT_POSITION, ConfigFields.LED_H_DEPTH,
            ConfigFields.LED_V_DEPTH, ConfigFields.LED_OVERLAP,
            ConfigFields.LED_EDGE_GAP,
            ConfigFields.LED_CORNER_TL_H, ConfigFields.LED_CORNER_TL_V,
            ConfigFields.LED_CORNER_TR_H, ConfigFields.LED_CORNER_TR_V,
            ConfigFields.LED_CORNER_BR_H, ConfigFields.LED_CORNER_BR_V,
            ConfigFields.LED_CORNER_BL_H, ConfigFields.LED_CORNER_BL_V,
            ConfigFields.FRAME_RATE, ConfigFields.CAPTURE_MAX_DIMENSION
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
        return new GuidanceStylist.Guidance("WLED layout",
                "Changes apply on next start. Stop sends black and verifies WLED off when reachable.",
                null, null);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, @Nullable Bundle savedInstanceState) {
        for (String field : NUMERIC_FIELDS) {
            actions.add(unSignedNumberAction(ids.get(field), title(field),
                    controller.draft().get(field)));
        }
        actions.add(new GuidedAction.Builder(requireContext())
                .id(REVERSE)
                .title("Reverse physical order")
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(Boolean.parseBoolean(controller.draft().get(ConfigFields.LED_REVERSE)))
                .build());
        String[] gapModes = {"skip", "black"};
        actions.add(radioListAction(GAP_MODE, "Gap mode", null, (int) SET_GAP_MODE,
                gapModes, gapModes, controller.draft().get(ConfigFields.LED_GAP_MODE)));
        String[] whiteModes = {"min_subtract", "preserve"};
        actions.add(radioListAction(WHITE_MODE, "RGBW white mode", null, (int) SET_WHITE_MODE,
                whiteModes, whiteModes,
                controller.draft().get(ConfigFields.WLED_RGBW_WHITE_MODE)));
        actions.add(new GuidedAction.Builder(requireContext())
                .id(AVG_COLOR)
                .title("Average screen color")
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(Boolean.parseBoolean(controller.draft().get(ConfigFields.USE_AVG_COLOR)))
                .build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(PRESET_274)
                .title("Load 274 first-unit preset")
                .build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(PREVIEW)
                .title("Preview layout")
                .build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(TEST_PATTERN)
                .title("Run 10-second test patterns")
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
            save(null);
            return;
        }
        if (action.getId() == PREVIEW) {
            save(LayoutPreviewActivity.class);
            return;
        }
        if (action.getId() == TEST_PATTERN) {
            save(com.abrenoch.wledgrabber.common.views.TestPatternActivity.class);
            return;
        }
        if (action.getId() == PRESET_274) {
            apply274Preset();
            return;
        }
        super.onGuidedActionClicked(action);
    }

    private void apply274Preset() {
        setField(ConfigFields.LED_TOP, 86);
        setField(ConfigFields.LED_BOTTOM, 90);
        setField(ConfigFields.LED_LEFT, 49);
        setField(ConfigFields.LED_RIGHT, 49);
        setField(ConfigFields.LED_GAP_LENGTH, 0);
        setField(ConfigFields.LED_GAP_POSITION, 0);
        setField(ConfigFields.LED_INPUT_POSITION, 161);
        setField(ConfigFields.LED_H_DEPTH, 8);
        setField(ConfigFields.LED_V_DEPTH, 5);
        setField(ConfigFields.LED_OVERLAP, 0);
        setField(ConfigFields.LED_EDGE_GAP, 0);
        setField(ConfigFields.LED_CORNER_TL_H, 0);
        setField(ConfigFields.LED_CORNER_TL_V, 0);
        setField(ConfigFields.LED_CORNER_TR_H, 100);
        setField(ConfigFields.LED_CORNER_TR_V, 0);
        setField(ConfigFields.LED_CORNER_BR_H, 100);
        setField(ConfigFields.LED_CORNER_BR_V, 100);
        setField(ConfigFields.LED_CORNER_BL_H, 0);
        setField(ConfigFields.LED_CORNER_BL_V, 100);
        controller.set(ConfigFields.LED_REVERSE, "true");
        controller.set(ConfigFields.LED_GAP_MODE, "skip");
        controller.set(ConfigFields.WLED_RGBW_WHITE_MODE, "min_subtract");
        showToast("274 preset loaded. Review and save.");
    }

    private void setField(String field, int value) {
        controller.set(field, Integer.toString(value));
        Long id = ids.get(field);
        if (id != null) {
            GuidedAction action = findActionById(id);
            if (action != null) {
                action.setDescription(Integer.toString(value));
                notifyActionChanged(findActionPositionById(id));
            }
        }
    }

    private void save(Class<?> targetActivity) {
        try {
            for (String field : NUMERIC_FIELDS) {
                controller.set(field, Integer.toString(assertIntValue(ids.get(field))));
            }
            controller.set(ConfigFields.LED_REVERSE,
                    Boolean.toString(findActionById(REVERSE).isChecked()));
            controller.set(ConfigFields.LED_GAP_MODE,
                    assertSubActionValue(GAP_MODE, String.class));
            controller.set(ConfigFields.WLED_RGBW_WHITE_MODE,
                    assertSubActionValue(WHITE_MODE, String.class));
            controller.set(ConfigFields.USE_AVG_COLOR,
                    Boolean.toString(findActionById(AVG_COLOR).isChecked()));
            ValidationResult<ConfigSnapshot> result = controller.save();
            if (!result.isValid()) {
                showToast(getString(R.string.wled_setup_invalid) + " "
                        + result.errors().get(0).field() + ":"
                        + result.errors().get(0).code());
                return;
            }
            showToast(getString(R.string.wled_setup_saved));
            if (targetActivity != null) {
                startActivity(new android.content.Intent(requireContext(), targetActivity));
            }
            finishGuidedStepSupportFragments();
        } catch (AssertionError error) {
            showToast(getString(R.string.wled_setup_invalid));
        }
    }

    private static String title(String field) {
        String label = field.replace("pref_key_", "").replace('_', ' ');
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
