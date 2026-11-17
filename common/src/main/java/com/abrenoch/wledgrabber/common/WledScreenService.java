package com.abrenoch.wledgrabber.common;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.ServiceCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.abrenoch.wledgrabber.common.capture.CaptureSession;
import com.abrenoch.wledgrabber.common.capture.FrameLease;
import com.abrenoch.wledgrabber.common.capture.FramePool;
import com.abrenoch.wledgrabber.common.capture.FrameTimingStats;
import com.abrenoch.wledgrabber.common.capture.ImagePlaneView;
import com.abrenoch.wledgrabber.common.capture.LedStreamer;
import com.abrenoch.wledgrabber.common.capture.OutputOwner;
import com.abrenoch.wledgrabber.common.capture.OutputSessionCoordinator;
import com.abrenoch.wledgrabber.common.led.CaptureConfig;
import com.abrenoch.wledgrabber.common.led.CaptureSizing;
import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.LedColorSampler;
import com.abrenoch.wledgrabber.common.led.PlaneView;
import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.led.WhiteMode;
import com.abrenoch.wledgrabber.common.network.wled.StateReadResult;
import com.abrenoch.wledgrabber.common.network.wled.UdpDatagramSink;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.network.wled.WledUdpTransport;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

/**
 * WLED direct capture service: captures the screen, samples the four-side layout, and streams colors to a single WLED controller.
 */
public class WledScreenService extends Service {
    public static final String BROADCAST_ERROR = "SERVICE_ERROR";
    public static final String BROADCAST_TAG = "SERVICE_STATUS";
    public static final String BROADCAST_FILTER = "SERVICE_FILTER";
    private static final String BASE = "com.abrenoch.wledgrabber.service.";
    public static final String ACTION_START = BASE + "ACTION_START";
    public static final String ACTION_STOP = BASE + "ACTION_STOP";
    public static final String ACTION_EXIT = BASE + "ACTION_EXIT";
    public static final String GET_STATUS = BASE + "ACTION_STATUS";
    public static final String EXTRA_RESULT_CODE = BASE + "EXTRA_RESULT_CODE";
    private static final int NOTIFICATION_ID = 1;
    private static final int NOTIFICATION_EXIT_INTENT_ID = 2;
    private static final long KEEP_ALIVE_MS = 1000L;
    private static final String TAG = "WledScreenService";
    private static final String OUTPUT_OWNER_ID = "wled-screen-service";

    private final OutputSessionCoordinator outputCoordinator = OutputSessionCoordinator.get();
    private final CaptureSession captureSession = new CaptureSession();
    private final LedColorSampler sampler = new LedColorSampler();
    private final Object lock = new Object();

    private MediaProjectionManager projectionManager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private LedStreamer streamer;
    private FramePool framePool;
    private UdpDatagramSink sink;
    private WledHttpClient httpClient;
    private byte[] scratch;
    private byte[] lastFrame;
    private final FrameTimingStats samplingMicros = new FrameTimingStats(600);
    private final FrameTimingStats sendIntervalMicros = new FrameTimingStats(600);
    private long lastSentAtMicros;
    private CompiledLayout layout;
    private RgbwMode rgbwMode;
    private WhiteMode whiteMode;
    private Protocol protocol;
    private int physicalCount;
    private int channels;
    private int frameRate;
    private boolean averageColor;
    private com.abrenoch.wledgrabber.common.led.QualitySettings quality =
            com.abrenoch.wledgrabber.common.led.QualitySettings.OFF;
    private boolean running;
    private boolean stopping;
    private long epoch;
    private long outputEpoch;
    private long lastFrameAt;
    private String startError;

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            captureSession.onProjectionRevoked();
            stopCapture("projection revoked");
        }
    };

    /**
     * MediaProjection only emits frames when the screen changes; without a heartbeat
     * WLED leaves realtime mode after its 2 s UDP timeout and the strip falls back to
     * its own effect. Resends the last payload through the same single writer.
     */
    private final Runnable keepAlive = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            synchronized (lock) {
                if (!running || stopping || lastFrame == null) {
                    return;
                }
                if (now - lastFrameAt >= KEEP_ALIVE_MS) {
                    lastFrameAt = now;
                    offerPayload(lastFrame, now);
                }
            }
            if (captureHandler != null) {
                captureHandler.postDelayed(this, KEEP_ALIVE_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        switch (intent.getAction()) {
            case ACTION_START:
                if (!beginStartSession(intent)) {
                    return START_NOT_STICKY;
                }
                startCapture(intent);
                break;
            case ACTION_STOP:
            case ACTION_EXIT:
                stopCapture("user stop");
                break;
            case GET_STATUS:
                notifyActivity();
                break;
            default:
                break;
        }
        return START_NOT_STICKY;
    }

    /**
     * Drives the CAP-1 state machine up to a foregrounded STARTING session. The
     * foreground transition always happens before any capture work, so the
     * startForegroundService() deadline can never expire.
     */
    private boolean beginStartSession(Intent intent) {
        if (!captureSession.beginStart() || !captureSession.validationPassed()) {
            return false;
        }
        boolean granted = intent.getIntExtra(EXTRA_RESULT_CODE, 0) == Activity.RESULT_OK;
        if (!captureSession.onConsentResult(granted)) {
            // Started via startForegroundService(): satisfy the contract before bailing out.
            if (startForegroundSafely()) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            }
            return false;
        }
        if (!startForegroundSafely()) {
            captureSession.resetToIdle();
            return false;
        }
        return captureSession.onServiceForegrounded();
    }

    private void startCapture(Intent intent) {
        synchronized (lock) {
            if (running || stopping) {
                return;
            }
            startError = null;
            ConfigRepository repository = new ConfigRepository(
                    new PreferencesConfigStorage(new Preferences(this)),
                    new ConfigValidator());
            ValidationResult<ConfigSnapshot> loaded = repository.load();
            ConfigSnapshot snapshot = repository.committedSnapshot();
            if (!loaded.isValid() || snapshot == null
                    || snapshot.device().host() == null
                    || snapshot.device().host().trim().isEmpty()
                    || snapshot.device().physicalLedCount() < 1
                    || snapshot.device().rgbwMode() == RgbwMode.UNCONFIRMED) {
                captureSession.rejectStart();
                failStart("WLED configuration is incomplete");
                return;
            }
            if (!outputCoordinator.acquire(OutputOwner.CAPTURE, OUTPUT_OWNER_ID)) {
                captureSession.rejectStart();
                failStart("output already owned by another session");
                return;
            }
            outputEpoch = outputCoordinator.epoch();

            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (windowManager == null) {
                failStarted("window service unavailable");
                return;
            }
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            CaptureConfig capture = snapshot.capture();
            CaptureSizing.Size size = CaptureSizing.fit(metrics.widthPixels,
                    metrics.heightPixels, capture.maxDimension());
            physicalCount = snapshot.device().physicalLedCount();
            ValidationResult<CompiledLayout> compiled = new ClassicLedLayout().compile(
                    snapshot.layout(), physicalCount, size.width(), size.height());
            if (!compiled.isValid()) {
                failStarted("layout: " + compiled.errors().get(0));
                return;
            }
            layout = compiled.value();
            rgbwMode = snapshot.device().rgbwMode();
            whiteMode = snapshot.device().whiteMode();
            protocol = snapshot.device().protocol();
            channels = rgbwMode == RgbwMode.RGBW ? 4 : 3;
            frameRate = Math.max(1, capture.frameRate());
            averageColor = capture.useAverageColor();
            quality = capture.quality();
            sampler.resetQualityState();
            scratch = new byte[physicalCount * channels];
            lastFrame = new byte[physicalCount * channels];
            framePool = new FramePool(physicalCount * channels);

            int port = channels == 4 || protocol == Protocol.DDP
                    ? snapshot.device().ddpPort() : snapshot.device().udpPort();
            try {
                sink = new UdpDatagramSink(snapshot.device().host(), port);
            } catch (Exception e) {
                failStarted("UDP endpoint: " + e.getMessage());
                return;
            }
            final WledUdpTransport transport = new WledUdpTransport(sink);
            httpClient = new WledHttpClient();
            LedStreamer.FrameSink frameSink = new LedStreamer.FrameSink() {
                @Override
                public boolean send(FrameLease lease) {
                    return sendFrame(transport, lease.data(), lease.ledCount(), channels);
                }

                @Override
                public boolean sendBlack(int ledCount, int ledChannels) {
                    return sendFrame(transport, new byte[ledCount * ledChannels], ledCount,
                            ledChannels);
                }
            };
            final String host = snapshot.device().host();
            final int httpPort = snapshot.device().httpPort();
            requestWledPowerOn(httpClient, host, httpPort);
            streamer = new LedStreamer(framePool, frameSink, reason -> {
                boolean off = httpClient.turnOff(host, httpPort);
                StateReadResult state = httpClient.readState(host, httpPort);
                return off && state.isSuccess() && !state.on() && !state.live();
            }, 3000);

            if (!captureSession.requestProjection()) {
                failStarted("projection already requested");
                return;
            }
            try {
                projection = projectionManager.getMediaProjection(
                        intent.getIntExtra(EXTRA_RESULT_CODE, 0),
                        intent);
            } catch (Exception e) {
                captureSession.onProjectionCreated(false);
                failStarted("projection consent: " + e.getMessage());
                return;
            }
            if (projection == null) {
                captureSession.onProjectionCreated(false);
                failStarted("projection unavailable");
                return;
            }
            captureSession.onProjectionCreated(true);
            projection.registerCallback(projectionCallback, new Handler(getMainLooper()));

            captureThread = new HandlerThread("wled-capture");
            captureThread.start();
            captureHandler = new Handler(captureThread.getLooper());
            reader = ImageReader.newInstance(size.width(), size.height(),
                    PixelFormat.RGBA_8888, 3);
            reader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);
            virtualDisplay = projection.createVirtualDisplay("wled-capture",
                    size.width(), size.height(), metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, captureHandler);
            if (virtualDisplay == null) {
                captureSession.createDisplay(false);
                failStarted("virtual display unavailable");
                return;
            }
            captureSession.createDisplay(true);
            epoch = System.currentTimeMillis();
            streamer.start(epoch, OutputOwner.CAPTURE, physicalCount, channels);
            running = true;
            stopping = false;
            if (captureHandler != null) {
                captureHandler.postDelayed(keepAlive, KEEP_ALIVE_MS);
            }
            notifyActivity();
        }
    }

    /**
     * WLED only outputs while its master power is on; realtime UDP frames alone do
     * not switch it on. Runs off the main thread for the no-main-thread-network rule.
     */
    private void requestWledPowerOn(WledHttpClient client, String host, int port) {
        Thread worker = new Thread(() -> {
            try {
                if (!client.turnOn(host, port)) {
                    Log.w(TAG, "WLED power-on not acknowledged");
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "WLED power-on failed", e);
            }
        }, "wled-power-on");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean sendFrame(WledUdpTransport transport, byte[] data, int leds, int ledChannels) {
        if (ledChannels == 4) {
            return transport.sendRgbw(data, leds);
        }
        if (protocol == Protocol.DDP) {
            return transport.sendRgbDdp(data, leds);
        }
        return transport.sendRgb(data, leds);
    }

    private void onImageAvailable(ImageReader source) {
        synchronized (lock) {
            if (!running || stopping) {
                return;
            }
        }
        long now = System.currentTimeMillis();
        if (now - lastFrameAt < 1000L / frameRate) {
            Image skipped = null;
            try {
                skipped = source.acquireLatestImage();
            } catch (IllegalStateException ignored) {
            } finally {
                if (skipped != null) {
                    skipped.close();
                }
            }
            return;
        }
        lastFrameAt = now;
        Image image = null;
        long sampleStartNanos = System.nanoTime();
        try {
            image = source.acquireLatestImage();
            if (image == null) {
                return;
            }
            ValidationResult<PlaneView> plane = ImagePlaneView.from(image);
            if (!plane.isValid()) {
                return;
            }
            if (!sampler.samplePhysical(plane.value(), layout, averageColor,
                    rgbwMode, whiteMode, scratch, quality)) {
                return;
            }
            samplingMicros.recordMicros((System.nanoTime() - sampleStartNanos) / 1000);
            byte[] frame = lastFrame;
            if (frame == null) {
                return;
            }
            System.arraycopy(scratch, 0, frame, 0, frame.length);
            if (offerPayload(frame, now)) {
                long nowMicros = System.nanoTime() / 1000;
                if (lastSentAtMicros != 0) {
                    sendIntervalMicros.recordMicros(nowMicros - lastSentAtMicros);
                }
                lastSentAtMicros = nowMicros;
                logCaptureMetricsIfDue();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "frame dropped", e);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private boolean offerPayload(byte[] payload, long now) {
        FramePool pool = framePool;
        LedStreamer target = streamer;
        CompiledLayout current = layout;
        if (pool == null || target == null || current == null || payload == null) {
            return false;
        }
        FrameLease lease = pool.acquire(epoch, current.generation(), now, channels, physicalCount);
        if (lease != null) {
            System.arraycopy(payload, 0, lease.data(), 0, payload.length);
            return target.offer(lease);
        }
        return false;
    }

    /** Emits the QA-2 timing evidence every 300 sent frames and resets the window. */
    private void logCaptureMetricsIfDue() {
        if (sendIntervalMicros.size() < 300) {
            return;
        }
        Log.i(TAG, String.format(java.util.Locale.US,
                "qa2 sampling_us p50=%d p95=%d p99=%d max=%d n=%d; "
                        + "interval_us p50=%d p95=%d p99=%d max=%d n=%d",
                samplingMicros.percentileMicros(50), samplingMicros.percentileMicros(95),
                samplingMicros.percentileMicros(99), samplingMicros.maxMicros(),
                samplingMicros.size(),
                sendIntervalMicros.percentileMicros(50), sendIntervalMicros.percentileMicros(95),
                sendIntervalMicros.percentileMicros(99), sendIntervalMicros.maxMicros(),
                sendIntervalMicros.size()));
        samplingMicros.reset();
        sendIntervalMicros.reset();
        lastSentAtMicros = 0;
    }

    private void stopCapture(String reason) {
        synchronized (lock) {
            if (stopping) {
                return;
            }
            Log.i(TAG, "capture stopping: " + reason);
            stopping = true;
            running = false;
        }
        captureSession.beginStop();
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (reader != null) {
            reader.setOnImageAvailableListener(null, null);
            reader.close();
            reader = null;
        }
        if (projection != null) {
            projection.unregisterCallback(projectionCallback);
            projection.stop();
            projection = null;
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
            captureHandler = null;
        }
        if (streamer != null) {
            streamer.stop(reason);
            streamer = null;
        }
        if (sink != null) {
            sink.close();
            sink = null;
        }
        if (httpClient != null) {
            httpClient.close();
            httpClient = null;
        }
        releaseOutputOwnership();
        sampler.resetQualityState();
        quality = com.abrenoch.wledgrabber.common.led.QualitySettings.OFF;
        framePool = null;
        layout = null;
        scratch = null;
        lastFrame = null;
        notifyActivity();
        captureSession.stopCompleted();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private boolean startForegroundSafely() {
        try {
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION : 0;
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type);
            return true;
        } catch (RuntimeException e) {
            startError = "foreground start failed: " + e.getMessage();
            notifyActivity();
            return false;
        }
    }

    private android.app.Notification buildNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        WledNotification notification = new WledNotification(this, manager);
        Intent exit = new Intent(this, WledScreenService.class).setAction(ACTION_EXIT);
        notification.setAction(NOTIFICATION_EXIT_INTENT_ID,
                getString(R.string.notification_exit_button), exit);
        return notification.buildNotification();
    }

    private void failStarted(String error) {
        Log.w(TAG, "capture start failed: " + error);
        captureSession.resetToIdle();
        startError = error;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (reader != null) {
            reader.setOnImageAvailableListener(null, null);
            reader.close();
            reader = null;
        }
        if (projection != null) {
            projection.unregisterCallback(projectionCallback);
            projection.stop();
            projection = null;
        }
        if (captureThread != null) {
            captureThread.quitSafely();
            captureThread = null;
            captureHandler = null;
        }
        if (streamer != null) {
            streamer.stop("start failed");
            streamer = null;
        }
        if (sink != null) {
            sink.close();
            sink = null;
        }
        if (httpClient != null) {
            httpClient.close();
            httpClient = null;
        }
        releaseOutputOwnership();
        framePool = null;
        layout = null;
        scratch = null;
        lastFrame = null;
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        notifyActivity();
        stopSelf();
    }

    private void failStart(String error) {
        Log.w(TAG, "capture start rejected: " + error);
        captureSession.resetToIdle();
        startError = error;
        notifyActivity();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseOutputOwnership() {
        outputCoordinator.release(OutputOwner.CAPTURE, OUTPUT_OWNER_ID, outputEpoch);
    }

    private void notifyActivity() {
        Intent intent = new Intent(BROADCAST_FILTER);
        intent.putExtra(BROADCAST_TAG, running);
        intent.putExtra(BROADCAST_ERROR, startError);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        stopCapture("service destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
