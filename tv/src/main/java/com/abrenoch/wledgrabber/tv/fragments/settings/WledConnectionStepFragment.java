package com.abrenoch.wledgrabber.tv.fragments.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.leanback.widget.GuidedAction;

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
import com.abrenoch.wledgrabber.tv.activities.LayoutSettingsActivity;

import java.util.List;
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
    private static final long PHYSICAL_LEDS = 1004L;
    private static final long PROTOCOL = 1005L;
    private static final long RGBW = 1006L;
    private static final long ACTION_TEST = 1007L;
    private static final long ACTION_LAYOUT = 1008L;
    private static final long SET_PROTOCOL = 2001;
    private static final long SET_RGBW = 2002;

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
        actions.add(unSignedNumberAction(PHYSICAL_LEDS, getString(R.string.wled_setup_physical_leds),
                controller.draft().get(ConfigFields.WLED_PHYSICAL_LEDS)));

        String[] protocols = {"auto", "dnrgb", "ddp"};
        actions.add(radioListAction(PROTOCOL, getString(R.string.wled_setup_protocol), null,
                (int) SET_PROTOCOL, protocols, protocols, protocol));
        String[] rgbwModes = {"unconfirmed", "rgb", "rgbw"};
        actions.add(radioListAction(RGBW, getString(R.string.wled_setup_rgbw), null,
                (int) SET_RGBW, rgbwModes, rgbwModes, rgbw));

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
            save();
            return;
        }
        if (action.getId() == ACTION_LAYOUT) {
            startActivity(new android.content.Intent(requireContext(), LayoutSettingsActivity.class));
            return;
        }
        if (action.getId() == ACTION_TEST) {
            testConnection();
            return;
        }
        super.onGuidedActionClicked(action);
    }

    private void save() {
        try {
            String host = assertStringValue(HOST);
            int httpPort = assertIntValue(HTTP_PORT);
            int udpPort = assertIntValue(UDP_PORT);
            int ddpPort = assertIntValue(DDP_PORT);
            int physical = assertIntValue(PHYSICAL_LEDS);
            String protocol = assertSubActionValue(PROTOCOL, String.class);
            String rgbw = assertSubActionValue(RGBW, String.class);
            if (physical < 1) {
                showToast(getString(R.string.wled_setup_invalid_led_count));
                return;
            }
            if ("unconfirmed".equals(rgbw)) {
                showToast(getString(R.string.wled_setup_invalid_rgbw));
                return;
            }
            controller.set(ConfigFields.WLED_HOST, host);
            controller.set(ConfigFields.WLED_HTTP_PORT, Integer.toString(httpPort));
            controller.set(ConfigFields.WLED_UDP_PORT, Integer.toString(udpPort));
            controller.set(ConfigFields.WLED_DDP_PORT, Integer.toString(ddpPort));
            controller.set(ConfigFields.WLED_PHYSICAL_LEDS, Integer.toString(physical));
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
            startActivity(new android.content.Intent(requireContext(), LayoutSettingsActivity.class));
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
