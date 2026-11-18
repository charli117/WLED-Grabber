package com.abrenoch.wledgrabber.common.views;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.abrenoch.wledgrabber.common.capture.FramePool;
import com.abrenoch.wledgrabber.common.capture.LedStreamer;
import com.abrenoch.wledgrabber.common.capture.LedTestPattern;
import com.abrenoch.wledgrabber.common.capture.OutputOwner;
import com.abrenoch.wledgrabber.common.capture.OutputSessionCoordinator;
import com.abrenoch.wledgrabber.common.capture.TestPatternRunner;
import com.abrenoch.wledgrabber.common.capture.ScreenOffReceiver;
import com.abrenoch.wledgrabber.common.capture.TestPatternSession;
import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.network.wled.StateReadResult;
import com.abrenoch.wledgrabber.common.network.wled.UdpDatagramSink;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.network.wled.WledFrameSink;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

/** Shared 10-second TEST host using the single-writer streamer and fixed transport. */
public class TestPatternActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(
                com.abrenoch.wledgrabber.common.util.LocaleHelper.wrap(newBase));
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ScreenOffReceiver powerReceiver = new ScreenOffReceiver(this::cancelIfRunning);
    private TextView status;
    private TestPatternRunner runner;
    private boolean started;
    private static final String OUTPUT_OWNER_ID = "test-pattern";

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (runner == null || !started) {
                return;
            }
            long now = System.currentTimeMillis();
            TestPatternSession.State state = runner.tick(now);
            status.setText("physical=" + runner.ledCount()
                    + " remaining=" + runner.remainingMillis(now) + "ms");
            if (state == TestPatternSession.State.RUNNING) {
                handler.postDelayed(this, 33);
            } else {
                finishOutput();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        root.addView(status);
        setContentView(root);
        powerReceiver.register(this);
        if (savedInstanceState != null || !ScreenOffReceiver.isAwake(this)) {
            status.setText(com.abrenoch.wledgrabber.common.R.string.wled_test_reopen);
            return;
        }

        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(this)), new ConfigValidator());
        ValidationResult<ConfigSnapshot> loaded = repository.load();
        ConfigSnapshot snapshot = repository.committedSnapshot();
        if (!loaded.isValid() || snapshot == null
                || !new ConfigValidator().validateForStart(snapshot).isValid()) {
            status.setText("Configure a confirmed WLED device before TEST.");
            return;
        }

        int physical = snapshot.device().physicalLedCount();
        ValidationResult<CompiledLayout> compiled = new ClassicLedLayout().compile(
                snapshot.layout(), physical, 640, 360);
        if (!compiled.isValid()) {
            status.setText("Layout invalid: " + compiled.errors().get(0));
            return;
        }

        int channels = snapshot.device().rgbwMode() == RgbwMode.RGBW ? 4 : 3;
        int port = channels == 4 || snapshot.device().protocol() == Protocol.DDP
                ? snapshot.device().ddpPort() : snapshot.device().udpPort();
        OutputSessionCoordinator outputCoordinator = OutputSessionCoordinator.get();
        if (!outputCoordinator.acquire(OutputOwner.TEST, OUTPUT_OWNER_ID)) {
            status.setText("Another capture/test session owns the output.");
            return;
        }
        long outputEpoch = outputCoordinator.epoch();
        final UdpDatagramSink udp;
        try {
            udp = new UdpDatagramSink(snapshot.device().host(), port);
        } catch (Exception e) {
            status.setText("UDP endpoint failed: " + e.getMessage());
            outputCoordinator.release(OutputOwner.TEST, OUTPUT_OWNER_ID, outputEpoch);
            return;
        }
        FramePool pool = new FramePool(physical * channels);
        final String host = snapshot.device().host();
        final int httpPort = snapshot.device().httpPort();
        final WledHttpClient control = new WledHttpClient();
        WledFrameSink frameSink = new WledFrameSink(snapshot.device(), control, udp);
        LedStreamer streamer = new LedStreamer(pool, frameSink, outputCoordinator.releaseWhenDone(
                OutputOwner.TEST, OUTPUT_OWNER_ID, outputEpoch, reason -> {
                    try (control; udp) {
                        boolean off = control.turnOff(host, httpPort);
                        StateReadResult stateRead = control.readState(host, httpPort);
                        return off && stateRead.isSuccess() && !stateRead.on() && !stateRead.live();
                    }
                }), com.abrenoch.wledgrabber.common.network.wled.WledSession.RELEASE_BUDGET_MS);
        runner = new TestPatternRunner(compiled.value(), channels,
                snapshot.device().whiteMode(), streamer, pool,
                System.currentTimeMillis(), TestPatternSession.DEFAULT_DURATION_MS);
        started = runner.start(LedTestPattern.Type.SIDES, 0, false,
                System.currentTimeMillis());
        if (!started) {
            status.setText("TEST could not start.");
            udp.close();
            control.close();
            outputCoordinator.release(OutputOwner.TEST, OUTPUT_OWNER_ID, outputEpoch);
            return;
        }
        handler.post(ticker);
    }

    private void finishOutput() {
        started = false;
        LedStreamer.StopResult stop = runner.lastStop();
        status.setText("TEST " + runner.state().name().toLowerCase(java.util.Locale.ROOT)
                + ": release " + (stop != null && stop.releaseOk() ? "CONFIRMED" : "UNCONFIRMED"));
        handler.removeCallbacks(ticker);
        // The writer owns its captured connections and lease even if stop timed out.
        runner = null;
    }

    private void cancelIfRunning() {
        if (started) {
            runner.cancel();
            finishOutput();
        }
    }

    @Override
    protected void onStop() {
        cancelIfRunning();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(powerReceiver);
        super.onDestroy();
    }
}
