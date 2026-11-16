package com.abrenoch.wledgrabber.tv.fragments.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.leanback.widget.GuidedAction;
import androidx.leanback.widget.GuidanceStylist;

import com.abrenoch.wledgrabber.common.R;
import com.abrenoch.wledgrabber.common.led.CaptureSizing;
import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.LayoutProbe;
import com.abrenoch.wledgrabber.common.led.Side;
import com.abrenoch.wledgrabber.common.network.wled.LayoutProbeSender;
import com.abrenoch.wledgrabber.common.util.ConfigFieldLabels;
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
    private static final long PROBE_TOP = 3107L;
    private static final long PROBE_RIGHT = 3108L;
    private static final long PROBE_BOTTOM = 3109L;
    private static final long PROBE_LEFT = 3110L;
    private static final long PROBE_INPUT = 3111L;
    private static final long QUALITY_CROP = 3112L;
    private static final long QUALITY_LINEAR = 3113L;
    private static final long SET_GAP_MODE = 3201L;
    private static final long SET_WHITE_MODE = 3202L;

    private static final String[] NUMERIC_FIELDS = {
            ConfigFields.WLED_PHYSICAL_LEDS,
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
            ConfigFields.FRAME_RATE, ConfigFields.CAPTURE_MAX_DIMENSION,
            ConfigFields.QUALITY_SATURATION, ConfigFields.QUALITY_SMOOTHING
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
        return new GuidanceStylist.Guidance(getString(R.string.wled_layout_title),
                getString(R.string.wled_layout_guidance),
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
                .title(getString(R.string.wled_layout_reverse))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(Boolean.parseBoolean(controller.draft().get(ConfigFields.LED_REVERSE)))
                .build());
        String[] gapValues = {"skip", "black"};
        String[] gapLabels = {getString(R.string.wled_layout_gap_skip),
                getString(R.string.wled_layout_gap_black)};
        actions.add(radioListAction(GAP_MODE, getString(R.string.wled_layout_gap_mode), null,
                (int) SET_GAP_MODE, gapLabels, gapValues,
                controller.draft().get(ConfigFields.LED_GAP_MODE)));
        String[] whiteValues = {"min_subtract", "preserve"};
        String[] whiteLabels = {getString(R.string.wled_layout_white_min_subtract),
                getString(R.string.wled_layout_white_preserve)};
        actions.add(radioListAction(WHITE_MODE, getString(R.string.wled_layout_white_mode), null,
                (int) SET_WHITE_MODE, whiteLabels, whiteValues,
                controller.draft().get(ConfigFields.WLED_RGBW_WHITE_MODE)));
        actions.add(new GuidedAction.Builder(requireContext())
                .id(AVG_COLOR)
                .title(getString(R.string.wled_layout_avg_color))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(Boolean.parseBoolean(controller.draft().get(ConfigFields.USE_AVG_COLOR)))
                .build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(PRESET_274)
                .title(getString(R.string.wled_layout_preset_274))
                .build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(PREVIEW)
                .title(getString(R.string.wled_layout_preview))
                .build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(TEST_PATTERN)
                .title(getString(R.string.wled_layout_test_pattern))
                .build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(PROBE_TOP).title(getString(R.string.wled_probe_top)).build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(PROBE_RIGHT).title(getString(R.string.wled_probe_right)).build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(PROBE_BOTTOM).title(getString(R.string.wled_probe_bottom)).build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(PROBE_LEFT).title(getString(R.string.wled_probe_left)).build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(PROBE_INPUT).title(getString(R.string.wled_probe_input)).build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(QUALITY_CROP)
                .title(getString(R.string.field_quality_crop))
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                .checked(Boolean.parseBoolean(
                        controller.draft().get(ConfigFields.QUALITY_CROP_BLACK_BARS)))
                .build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(QUALITY_LINEAR)
                .title(getString(R.string.field_quality_linear))
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
        if (action.getId() == PROBE_TOP) {
            probe(Side.TOP);
            return;
        }
        if (action.getId() == PROBE_RIGHT) {
            probe(Side.RIGHT);
            return;
        }
        if (action.getId() == PROBE_BOTTOM) {
            probe(Side.BOTTOM);
            return;
        }
        if (action.getId() == PROBE_LEFT) {
            probe(Side.LEFT);
            return;
        }
        if (action.getId() == PROBE_INPUT) {
            probe(null);
            return;
        }
        if (action.getId() == PRESET_274) {
            apply274Preset();
            return;
        }
        super.onGuidedActionClicked(action);
    }

    /**
     * Pulses the current draft's LEDs for one edge, or the input marker when
     * {@code side} is null, so the user can verify the mapping on the strip.
     */
    private void probe(Side side) {
        ValidationResult<ConfigSnapshot> validated = controller.validate();
        if (!validated.isValid()) {
            showToast(getString(R.string.wled_setup_invalid));
            return;
        }
        ConfigSnapshot snapshot = validated.value();
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        CaptureSizing.Size size = CaptureSizing.fit(metrics.widthPixels, metrics.heightPixels,
                snapshot.capture().maxDimension());
        ValidationResult<CompiledLayout> compiled = new ClassicLedLayout().compile(
                snapshot.layout(), snapshot.device().physicalLedCount(),
                size.width(), size.height());
        if (!compiled.isValid()) {
            showToast(getString(R.string.wled_setup_invalid));
            return;
        }
        int[] logical = side == null
                ? new int[]{LayoutProbe.inputLogical(snapshot.layout())}
                : LayoutProbe.logicalForSide(snapshot.layout(), side);
        if (LayoutProbeSender.pulse(snapshot, compiled.value(), logical)) {
            showToast(getString(R.string.wled_probe_sent, logical.length));
        } else {
            showToast(getString(R.string.wled_probe_busy));
        }
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
        showToast(getString(R.string.wled_layout_preset_loaded));
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
            int physical = Integer.parseInt(
                    controller.draft().get(ConfigFields.WLED_PHYSICAL_LEDS));
            int layoutTotal = Integer.parseInt(controller.draft().get(ConfigFields.LED_TOP))
                    + Integer.parseInt(controller.draft().get(ConfigFields.LED_BOTTOM))
                    + Integer.parseInt(controller.draft().get(ConfigFields.LED_LEFT))
                    + Integer.parseInt(controller.draft().get(ConfigFields.LED_RIGHT));
            if (physical < layoutTotal) {
                // LedMapping rejects physical < effective, which would only surface
                // as a silent start failure later; block it here instead.
                showToast(getString(R.string.wled_layout_physical_too_small, physical, layoutTotal));
                return;
            }
            controller.set(ConfigFields.LED_REVERSE,
                    Boolean.toString(findActionById(REVERSE).isChecked()));
            controller.set(ConfigFields.LED_GAP_MODE,
                    assertSubActionValue(GAP_MODE, String.class));
            controller.set(ConfigFields.WLED_RGBW_WHITE_MODE,
                    assertSubActionValue(WHITE_MODE, String.class));
            controller.set(ConfigFields.USE_AVG_COLOR,
                    Boolean.toString(findActionById(AVG_COLOR).isChecked()));
            controller.set(ConfigFields.QUALITY_CROP_BLACK_BARS,
                    Boolean.toString(findActionById(QUALITY_CROP).isChecked()));
            controller.set(ConfigFields.QUALITY_LINEAR_AVERAGE,
                    Boolean.toString(findActionById(QUALITY_LINEAR).isChecked()));
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

    private String title(String field) {
        return ConfigFieldLabels.label(requireContext(), field);
    }
}
