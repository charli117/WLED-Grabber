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
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
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
import com.abrenoch.wledgrabber.common.led.LedLayoutConfig;
import com.abrenoch.wledgrabber.common.led.PlaneView;
import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.RgbwMode;
import com.abrenoch.wledgrabber.common.led.WhiteMode;
import com.abrenoch.wledgrabber.common.network.wled.DeviceReadiness;
import com.abrenoch.wledgrabber.common.network.wled.ReadyDevice;
import com.abrenoch.wledgrabber.common.network.wled.StartConfirmation;
import com.abrenoch.wledgrabber.common.network.wled.UdpDatagramSink;
import com.abrenoch.wledgrabber.common.network.wled.WledDevice;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.network.wled.WledPowerOn;
import com.abrenoch.wledgrabber.common.network.wled.WledSession;
import com.abrenoch.wledgrabber.common.network.wled.WledUdpTransport;
import com.abrenoch.wledgrabber.common.startup.StartConfirmationStore;
import com.abrenoch.wledgrabber.common.util.ConfigRepository;
import com.abrenoch.wledgrabber.common.util.ConfigValidator;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * WLED direct capture service: captures the screen, samples the four-side layout, and streams colors to a single WLED controller.
 */
public class WledScreenService extends Service {
    public static final String BROADCAST_ERROR = "SERVICE_ERROR";
    public static final String BROADCAST_TAG = "SERVICE_STATUS";
    public static final String BROADCAST_RELEASE_KNOWN = "SERVICE_RELEASE_KNOWN";
    public static final String BROADCAST_RELEASE_CONFIRMED = "SERVICE_RELEASE_CONFIRMED";
    public static final String BROADCAST_OBSERVATION_STALE = "SERVICE_OBSERVATION_STALE";
    public static final String BROADCAST_OBSERVED_AT = "SERVICE_OBSERVED_AT";
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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
    private final FrameTimingStats captureAgeMicros = new FrameTimingStats(600);
    private final FrameTimingStats sendIntervalMicros = new FrameTimingStats(600);
    private long lastSentAtMicros;
    private CompiledLayout layout;
    private LedLayoutConfig layoutConfig;
    private int captureWidth;
    private int captureHeight;
    private int captureDensityDpi;
    private int captureMaxDimension = 640;
    private int captureGeneration;
    private DisplayManager displayManager;
    private DisplayManager.DisplayListener displayListener;
    private RgbwMode rgbwMode;
    private WhiteMode whiteMode;
    private Protocol protocol;
    private int physicalCount;
    private int channels;
    private int frameRate;
    private boolean averageColor;
    private volatile com.abrenoch.wledgrabber.common.led.QualitySettings quality =
            com.abrenoch.wledgrabber.common.led.QualitySettings.OFF;
    private com.abrenoch.wledgrabber.common.led.QualitySettings baseQuality =
            com.abrenoch.wledgrabber.common.led.QualitySettings.OFF;
    private volatile boolean hdrCompensation;
    private long diagR;
    private long diagG;
    private long diagB;
    private int diagFrames;
    private boolean running;
    private boolean stopping;
    private volatile boolean releaseKnown;
    private volatile boolean releaseConfirmed;
    private volatile boolean observationStale;
    private volatile long lastObservedAt;
    private WledSession session;
    private ScheduledExecutorService healthExecutor;
    private ScheduledFuture<?> healthTask;
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

        @Override
        public void onCapturedContentResize(int width, int height) {
            // API 34+: single-app window or rotation changed the captured content.
            postResize(width, height, captureDensityDpi);
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
        releaseKnown = false;
        releaseConfirmed = false;
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
                    || !new ConfigValidator().validateForStart(snapshot).isValid()) {
                captureSession.rejectStart();
                failStart("WLED configuration is incomplete");
                return;
            }
            WledDevice observed = BootActivity.observedFrom(intent);
            StartConfirmation confirmation = new StartConfirmationStore(this).load();
            if (observed == null || confirmation == null) {
                captureSession.rejectStart();
                failStart("device verification required");
                return;
            }
            ValidationResult<ReadyDevice> readiness = new DeviceReadiness()
                    .evaluate(snapshot.device(), observed, confirmation);
            if (!readiness.isValid()) {
                captureSession.rejectStart();
                failStart("device not ready: " + readiness.errors().get(0).code().name());
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
            layoutConfig = snapshot.layout();
            captureWidth = size.width();
            captureHeight = size.height();
            captureDensityDpi = metrics.densityDpi;
            captureMaxDimension = capture.maxDimension();
            captureGeneration = 0;
            displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            displayListener = new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId != Display.DEFAULT_DISPLAY) {
                        return;
                    }
                    Display display = displayManager == null ? null
                            : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
                    if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        refreshHdrCompensation(display);
                    }
                    DisplayMetrics current = new DisplayMetrics();
                    WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                    if (wm == null) {
                        return;
                    }
                    wm.getDefaultDisplay().getRealMetrics(current);
                    postResize(current.widthPixels, current.heightPixels, current.densityDpi);
                }
            };
            rgbwMode = snapshot.device().rgbwMode();
            whiteMode = snapshot.device().whiteMode();
            protocol = snapshot.device().protocol();
            channels = rgbwMode == RgbwMode.RGBW ? 4 : 3;
            frameRate = Math.max(1, capture.frameRate());
            averageColor = capture.useAverageColor();
            baseQuality = capture.quality();
            hdrCompensation = false;
            quality = baseQuality;
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
            // WLED only outputs while its master power is on; realtime UDP frames
            // alone do not switch it on. The worker keeps this off the main thread.
            WledPowerOn.async(httpClient, host, httpPort, "wled-power-on");
            final ReadyDevice readyDevice = readiness.value();
            final WledSession activeSession = new WledSession(httpClient,
                    (count, rgbw) -> sendFrame(transport,
                            new byte[count * (rgbw ? 4 : 3)], count, rgbw ? 4 : 3),
                    System::currentTimeMillis);
            activeSession.start(readyDevice);
            session = activeSession;
            // The streamer's stop barrier sends black first; the session release
            // then re-sends black, posts off and verifies the readback in one place.
            streamer = new LedStreamer(framePool, frameSink, reason ->
                    activeSession.release(reason).status()
                            == WledSession.ReleaseStatus.CONFIRMED, 3000);

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
            final int initialGeneration = captureGeneration;
            reader.setOnImageAvailableListener(
                    source -> onImageAvailable(source, initialGeneration), captureHandler);
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
            if (displayManager != null) {
                displayManager.registerDisplayListener(displayListener, mainHandler);
            }
            epoch = System.currentTimeMillis();
            streamer.start(epoch, OutputOwner.CAPTURE, physicalCount, channels);
            running = true;
            stopping = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && displayManager != null) {
                Display defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
                if (defaultDisplay != null) {
                    refreshHdrCompensation(defaultDisplay);
                }
            }
            startHealthLoop();
            if (captureHandler != null) {
                captureHandler.postDelayed(keepAlive, KEEP_ALIVE_MS);
            }
            notifyActivity();
        }
    }

    /**
     * WLED observation is a background HTTP readback at most once per 5 s. An
     * observation older than 10 s is reported as stale; a changed device identity
     * stops capture instead of silently sending to a different controller.
     */
    /**
     * HDR/Dolby Vision content is tone-mapped into the SDR virtual display, which
     * flattens contrast and chroma. Detect the active HDR mode and add a modest
     * compensation while it lasts; SDR output stays byte-identical.
     */
    private void refreshHdrCompensation(Display display) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || display == null) {
            hdrCompensation = false;
            quality = baseQuality;
            return;
        }
        boolean hdr = display.isHdr();
        boolean dolbyVision = false;
        for (int type : display.getHdrCapabilities().getSupportedHdrTypes()) {
            if (type == Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) {
                dolbyVision = true;
                break;
            }
        }
        boolean changed = hdr != hdrCompensation;
        hdrCompensation = hdr;
        quality = baseQuality.withHdrCompensation(hdr);
        if (changed) {
            Log.i(TAG, "hdr active=" + hdr + " dolbyVisionSupported=" + dolbyVision
                    + " compensation=" + hdr);
        }
    }

    private void startHealthLoop() {
        WledSession.Observation first = session == null ? null : session.currentObservation();
        lastObservedAt = first == null ? System.currentTimeMillis() : first.observedAtMillis();
        observationStale = false;
        healthExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "wled-health");
            thread.setDaemon(true);
            return thread;
        });
        healthTask = healthExecutor.scheduleWithFixedDelay(this::observeOnce,
                WledSession.HEALTH_INTERVAL_MS, WledSession.HEALTH_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    private void observeOnce() {
        WledSession current;
        synchronized (lock) {
            if (!running || stopping) {
                return;
            }
            current = session;
        }
        if (current == null) {
            return;
        }
        WledSession.Observation observation;
        try {
            observation = current.observeNow();
        } catch (RuntimeException error) {
            observation = null;
        }
        long now = System.currentTimeMillis();
        boolean stale = com.abrenoch.wledgrabber.common.network.wled.ObservationMonitor
                .isStale(observation, now);
        long observedAt = observation == null ? lastObservedAt : observation.observedAtMillis();
        if (stale != observationStale || observedAt != lastObservedAt) {
            observationStale = stale;
            lastObservedAt = observedAt;
            notifyActivity();
        }
        if (observation != null && observation.device() != null
                && observation.device().capabilities() != null) {
            Log.i(TAG, "diag wled live=" + observation.device().capabilities().live()
                    + " udp=" + observation.device().capabilities().udpPort()
                    + " count=" + observation.device().capabilities().observedLedCount()
                    + " err=" + observation.error());
        }
        if (observation != null && observation.identityChanged()) {
            mainHandler.post(() -> stopCapture("device identity changed"));
        }
    }

    private void stopHealthLoop() {
        if (healthTask != null) {
            healthTask.cancel(false);
            healthTask = null;
        }
        if (healthExecutor != null) {
            healthExecutor.shutdownNow();
            healthExecutor = null;
        }
    }

    /**
     * Rebuilds the sampling plan and swaps the ImageReader surface on the same
     * VirtualDisplay. Runs on the capture thread, so an in-flight old-generation
     * image finishes before the reader is replaced; old callbacks are rejected by
     * the generation check.
     */
    private void postResize(int sourceWidth, int sourceHeight, int densityDpi) {
        Handler handler = captureHandler;
        if (handler == null) {
            return;
        }
        handler.post(() -> applyCaptureSize(sourceWidth, sourceHeight, densityDpi));
    }

    private void applyCaptureSize(int sourceWidth, int sourceHeight, int densityDpi) {
        synchronized (lock) {
            if (!running || stopping || virtualDisplay == null
                    || layoutConfig == null || reader == null || captureHandler == null) {
                return;
            }
        }
        ValidationResult<com.abrenoch.wledgrabber.common.led.CaptureResizePlanner.Plan> planned =
                com.abrenoch.wledgrabber.common.led.CaptureResizePlanner.plan(
                        layoutConfig, physicalCount, sourceWidth, sourceHeight,
                        captureMaxDimension, captureWidth, captureHeight, captureGeneration);
        if (!planned.isValid()) {
            mainHandler.post(() -> stopCapture("resize layout invalid"));
            return;
        }
        if (!planned.value().changed()) {
            return;
        }
        final int width = planned.value().width();
        final int height = planned.value().height();
        final int generation = (int) planned.value().generation();
        final CompiledLayout nextLayout = planned.value().layout();
        ImageReader replacement;
        try {
            replacement = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        } catch (RuntimeException unavailable) {
            return;
        }
        replacement.setOnImageAvailableListener(
                source -> onImageAvailable(source, generation), captureHandler);
        VirtualDisplay display = virtualDisplay;
        try {
            display.resize(width, height, densityDpi);
            display.setSurface(replacement.getSurface());
        } catch (RuntimeException failed) {
            replacement.close();
            return;
        }
        ImageReader previous = reader;
        reader = replacement;
        layout = nextLayout;
        captureWidth = width;
        captureHeight = height;
        captureDensityDpi = densityDpi;
        captureGeneration = generation;
        if (previous != null) {
            previous.setOnImageAvailableListener(null, null);
            previous.close();
        }
    }

    private void stopDisplayListener() {
        if (displayManager != null && displayListener != null) {
            try {
                displayManager.unregisterDisplayListener(displayListener);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered.
            }
        }
        displayListener = null;
        displayManager = null;
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

    private void onImageAvailable(ImageReader source, int generation) {
        synchronized (lock) {
            if (!running || stopping || generation != captureGeneration) {
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
            long capturedNanos = image.getTimestamp();
            if (capturedNanos > 0) {
                long ageMicros = (System.nanoTime() - capturedNanos) / 1000;
                if (ageMicros >= 0 && ageMicros < 5_000_000L) {
                    captureAgeMicros.recordMicros(ageMicros);
                }
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
            for (int i = 0; i < physicalCount; i++) {
                int offset = i * channels;
                diagR += scratch[offset] & 0xff;
                diagG += scratch[offset + 1] & 0xff;
                diagB += scratch[offset + 2] & 0xff;
            }
            diagFrames++;
            if (diagFrames >= 120) {
                int satSum = 0;
                int s0 = scratch[0] & 0xff;
                int s1 = scratch[1] & 0xff;
                int s2 = scratch[2] & 0xff;
                int q1 = 0;
                int q2 = 0;
                int q3 = 0;
                int q4 = 0;
                for (int i = 0; i < physicalCount; i++) {
                    int offset = i * channels;
                    int r = scratch[offset] & 0xff;
                    int g = scratch[offset + 1] & 0xff;
                    int b = scratch[offset + 2] & 0xff;
                    int mx = Math.max(r, Math.max(g, b));
                    int mn = Math.min(r, Math.min(g, b));
                    satSum += mx - mn;
                    if (i == physicalCount / 4) {
                        q1 = (r << 16) | (g << 8) | b;
                    } else if (i == physicalCount / 2) {
                        q2 = (r << 16) | (g << 8) | b;
                    } else if (i == (physicalCount * 3) / 4) {
                        q3 = (r << 16) | (g << 8) | b;
                    } else if (i == physicalCount - 1) {
                        q4 = (r << 16) | (g << 8) | b;
                    }
                }
                Log.i(TAG, String.format(java.util.Locale.US,
                        "diag sample avg rgb=(%d,%d,%d) sat=%d leds=[%06x,%06x,%06x,%06x,%06x]",
                        diagR / (diagFrames * physicalCount),
                        diagG / (diagFrames * physicalCount),
                        diagB / (diagFrames * physicalCount),
                        satSum / physicalCount,
                        (s0 << 16) | (s1 << 8) | s2, q1, q2, q3, q4));
                diagR = 0;
                diagG = 0;
                diagB = 0;
                diagFrames = 0;
            }
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
                        + "capture_age_us p50=%d p95=%d p99=%d max=%d n=%d; "
                        + "interval_us p50=%d p95=%d p99=%d max=%d n=%d",
                samplingMicros.percentileMicros(50), samplingMicros.percentileMicros(95),
                samplingMicros.percentileMicros(99), samplingMicros.maxMicros(),
                samplingMicros.size(),
                captureAgeMicros.percentileMicros(50),
                captureAgeMicros.percentileMicros(95),
                captureAgeMicros.percentileMicros(99), captureAgeMicros.maxMicros(),
                captureAgeMicros.size(),
                sendIntervalMicros.percentileMicros(50), sendIntervalMicros.percentileMicros(95),
                sendIntervalMicros.percentileMicros(99), sendIntervalMicros.maxMicros(),
                sendIntervalMicros.size()));
        samplingMicros.reset();
        captureAgeMicros.reset();
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
        stopDisplayListener();
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
        stopHealthLoop();
        if (streamer != null) {
            LedStreamer.StopResult stop = streamer.stop(reason);
            // A completed or timed-out release has a displayable result; a timeout
            // must surface as UNCONFIRMED rather than as "no result".
            releaseKnown = stop != null && (stop.releaseCalled() || stop.timedOut());
            releaseConfirmed = stop != null && stop.releaseCalled() && stop.releaseOk();
            streamer = null;
        }
        session = null;
        observationStale = false;
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
        stopDisplayListener();
        stopHealthLoop();
        if (session != null) {
            // Nothing was streamed; invalidate without sending off to the device.
            session.invalidate();
            session = null;
        }
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
        intent.putExtra(BROADCAST_RELEASE_KNOWN, releaseKnown);
        intent.putExtra(BROADCAST_RELEASE_CONFIRMED, releaseConfirmed);
        intent.putExtra(BROADCAST_OBSERVATION_STALE, observationStale);
        intent.putExtra(BROADCAST_OBSERVED_AT, lastObservedAt);
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
