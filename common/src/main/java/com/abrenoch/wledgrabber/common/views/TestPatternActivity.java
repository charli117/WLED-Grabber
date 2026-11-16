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
import com.abrenoch.wledgrabber.common.capture.TestPatternSession;
import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.network.wled.StateReadResult;
import com.abrenoch.wledgrabber.common.network.wled.UdpDatagramSink;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.network.wled.WledUdpTransport;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

/** Shared 10-second TEST host using the single-writer streamer and fixed transport. */
public class TestPatternActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TestPatternRunner runner;
    private UdpDatagramSink sink;
    private WledHttpClient httpClient;
    private String host;
    private int httpPort;
    private boolean started;
    private static final String OUTPUT_OWNER_ID = "test-pattern";
    private OutputSessionCoordinator outputCoordinator;
    private long outputEpoch;

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
                started = false;
                LedStreamer.StopResult stop = runner.lastStop();
                boolean confirmed = stop != null && stop.releaseOk();
                status.setText("TEST " + state.name().toLowerCase() + ": release "
                        + (confirmed ? "CONFIRMED" : "UNCONFIRMED"));
                closeSink();
                closeHttp();
                releaseOutput();
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

        ConfigRepository repository = new ConfigRepository(
                new PreferencesConfigStorage(new Preferences(this)), new ConfigValidator());
        ValidationResult<ConfigSnapshot> loaded = repository.load();
        ConfigSnapshot snapshot = repository.committedSnapshot();
        if (!loaded.isValid() || snapshot == null
                || snapshot.device().host() == null
                || snapshot.device().host().trim().isEmpty()
                || snapshot.device().physicalLedCount() < 1
                || snapshot.device().rgbwMode() == RgbwMode.UNCONFIRMED) {
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
        outputCoordinator = OutputSessionCoordinator.get();
        if (!outputCoordinator.acquire(OutputOwner.TEST, OUTPUT_OWNER_ID)) {
            status.setText("Another capture/test session owns the output.");
            return;
        }
        outputEpoch = outputCoordinator.epoch();
        try {
            sink = new UdpDatagramSink(snapshot.device().host(), port);
        } catch (Exception e) {
            status.setText("UDP endpoint failed: " + e.getMessage());
            releaseOutput();
            return;
        }
        final WledUdpTransport transport = new WledUdpTransport(sink);
        final int finalChannels = channels;
        LedStreamer.FrameSink frameSink = new LedStreamer.FrameSink() {
            @Override
            public boolean send(com.abrenoch.wledgrabber.common.capture.FrameLease lease) {
                if (finalChannels == 4) {
                    return transport.sendRgbw(lease.data(), lease.ledCount());
                }
                if (snapshot.device().protocol() == Protocol.DDP) {
                    return transport.sendRgbDdp(lease.data(), lease.ledCount());
                }
                return transport.sendRgb(lease.data(), lease.ledCount());
            }

            @Override
            public boolean sendBlack(int ledCount, int ledChannels) {
                byte[] black = new byte[ledCount * ledChannels];
                if (ledChannels == 4) {
                    return transport.sendRgbw(black, ledCount);
                }
                if (snapshot.device().protocol() == Protocol.DDP) {
                    return transport.sendRgbDdp(black, ledCount);
                }
                return transport.sendRgb(black, ledCount);
            }
        };

        FramePool pool = new FramePool(physical * channels);
        host = snapshot.device().host();
        httpPort = snapshot.device().httpPort();
        httpClient = new WledHttpClient();
        LedStreamer streamer = new LedStreamer(pool, frameSink, reason -> {
            boolean off = httpClient.turnOff(host, httpPort);
            StateReadResult stateRead = httpClient.readState(host, httpPort);
            return off && stateRead.isSuccess() && !stateRead.on() && !stateRead.live();
        }, 3000);
        runner = new TestPatternRunner(compiled.value(), channels,
                snapshot.device().whiteMode(), streamer, pool,
                System.currentTimeMillis(), TestPatternSession.DEFAULT_DURATION_MS);
        started = runner.start(LedTestPattern.Type.SIDES, 0, false,
                System.currentTimeMillis());
        if (!started) {
            status.setText("TEST could not start.");
            closeSink();
            closeHttp();
            releaseOutput();
            return;
        }
        handler.post(ticker);
    }

    private void releaseOutput() {
        if (outputCoordinator != null) {
            outputCoordinator.release(OutputOwner.TEST, OUTPUT_OWNER_ID, outputEpoch);
        }
    }

    private void closeSink() {
        handler.removeCallbacks(ticker);
        if (sink != null) {
            sink.close();
            sink = null;
        }
    }

    private void closeHttp() {
        if (httpClient != null) {
            httpClient.close();
            httpClient = null;
        }
    }

    @Override
    protected void onDestroy() {
        if (runner != null) {
            runner.cancel();
            runner = null;
        }
        closeSink();
        closeHttp();
        releaseOutput();
        super.onDestroy();
    }
}
