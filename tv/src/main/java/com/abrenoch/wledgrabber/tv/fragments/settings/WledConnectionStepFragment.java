package com.abrenoch.wledgrabber.tv.fragments.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.leanback.widget.GuidedAction;

import com.abrenoch.wledgrabber.common.WledGrabberApp;
import com.abrenoch.wledgrabber.common.R;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.network.wled.Capabilities;
import com.abrenoch.wledgrabber.common.network.wled.ProbeResult;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.ValidationResult;
import com.abrenoch.wledgrabber.tv.activities.LayoutEditorActivity;
import com.abrenoch.wledgrabber.tv.activities.LayoutSettingsActivity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TV-1 manual WLED connection step. Probe runs on a background worker; the
 * GuidedStep never performs network I/O or saves a half configuration.
 */
public class WledConnectionStepFragment extends SettingsStepBaseFragment {
    private static final long HOST = 1000L;
    private static final long HTTP_PORT = 1001L;
    private static final long UDP_PORT = 1002L;
    private static final long DDP_PORT = 1003L;
    private static final long PROTOCOL = 1005L;
    private static final long RGBW = 1006L;
    private static final long ACTION_TEST = 1007L;
    private static final long ACTION_LAYOUT = 1008L;
    private static final long ACTION_ADVANCED = 1011L;
    /** Public for the merge regression test in the TV test module. */
    public static final int REQUEST_LAYOUT = 4201;
    public static final int REQUEST_ADVANCED = 4202;
    private static final long FRAME_RATE = 1009L;
    private static final long LANGUAGE = 1010L;
    private static final long SET_PROTOCOL = 2001;
    private static final long SET_RGBW = 2002;
    private static final long SET_LANGUAGE = 2003;

    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor();
    private SettingsDraftController controller;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        // GuidedStepSupportFragment.onCreate() invokes onCreateActions(), so the
        // draft controller must exist before super.onCreate().
        controller = new SettingsDraftController(new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(requireContext())),
                new ConfigValidator()));
        controller.load();
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, @Nullable Bundle savedInstanceState) {
        // Direct entry so layout/LED-count settings are reachable without first
        // saving this step; the layout page owns physical LEDs, T/B/L/R, gap,
        // input position and reverse.
        actions.add(new GuidedAction.Builder(requireContext())
                .id(ACTION_LAYOUT)
                .title(getString(R.string.wled_setup_layout_entry))
                .description(getString(R.string.wled_setup_layout_entry_desc))
                .build());
        actions.add(new GuidedAction.Builder(requireContext())
                .id(ACTION_ADVANCED)
                .title(getString(com.abrenoch.wledgrabber.tv.R.string.layout_editor_advanced_entry))
                .description(getString(
                        com.abrenoch.wledgrabber.tv.R.string.layout_editor_advanced_entry_desc))
                .build());

        String host = controller.draft().get(ConfigFields.WLED_HOST);
        String protocol = controller.draft().get(ConfigFields.WLED_PROTOCOL);
        String rgbw = controller.draft().get(ConfigFields.WLED_RGBW_MODE);

        actions.add(new GuidedAction.Builder(requireContext())
                .id(HOST)
                .title(getString(R.string.wled_setup_host))
                .description(host == null ? "" : host)
                .descriptionEditable(true)
                .build());
        actions.add(unSignedNumberAction(HTTP_PORT, getString(R.string.wled_setup_http_port),
                controller.draft().get(ConfigFields.WLED_HTTP_PORT)));
        actions.add(unSignedNumberAction(UDP_PORT, getString(R.string.wled_setup_udp_port),
                controller.draft().get(ConfigFields.WLED_UDP_PORT)));
        actions.add(unSignedNumberAction(DDP_PORT, getString(R.string.wled_setup_ddp_port),
                controller.draft().get(ConfigFields.WLED_DDP_PORT)));
        actions.add(unSignedNumberAction(FRAME_RATE, getString(R.string.field_framerate),
                controller.draft().get(ConfigFields.FRAME_RATE)));

        String[] protocols = {"auto", "dnrgb", "ddp"};
        actions.add(radioListAction(PROTOCOL, getString(R.string.wled_setup_protocol), null,
                (int) SET_PROTOCOL, protocols, protocols, protocol));
        String[] rgbwModes = {"unconfirmed", "rgb", "rgbw"};
        actions.add(radioListAction(RGBW, getString(R.string.wled_setup_rgbw), null,
                (int) SET_RGBW, rgbwModes, rgbwModes, rgbw));

        String[] languageValues = {WledGrabberApp.LANGUAGE_SYSTEM,
                WledGrabberApp.LANGUAGE_ZH, WledGrabberApp.LANGUAGE_EN};
        String[] languageLabels = {getString(R.string.language_system),
                getString(R.string.language_chinese), getString(R.string.language_english)};
        actions.add(radioListAction(LANGUAGE, getString(R.string.settings_language), null,
                (int) SET_LANGUAGE, languageLabels, languageValues,
                WledGrabberApp.currentLanguage(requireContext())));

        actions.add(new GuidedAction.Builder(requireContext())
                .id(ACTION_TEST)
                .title(getString(R.string.wled_setup_test))
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
            applyLanguage();
            save();
            return;
        }
        if (action.getId() == ACTION_LAYOUT) {
            startActivityForResult(new android.content.Intent(
                    requireContext(), LayoutEditorActivity.class), REQUEST_LAYOUT);
            return;
        }
        if (action.getId() == ACTION_ADVANCED) {
            startActivityForResult(new android.content.Intent(
                    requireContext(), LayoutSettingsActivity.class), REQUEST_ADVANCED);
            return;
        }
        if (action.getId() == ACTION_TEST) {
            testConnection();
            return;
        }
        super.onGuidedActionClicked(action);
    }

    /**
     * A child page writes the whole config through its own repository. Without
     * merging on return, the next Save here would persist this fragment's stale
     * draft and silently undo the child's layout/advanced changes.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != android.app.Activity.RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_LAYOUT || requestCode == REQUEST_ADVANCED) {
            mergeChildSave();
        }
    }

    /**
     * Reloads the committed snapshot, then re-applies only the fields this step
     * owns from the current UI. The layout editor owns the physical count and the
     * layout fields; the advanced page owns white mode/capture/quality. Both
     * child pages save the whole config, so this step must not write its stale
     * draft over them.
     */
    private void mergeChildSave() {
        Map<String, String> own = new LinkedHashMap<>();
        own.put(ConfigFields.WLED_HOST,
                description(HOST, controller.draft().get(ConfigFields.WLED_HOST)));
        own.put(ConfigFields.WLED_HTTP_PORT,
                description(HTTP_PORT, controller.draft().get(ConfigFields.WLED_HTTP_PORT)));
        own.put(ConfigFields.WLED_UDP_PORT,
                description(UDP_PORT, controller.draft().get(ConfigFields.WLED_UDP_PORT)));
        own.put(ConfigFields.WLED_DDP_PORT,
                description(DDP_PORT, controller.draft().get(ConfigFields.WLED_DDP_PORT)));
        own.put(ConfigFields.FRAME_RATE,
                description(FRAME_RATE, controller.draft().get(ConfigFields.FRAME_RATE)));
        own.put(ConfigFields.WLED_PROTOCOL,
                selectedValue(PROTOCOL, controller.draft().get(ConfigFields.WLED_PROTOCOL)));
        own.put(ConfigFields.WLED_RGBW_MODE,
                selectedValue(RGBW, controller.draft().get(ConfigFields.WLED_RGBW_MODE)));

        controller.load();
        for (Map.Entry<String, String> entry : own.entrySet()) {
            controller.set(entry.getKey(), entry.getValue());
        }
    }

    private String description(long actionId, String fallback) {
        GuidedAction action = findActionById(actionId);
        if (action == null || action.getDescription() == null) {
            return fallback == null ? "" : fallback;
        }
        return action.getDescription().toString();
    }

    private String selectedValue(long actionId, String fallback) {
        GuidedAction action = findActionById(actionId);
        if (action != null && action.getSubActions() != null) {
            for (GuidedAction sub : action.getSubActions()) {
                if (!sub.isChecked()) {
                    continue;
                }
                if (sub instanceof SettingsStepBaseFragment.ValueGuidedAction) {
                    Object value = ((SettingsStepBaseFragment.ValueGuidedAction) sub)
                            .getValue();
                    return value == null ? fallback : value.toString();
                }
                return sub.getTitle() == null ? fallback : sub.getTitle().toString();
            }
        }
        return fallback;
    }

    /** Language is a UI preference, so it applies on Save together with the config. */
    private void applyLanguage() {
        try {
            String selected = assertSubActionValue(LANGUAGE, String.class);
            if (!selected.equals(WledGrabberApp.currentLanguage(requireContext()))) {
                WledGrabberApp.setLanguage(requireContext(), selected);
                requireActivity().recreate();
            }
        } catch (AssertionError ignored) {
            // No selection yet; keep the current language.
        }
    }

    private void save() {
        try {
            String host = assertStringValue(HOST);
            int httpPort = assertIntValue(HTTP_PORT);
            int udpPort = assertIntValue(UDP_PORT);
            int ddpPort = assertIntValue(DDP_PORT);
            int frameRate = assertIntValue(FRAME_RATE);
            String protocol = assertSubActionValue(PROTOCOL, String.class);
            String rgbw = assertSubActionValue(RGBW, String.class);
            if ("unconfirmed".equals(rgbw)) {
                showToast(getString(R.string.wled_setup_invalid_rgbw));
                return;
            }
            controller.set(ConfigFields.WLED_HOST, host);
            controller.set(ConfigFields.WLED_HTTP_PORT, Integer.toString(httpPort));
            controller.set(ConfigFields.WLED_UDP_PORT, Integer.toString(udpPort));
            controller.set(ConfigFields.WLED_DDP_PORT, Integer.toString(ddpPort));
            controller.set(ConfigFields.FRAME_RATE, Integer.toString(frameRate));
            controller.set(ConfigFields.WLED_PROTOCOL, protocol);
            controller.set(ConfigFields.WLED_RGBW_MODE, rgbw);

            ValidationResult<ConfigSnapshot> result = controller.save();
            if (!result.isValid()) {
                showToast(getString(R.string.wled_setup_invalid) + " "
                        + result.errors().get(0).field() + ":"
                        + result.errors().get(0).code());
                return;
            }
            showToast(getString(R.string.wled_setup_saved));
            startActivity(new android.content.Intent(requireContext(), LayoutEditorActivity.class));
            finishGuidedStepSupportFragments();
        } catch (AssertionError error) {
            showToast(getString(R.string.wled_setup_invalid));
        }
    }

    private void testConnection() {
        String host;
        int httpPort;
        try {
            host = assertStringValue(HOST);
            httpPort = assertIntValue(HTTP_PORT);
        } catch (AssertionError error) {
            showToast(getString(R.string.wled_setup_invalid));
            return;
        }
        probeExecutor.execute(() -> {
            ProbeResult result;
            try (WledHttpClient client = new WledHttpClient()) {
                result = client.probe(host, httpPort);
            }
            final String message;
            if (result.isSuccess()) {
                Capabilities capabilities = result.device().capabilities();
                message = result.device().firmwareVersion()
                        + " LEDs=" + result.device().observedLedCount()
                        + " channel=" + (capabilities == null ? "?" : capabilities.channelHint());
            } else {
                message = String.valueOf(result.errorType());
            }
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        showToast(message);
                    }
                });
            }
        });
    }

    @Override
    public void onDestroy() {
        probeExecutor.shutdownNow();
        super.onDestroy();
    }
}
