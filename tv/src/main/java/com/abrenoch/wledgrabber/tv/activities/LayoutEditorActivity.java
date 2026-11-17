package com.abrenoch.wledgrabber.tv.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.FirstUnitPreset;
import com.abrenoch.wledgrabber.common.led.LayoutAutoDistribute;
import com.abrenoch.wledgrabber.common.led.LayoutDraft;
import com.abrenoch.wledgrabber.common.led.LayoutDraftCompiler;
import com.abrenoch.wledgrabber.common.led.LayoutProbe;
import com.abrenoch.wledgrabber.common.led.Side;
import com.abrenoch.wledgrabber.common.network.wled.LayoutProbeSender;
import com.abrenoch.wledgrabber.common.util.ConfigFieldLabels;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.ValidationResult;
import com.abrenoch.wledgrabber.common.views.LedLayoutPreviewView;
import com.abrenoch.wledgrabber.common.views.LayoutPreviewActivity;
import com.abrenoch.wledgrabber.common.views.TestPatternActivity;
import com.abrenoch.wledgrabber.tv.R;
import com.abrenoch.wledgrabber.tv.views.LayoutValueRow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TV-2 live LED layout editor.
 *
 * <p>Replaces the long guided-step form: the preview recompiles from the draft on
 * every change (never from the last save), Left/Right step the focused value with
 * key-repeat acceleration, OK opens exact entry, and Save still goes through the
 * shared {@link SettingsDraftController}. Geometry, mapping and validation stay in
 * common; this host only owns presentation and focus.</p>
 */
public class LayoutEditorActivity extends LeanbackActivity {
    public static final String ACTION_AUTO = "action:auto";
    public static final String ACTION_PRESET_274 = "action:preset274";
    public static final String ACTION_PROBE_TOP = "action:probe:top";
    public static final String ACTION_PROBE_RIGHT = "action:probe:right";
    public static final String ACTION_PROBE_BOTTOM = "action:probe:bottom";
    public static final String ACTION_PROBE_LEFT = "action:probe:left";
    public static final String ACTION_PROBE_INPUT = "action:probe:input";
    public static final String ACTION_PREVIEW = "action:preview";
    public static final String ACTION_TEST = "action:test";

    private static final String EMPTY_VALUE = "—";
    private static final String PERCENT = "%";
    private static final String[] GAP_VALUES = {"skip", "black"};

    private static final String[] PERCENT_FIELDS = {
            ConfigFields.LED_H_DEPTH, ConfigFields.LED_V_DEPTH,
            ConfigFields.LED_OVERLAP, ConfigFields.LED_EDGE_GAP,
            ConfigFields.LED_CORNER_TL_H, ConfigFields.LED_CORNER_TL_V,
            ConfigFields.LED_CORNER_TR_H, ConfigFields.LED_CORNER_TR_V,
            ConfigFields.LED_CORNER_BR_H, ConfigFields.LED_CORNER_BR_V,
            ConfigFields.LED_CORNER_BL_H, ConfigFields.LED_CORNER_BL_V
    };

    private final Map<String, LayoutValueRow> rows = new LinkedHashMap<>();
    private SettingsDraftController controller;
    private LedLayoutPreviewView preview;
    private LinearLayout rowsContainer;
    private TextView status;
    private int captureWidth = 640;
    private int captureHeight = 360;
    private boolean initialFocusApplied;

    private final LayoutValueRow.Listener rowListener = new LayoutValueRow.Listener() {
        @Override
        public void onAdjust(LayoutValueRow row, int delta) {
            adjust(row, delta);
        }

        @Override
        public void onSelect(LayoutValueRow row) {
            select(row);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout_editor);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        captureWidth = Math.max(1, metrics.widthPixels);
        captureHeight = Math.max(1, metrics.heightPixels);

        controller = new SettingsDraftController(new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(this)), new ConfigValidator()));
        controller.load();

        preview = findViewById(R.id.layout_editor_preview);
        preview.setFocusable(false);
        preview.setFocusableInTouchMode(false);
        preview.setClickable(false);
        status = findViewById(R.id.layout_editor_status);
        rowsContainer = findViewById(R.id.layout_editor_rows);

        buildRows();
        refreshAll();

        android.view.View save = findViewById(R.id.layout_editor_save);
        android.view.View cancel = findViewById(R.id.layout_editor_cancel);
        save.setOnClickListener(v -> saveAndFinish());
        cancel.setOnClickListener(v -> finish());
        LayoutValueRow lastRow = null;
        for (LayoutValueRow row : rows.values()) {
            lastRow = row;
        }
        if (lastRow != null) {
            // ScrollView-based lists do not reliably hand focus to a sibling
            // below them; pin the insertion-order last row (currently the last
            // corner) so every row stays reachable with the remote.
            lastRow.setNextFocusDownId(R.id.layout_editor_save);
            lastRow.setNextDownView(save);
            save.setNextFocusUpId(lastRow.getId());
        }
        save.setNextFocusLeftId(R.id.layout_editor_cancel);
        cancel.setNextFocusRightId(R.id.layout_editor_save);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !initialFocusApplied) {
            initialFocusApplied = true;
            LayoutValueRow first = rows.get(ConfigFields.WLED_PHYSICAL_LEDS);
            if (first != null && getCurrentFocus() == null) {
                first.requestFocus();
            }
        }
    }

    private void buildRows() {
        addSection(R.string.layout_editor_section_shape);
        addNumber(ConfigFields.WLED_PHYSICAL_LEDS);
        // Starting actions sit next to the confirmed count so the first Down
        // reaches "auto-distribute" instead of eight corner rows.
        addAction(ACTION_AUTO, getString(R.string.layout_editor_auto));
        addAction(ACTION_PRESET_274,
                getString(com.abrenoch.wledgrabber.common.R.string.wled_layout_preset_274));
        addNumber(ConfigFields.LED_TOP);
        addNumber(ConfigFields.LED_BOTTOM);
        addNumber(ConfigFields.LED_LEFT);
        addNumber(ConfigFields.LED_RIGHT);

        addSection(R.string.layout_editor_section_mapping);
        addNumber(ConfigFields.LED_GAP_LENGTH);
        addNumber(ConfigFields.LED_GAP_POSITION);
        addChoice(ConfigFields.LED_GAP_MODE);
        addNumber(ConfigFields.LED_INPUT_POSITION);
        addToggle(ConfigFields.LED_REVERSE);

        addSection(R.string.layout_editor_section_sampling);
        addNumber(ConfigFields.LED_H_DEPTH);
        addNumber(ConfigFields.LED_V_DEPTH);
        addNumber(ConfigFields.LED_OVERLAP);
        addNumber(ConfigFields.LED_EDGE_GAP);

        // Verification is used more often than the eight corner percentages, so
        // keep it above the corner section.
        addSection(R.string.layout_editor_section_verify);
        addAction(ACTION_PROBE_TOP, getString(com.abrenoch.wledgrabber.common.R.string.wled_probe_top));
        addAction(ACTION_PROBE_RIGHT, getString(com.abrenoch.wledgrabber.common.R.string.wled_probe_right));
        addAction(ACTION_PROBE_BOTTOM, getString(com.abrenoch.wledgrabber.common.R.string.wled_probe_bottom));
        addAction(ACTION_PROBE_LEFT, getString(com.abrenoch.wledgrabber.common.R.string.wled_probe_left));
        addAction(ACTION_PROBE_INPUT, getString(com.abrenoch.wledgrabber.common.R.string.wled_probe_input));
        addAction(ACTION_PREVIEW, getString(com.abrenoch.wledgrabber.common.R.string.wled_layout_preview));
        addAction(ACTION_TEST,
                getString(com.abrenoch.wledgrabber.common.R.string.wled_layout_test_pattern));

        addSection(R.string.layout_editor_section_corners);
        addNumber(ConfigFields.LED_CORNER_TL_H);
        addNumber(ConfigFields.LED_CORNER_TL_V);
        addNumber(ConfigFields.LED_CORNER_TR_H);
        addNumber(ConfigFields.LED_CORNER_TR_V);
        addNumber(ConfigFields.LED_CORNER_BR_H);
        addNumber(ConfigFields.LED_CORNER_BR_V);
        addNumber(ConfigFields.LED_CORNER_BL_H);
        addNumber(ConfigFields.LED_CORNER_BL_V);
    }

    private void addSection(int titleRes) {
        TextView header = new TextView(this);
        header.setText(titleRes);
        header.setTextSize(16);
        header.setTextColor(0xFFCFD8DC);
        header.setPadding(dp(8), dp(18), dp(8), dp(6));
        rowsContainer.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void addNumber(String field) {
        addRow(LayoutValueRow.number(this, field, label(field), rowListener));
    }

    private void addChoice(String field) {
        addRow(LayoutValueRow.choice(this, field, label(field), rowListener));
    }

    private void addToggle(String field) {
        addRow(LayoutValueRow.toggle(this, field, label(field), rowListener));
    }

    private void addAction(String key, CharSequence title) {
        addRow(LayoutValueRow.action(this, key, title, rowListener));
    }

    private void addRow(LayoutValueRow row) {
        rows.put(row.fieldKey(), row);
        row.setId(android.view.View.generateViewId());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        rowsContainer.addView(row, params);
    }

    private void adjust(LayoutValueRow row, int delta) {
        String key = row.fieldKey();
        if (ConfigFields.LED_GAP_MODE.equals(key)) {
            cycleGapMode(delta);
        } else if (ConfigFields.LED_REVERSE.equals(key)) {
            toggleBoolean(key);
        } else {
            stepNumber(key, delta);
        }
    }

    private void stepNumber(String field, int delta) {
        if (ConfigFields.WLED_PHYSICAL_LEDS.equals(field) && delta > 0
                && intValueOr(controller.draft().get(field), -1) == 0) {
            // 0 means "not confirmed yet". The first Right press starts from the
            // effective ring instead of making the user step from 1.
            ValidationResult<LayoutDraft> layout = controller.validateLayout();
            if (layout.isValid()) {
                int base = Math.max(1, layout.value().effectiveLedCount());
                ValidationResult<LayoutDraft> result = controller.setLayoutField(
                        field, Integer.toString(base + delta - 1));
                if (result.isValid()) {
                    refreshAll();
                    return;
                }
            }
        }
        boolean accepted = controller.adjustLayoutField(field, delta);
        if (!accepted && Math.abs(delta) > 1) {
            // The accelerated jump can overshoot a bound while the 1-step value
            // is still legal; fall back so holding the key keeps moving.
            accepted = controller.adjustLayoutField(field, Integer.signum(delta));
        }
        if (accepted) {
            refreshAll();
        } else {
            toast(R.string.layout_editor_limit);
            refreshRow(rows.get(field));
        }
    }

    private static int intValueOr(String raw, int fallback) {
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void cycleGapMode(int delta) {
        int current = gapIndex(controller.draft().get(ConfigFields.LED_GAP_MODE));
        int next = Math.floorMod(current + delta, GAP_VALUES.length);
        ValidationResult<LayoutDraft> result =
                controller.setLayoutField(ConfigFields.LED_GAP_MODE, GAP_VALUES[next]);
        if (result.isValid()) {
            refreshAll();
        } else {
            showError(result.errors().get(0));
        }
    }

    private void toggleBoolean(String field) {
        boolean next = !Boolean.parseBoolean(controller.draft().get(field));
        ValidationResult<LayoutDraft> result =
                controller.setLayoutField(field, Boolean.toString(next));
        if (result.isValid()) {
            refreshAll();
        } else {
            showError(result.errors().get(0));
        }
    }

    private void select(LayoutValueRow row) {
        String key = row.fieldKey();
        switch (key) {
            case ACTION_AUTO:
                autoDistribute();
                break;
            case ACTION_PRESET_274:
                apply274Preset();
                break;
            case ACTION_PROBE_TOP:
                probe(Side.TOP);
                break;
            case ACTION_PROBE_RIGHT:
                probe(Side.RIGHT);
                break;
            case ACTION_PROBE_BOTTOM:
                probe(Side.BOTTOM);
                break;
            case ACTION_PROBE_LEFT:
                probe(Side.LEFT);
                break;
            case ACTION_PROBE_INPUT:
                probe(null);
                break;
            case ACTION_PREVIEW:
                openPreview();
                break;
            case ACTION_TEST:
                openTestPattern();
                break;
            case ConfigFields.LED_GAP_MODE:
                cycleGapMode(1);
                break;
            case ConfigFields.LED_REVERSE:
                toggleBoolean(key);
                break;
            default:
                showExactDialog(key);
                break;
        }
    }

    private void autoDistribute() {
        ValidationResult<LayoutDraft> validated = controller.validateLayout();
        if (!validated.isValid()) {
            showError(validated.errors().get(0));
            return;
        }
        LayoutDraft layout = validated.value();
        if (!layout.physicalConfirmed()) {
            toast(R.string.layout_editor_auto_needs_count);
            return;
        }
        if (layout.physicalLedCount() > 4 * ConfigValidator.MAX_LEDS_PER_SIDE) {
            toast(R.string.layout_editor_auto_too_many);
            return;
        }
        int[] sides = LayoutAutoDistribute.distribute(
                layout.physicalLedCount(), captureWidth, captureHeight);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(ConfigFields.LED_TOP,
                Integer.toString(sides[LayoutAutoDistribute.TOP]));
        values.put(ConfigFields.LED_BOTTOM,
                Integer.toString(sides[LayoutAutoDistribute.BOTTOM]));
        values.put(ConfigFields.LED_LEFT,
                Integer.toString(sides[LayoutAutoDistribute.LEFT]));
        values.put(ConfigFields.LED_RIGHT,
                Integer.toString(sides[LayoutAutoDistribute.RIGHT]));
        ValidationResult<LayoutDraft> result = controller.setLayoutFields(values);
        if (result.isValid()) {
            refreshAll();
            toast(R.string.layout_editor_auto_applied);
        } else {
            showError(result.errors().get(0));
        }
    }

    private void apply274Preset() {
        ValidationResult<LayoutDraft> result =
                controller.setLayoutFields(FirstUnitPreset.layoutValues());
        if (result.isValid()) {
            refreshAll();
            toast(com.abrenoch.wledgrabber.common.R.string.wled_layout_preset_loaded);
        } else {
            showError(result.errors().get(0));
        }
    }

    private void probe(Side side) {
        String host = controller.draft().get(ConfigFields.WLED_HOST);
        if (host == null || host.trim().isEmpty()) {
            toast(R.string.layout_editor_configure_device);
            return;
        }
        ValidationResult<LayoutDraft> validated = controller.validateLayout();
        if (!validated.isValid()) {
            showError(validated.errors().get(0));
            return;
        }
        LayoutDraft layout = validated.value();
        if (!layout.physicalConfirmed()
                || layout.physicalLedCount() < layout.effectiveLedCount()) {
            toast(getString(com.abrenoch.wledgrabber.common.R.string
                            .wled_layout_physical_too_small,
                    layout.physicalLedCount(), layout.effectiveLedCount()));
            return;
        }
        ValidationResult<CompiledLayout> compiled = LayoutDraftCompiler.compileValidated(
                layout, captureWidth, captureHeight);
        if (!compiled.isValid()) {
            showError(compiled.errors().get(0));
            return;
        }
        ValidationResult<ConfigSnapshot> snapshot = controller.validate();
        if (!snapshot.isValid()) {
            FieldError error = snapshot.errors().get(0);
            if (isDeviceField(error.field())) {
                toast(R.string.layout_editor_configure_device);
            } else {
                showError(error);
            }
            return;
        }
        if (!new ConfigValidator().validateForStart(snapshot.value()).isValid()) {
            toast(R.string.layout_editor_configure_device);
            return;
        }
        int[] logical = side == null
                ? new int[]{LayoutProbe.inputLogical(layout.layout())}
                : LayoutProbe.logicalForSide(layout.layout(), side);
        if (logical.length == 0) {
            toast(R.string.layout_editor_no_side_leds);
            return;
        }
        if (LayoutProbeSender.pulse(snapshot.value(), compiled.value(), logical)) {
            toast(getString(com.abrenoch.wledgrabber.common.R.string.wled_probe_sent,
                    logical.length));
        } else {
            toast(com.abrenoch.wledgrabber.common.R.string.wled_probe_busy);
        }
    }

    private void openPreview() {
        if (save()) {
            startActivity(new Intent(this, LayoutPreviewActivity.class));
        }
    }

    private void openTestPattern() {
        if (save()) {
            startActivity(new Intent(this, TestPatternActivity.class));
        }
    }

    private boolean save() {
        ValidationResult<LayoutDraft> validated = controller.validateLayout();
        if (!validated.isValid()) {
            showError(validated.errors().get(0));
            return false;
        }
        LayoutDraft layout = validated.value();
        if (!layout.physicalConfirmed()) {
            toast(com.abrenoch.wledgrabber.common.R.string.wled_setup_invalid_led_count);
            return false;
        }
        if (layout.physicalLedCount() < layout.effectiveLedCount()) {
            toast(getString(com.abrenoch.wledgrabber.common.R.string
                            .wled_layout_physical_too_small,
                    layout.physicalLedCount(), layout.effectiveLedCount()));
            return false;
        }
        // The editor already shows "layout geometry" errors from the preview;
        // refuse to persist a draft the service's compiler will reject.
        ValidationResult<CompiledLayout> compiled = LayoutDraftCompiler.compileValidated(
                layout, captureWidth, captureHeight);
        if (!compiled.isValid()) {
            showError(compiled.errors().get(0));
            return false;
        }
        ValidationResult<ConfigSnapshot> saved = controller.save();
        if (!saved.isValid()) {
            showError(saved.errors().get(0));
            return false;
        }
        toast(R.string.layout_editor_saved);
        return true;
    }

    private void saveAndFinish() {
        if (save()) {
            setResult(RESULT_OK);
            finish();
        }
    }

    private void refreshAll() {
        for (LayoutValueRow row : rows.values()) {
            refreshRow(row);
        }
        ValidationResult<LayoutDraft> validated = controller.validateLayout();
        if (!validated.isValid()) {
            FieldError error = validated.errors().get(0);
            String message = describe(error);
            status.setText(message);
            preview.setError(message);
            return;
        }
        ValidationResult<CompiledLayout> compiled = LayoutDraftCompiler.compileValidated(
                validated.value(), captureWidth, captureHeight);
        if (!compiled.isValid()) {
            FieldError error = compiled.errors().get(0);
            String message = describe(error);
            status.setText(message);
            preview.setError(message);
            return;
        }
        preview.setLayout(compiled.value(), 0);
        status.setText(statusText(validated.value(), compiled.value()));
    }

    private void refreshRow(LayoutValueRow row) {
        if (row == null || row.kind() == LayoutValueRow.Kind.ACTION) {
            return;
        }
        String field = row.fieldKey();
        String raw = controller.draft().get(field);
        if (ConfigFields.LED_GAP_MODE.equals(field)) {
            row.setDisplayValue(gapLabel(raw));
        } else if (ConfigFields.LED_REVERSE.equals(field)) {
            row.setDisplayValue(getString(Boolean.parseBoolean(raw)
                    ? R.string.layout_editor_on : R.string.layout_editor_off));
        } else {
            row.setDisplayValue(formatNumber(field, raw));
        }
    }

    private CharSequence statusText(LayoutDraft layout, CompiledLayout compiled) {
        int total = layout.layout().totalLedCount();
        int effective = layout.effectiveLedCount();
        String text;
        if (!layout.physicalConfirmed()) {
            text = getString(R.string.layout_editor_status_unconfirmed, total, effective);
        } else {
            int tail = Math.max(0, layout.physicalLedCount() - effective);
            text = getString(R.string.layout_editor_status, total, effective, tail);
            if (layout.physicalLedCount() < effective) {
                text += "\n" + getString(com.abrenoch.wledgrabber.common.R.string
                        .wled_layout_physical_too_small,
                        layout.physicalLedCount(), effective);
            }
        }
        int physicalZeroLogical = compiled.mapping().physicalToLogical(0);
        text += "\n" + getString(R.string.layout_editor_input_map,
                layout.layout().inputPosition(), physicalZeroLogical);
        return text;
    }

    private void showExactDialog(String field) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | (ConfigFields.LED_INPUT_POSITION.equals(field)
                ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0));
        String raw = controller.draft().get(field);
        input.setText(raw == null ? "" : raw);
        input.setSelectAllOnFocus(true);
        input.setHint(R.string.layout_editor_exact_hint);
        int horizontal = dp(24);
        input.setPadding(horizontal, dp(8), horizontal, dp(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.layout_editor_exact_title, label(field)))
                .setView(input)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        input.setError(getString(R.string.layout_editor_error_empty));
                        return;
                    }
                    if (!value.matches("-?\\d+")) {
                        input.setError(getString(R.string.layout_editor_error_number));
                        return;
                    }
                    ValidationResult<LayoutDraft> result =
                            controller.setLayoutField(field, value);
                    if (!result.isValid()) {
                        FieldError error = result.errorFor(field) != null
                                ? result.errorFor(field) : result.errors().get(0);
                        input.setError(describe(error));
                        return;
                    }
                    dialog.dismiss();
                    refreshAll();
                }));
        dialog.show();
    }

    private String formatNumber(String field, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return EMPTY_VALUE;
        }
        return isPercentField(field) ? raw + PERCENT : raw;
    }

    private String gapLabel(String raw) {
        int index = gapIndex(raw);
        return index == 1
                ? getString(com.abrenoch.wledgrabber.common.R.string.wled_layout_gap_black)
                : getString(com.abrenoch.wledgrabber.common.R.string.wled_layout_gap_skip);
    }

    private static int gapIndex(String raw) {
        if (raw != null && "black".equalsIgnoreCase(raw.trim())) {
            return 1;
        }
        return 0;
    }

    private String label(String field) {
        if (ConfigFields.LAYOUT_GEOMETRY.equals(field)) {
            return getString(R.string.layout_editor_geometry);
        }
        return ConfigFieldLabels.label(this, field);
    }

    private String describe(FieldError error) {
        return label(error.field()) + ": " + errorText(error);
    }

    private String errorText(FieldError error) {
        switch (error.code()) {
            case EMPTY:
                return getString(R.string.layout_editor_error_empty);
            case NOT_A_NUMBER:
                return getString(R.string.layout_editor_error_number);
            case OUT_OF_RANGE:
                return getString(R.string.layout_editor_error_range);
            case CROSS_FIELD:
                return getString(R.string.layout_editor_error_cross);
            default:
                return getString(R.string.layout_editor_error_invalid);
        }
    }

    private void showError(FieldError error) {
        String message = describe(error);
        status.setText(message);
        toast(message);
    }

    private void toast(int messageRes) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
    }

    private void toast(CharSequence message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static boolean isPercentField(String field) {
        for (String candidate : PERCENT_FIELDS) {
            if (candidate.equals(field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDeviceField(String field) {
        return ConfigFields.WLED_HOST.equals(field)
                || ConfigFields.WLED_HTTP_PORT.equals(field)
                || ConfigFields.WLED_UDP_PORT.equals(field)
                || ConfigFields.WLED_DDP_PORT.equals(field)
                || ConfigFields.WLED_PROTOCOL.equals(field)
                || ConfigFields.WLED_RGBW_MODE.equals(field);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
