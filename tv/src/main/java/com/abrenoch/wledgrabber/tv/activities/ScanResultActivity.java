package com.abrenoch.wledgrabber.tv.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.network.wled.ChannelHint;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.ValidationResult;
import com.abrenoch.wledgrabber.tv.R;
import com.abrenoch.wledgrabber.tv.databinding.ActivityScanResultBinding;

import java.util.Random;

import nl.dionsegijn.konfetti.models.Shape;
import nl.dionsegijn.konfetti.models.Size;

/** Confirms a WLED discovery result and saves it through the shared config repository. */
public class ScanResultActivity extends LeanbackActivity {
    public static final String EXTRA_RESULT_HOST_NAME = "EXTRA_RESULT_HOST_NAME";
    public static final String EXTRA_RESULT_HTTP_PORT = "EXTRA_RESULT_HTTP_PORT";
    public static final String EXTRA_RESULT_UDP_PORT = "EXTRA_RESULT_UDP_PORT";
    public static final String EXTRA_RESULT_COUNT = "EXTRA_RESULT_COUNT";
    public static final String EXTRA_RESULT_VERSION = "EXTRA_RESULT_VERSION";
    public static final String EXTRA_RESULT_HINT = "EXTRA_RESULT_HINT";

    private ActivityScanResultBinding binding;
    private String hostName;
    private int httpPort = 80;
    private int udpPort = 21324;
    private int observedCount;
    private String version = "";
    private ChannelHint hint = ChannelHint.UNKNOWN;

    private final int ORANGE = Color.rgb(253, 104, 40);
    private final int[] COLORS = {Color.RED, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, ORANGE};
    private final int BURST_COLOR_SPREAD = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanResultBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.scanResultEmojiText.setText("👏");
        String partyPopper = "🎉";
        Bundle extras = getIntent().getExtras();
        if (extras == null || extras.getString(EXTRA_RESULT_HOST_NAME) == null) {
            binding.scanResultHostName.setText(
                    com.abrenoch.wledgrabber.common.R.string.error_no_host_name_extra);
            binding.scanResultDescriptionText.setText(
                    com.abrenoch.wledgrabber.common.R.string.error_no_host_name_extra);
        } else {
            hostName = extras.getString(EXTRA_RESULT_HOST_NAME);
            httpPort = extras.getInt(EXTRA_RESULT_HTTP_PORT, 80);
            udpPort = extras.getInt(EXTRA_RESULT_UDP_PORT, 21324);
            observedCount = extras.getInt(EXTRA_RESULT_COUNT, 0);
            version = extras.getString(EXTRA_RESULT_VERSION, "");
            try {
                hint = ChannelHint.valueOf(extras.getString(EXTRA_RESULT_HINT,
                        ChannelHint.UNKNOWN.name()));
            } catch (IllegalArgumentException ignored) {
                hint = ChannelHint.UNKNOWN;
            }
            binding.scanResultHostName.setText(hostName + ":" + httpPort);
            binding.scanResultDescriptionText.setText(
                    getResources().getString(
                            com.abrenoch.wledgrabber.common.R.string.scan_result_description,
                            partyPopper)
                            + "\n" + version + " · LEDs=" + observedCount + " · channel=" + hint);
        }

        binding.konfetti.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (hostName != null) startKonfetti();
                binding.konfetti.removeOnLayoutChangeListener(this);
            }
        });
    }

    public void onClick(View v) {
        if (v.getId() == R.id.confirmButton) {
            if (saveResult()) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            }
        } else if (v.getId() == R.id.changeColorButton) {
            burstKonfetti(COLORS[new Random().nextInt(COLORS.length)]);
        }
    }

    private boolean saveResult() {
        if (hostName == null) {
            return false;
        }
        SettingsDraftController controller = new SettingsDraftController(
                new ConfigRepository(new PreferencesConfigStorage(new Preferences(this)),
                        new ConfigValidator()));
        controller.load();
        controller.set(ConfigFields.WLED_HOST, hostName);
        controller.set(ConfigFields.WLED_HTTP_PORT, Integer.toString(httpPort));
        controller.set(ConfigFields.WLED_UDP_PORT, Integer.toString(udpPort));
        controller.set(ConfigFields.WLED_DDP_PORT, "4048");
        controller.set(ConfigFields.WLED_PHYSICAL_LEDS, Integer.toString(observedCount));
        controller.set(ConfigFields.WLED_PROTOCOL, "auto");
        if (hint == ChannelHint.RGBW) {
            controller.set(ConfigFields.WLED_RGBW_MODE, "rgbw");
        } else if (hint == ChannelHint.RGB) {
            controller.set(ConfigFields.WLED_RGBW_MODE, "rgb");
        } else {
            controller.set(ConfigFields.WLED_RGBW_MODE, "unconfirmed");
        }
        ValidationResult<ConfigSnapshot> result = controller.save();
        if (!result.isValid()) {
            binding.scanResultDescriptionText.setText("Check settings: "
                    + result.errors().get(0).field() + ":"
                    + result.errors().get(0).code());
            return false;
        }
        return true;
    }

    private void startKonfetti() {
        binding.konfetti.build()
                .addColors(Color.YELLOW, Color.GREEN, Color.MAGENTA)
                .setDirection(0.0, 359.0)
                .setSpeed(1f, 5f)
                .setFadeOutEnabled(true)
                .setTimeToLive(400L)
                .addShapes(Shape.RECT, Shape.CIRCLE)
                .addSizes(new Size(12, 5))
                .setPosition(-50f, binding.konfetti.getWidth() + 50f, -50f, -50f)
                .streamFor(60, 1000L);
    }

    private void burstKonfetti(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        Random random = new Random();
        int[] variations = new int[10];
        int halfSpread = BURST_COLOR_SPREAD / 2;
        for (int i = 0; i < variations.length; i++) {
            variations[i] = Color.rgb(
                    colorComponentClamp(r - halfSpread + random.nextInt(BURST_COLOR_SPREAD)),
                    colorComponentClamp(g - halfSpread + random.nextInt(BURST_COLOR_SPREAD)),
                    colorComponentClamp(b - halfSpread + random.nextInt(BURST_COLOR_SPREAD))
            );
        }

        float posX = (float) (Math.random() * binding.konfetti.getWidth());
        float posY = (float) (Math.random() * binding.konfetti.getHeight());

        binding.konfetti.build()
                .addColors(variations)
                .setDirection(0.0, 359.0)
                .setSpeed(1f, 5f)
                .setFadeOutEnabled(true)
                .setTimeToLive(400L)
                .addShapes(Shape.RECT, Shape.CIRCLE)
                .addSizes(new Size(12, 5))
                .setPosition(posX, posY)
                .burst(100);
    }

    private static int colorComponentClamp(int value) {
        return Math.min(255, Math.max(0, value));
    }
}
