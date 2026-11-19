package com.abrenoch.wledgrabber.tv.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

import com.abrenoch.wledgrabber.common.capture.ScreenOffReceiver;
import com.abrenoch.wledgrabber.common.led.CalibrationSolver;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.ConfigFields;
import com.abrenoch.wledgrabber.common.led.ConfigSnapshot;
import com.abrenoch.wledgrabber.common.led.LayoutDraftCompiler;
import com.abrenoch.wledgrabber.common.led.LayoutProbe;
import com.abrenoch.wledgrabber.common.led.LedLayoutConfig;
import com.abrenoch.wledgrabber.common.led.Side;
import com.abrenoch.wledgrabber.common.network.wled.DeviceReadiness;
import com.abrenoch.wledgrabber.common.network.wled.LayoutProbeSender;
import com.abrenoch.wledgrabber.common.network.wled.ProbeResult;
import com.abrenoch.wledgrabber.common.network.wled.WledHttpClient;
import com.abrenoch.wledgrabber.common.network.wled.WledSession;
import com.abrenoch.wledgrabber.common.startup.StartConfirmationStore;
import com.abrenoch.wledgrabber.common.util.ConfigDraft;
import com.abrenoch.wledgrabber.common.util.Preferences;
import com.abrenoch.wledgrabber.common.util.PreferencesConfigStorage;
import com.abrenoch.wledgrabber.common.util.SettingsDraftController;
import com.abrenoch.wledgrabber.common.util.ValidationResult;
import com.abrenoch.wledgrabber.tv.R;

/** A single, non-restorable calibration attempt. Only the explicit final action can write. */
final class GuidedCalibration implements LayoutProbeSender.GuidedListener {
    private static final long IDLE_MS = 60_000;
    private final Activity host;
    private final SettingsDraftController editor;
    private final int width, height;
    private final Runnable finished;
    private final LayoutProbeSender sender = LayoutProbeSender.get();
    private final LayoutProbeSender.GuidedToken token = new LayoutProbeSender.GuidedToken();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable idle = this::close;
    private final Runnable stale = () -> abort(R.string.cal_not_ready);
    private final ConfigSnapshot base;
    private AlertDialog dialog;
    private CompiledLayout original, preview;
    private CalibrationSolver.Candidate candidate;
    private int cursor;
    private final CalibrationReview progress = new CalibrationReview();
    private boolean closed;
    private long verifiedAt;
    private Runnable onAck;

    GuidedCalibration(Activity host, SettingsDraftController editor, int width, int height, Runnable finished) {
        this.host = host; this.editor = editor; this.width = width; this.height = height;
        this.finished = finished; this.base = editor.committedSnapshot();
    }

    void open() {
        if (base == null || !CalibrationSolver.supports(base.layout())) { abort(R.string.cal_unsupported); return; }
        ValidationResult<CompiledLayout> compiled = LayoutDraftCompiler.compileForPreview(editor.draft(), width, height);
        if (!compiled.isValid() || compiled.value().mapping().physicalLedCount() != base.device().physicalLedCount()) {
            abort(R.string.cal_not_ready); return;
        }
        original = compiled.value();
        LedLayoutConfig l = base.layout();
        show(builder().setMessage(host.getString(R.string.cal_setup, base.device().host(), l.top(), l.right(),
                l.bottom(), l.left(), base.device().physicalLedCount() - l.totalLedCount()))
                .setPositiveButton(R.string.cal_start, (d, w) -> {
                    show(builder().setMessage(R.string.cal_checking));
                    checkDevice();
                }));
    }

    private void checkDevice() {
        if (closed || progress.readyToSave()) return;
        new Thread(() -> {
            boolean ready = false;
            try (WledHttpClient client = new WledHttpClient()) {
                ProbeResult probe = client.probe(base.device().host(), base.device().httpPort());
                ready = probe.isSuccess() && new DeviceReadiness().evaluate(base.device(), probe.device(),
                        new StartConfirmationStore(host).load()).isValid()
                        && new PreferencesConfigStorage(new Preferences(host)).load().revision() == base.revision();
            } catch (RuntimeException ignored) { /* fail closed */ }
            final boolean ok = ready;
            main.post(() -> {
                if (closed || progress.readyToSave()) return;
                if (!ok || !ScreenOffReceiver.isAwake(host)) { abort(R.string.cal_not_ready); return; }
                boolean first = verifiedAt == 0;
                verifiedAt = SystemClock.elapsedRealtime();
                main.removeCallbacks(stale);
                main.postDelayed(stale, WledSession.STALE_AFTER_MS);
                main.postDelayed(this::checkDevice, WledSession.HEALTH_INTERVAL_MS);
                if (first) seek();
            });
        }, "calibration-readiness").start();
    }

    private void seek() {
        progress.seek();
        candidate = null;
        show(builder().setMessage(seekText()).setPositiveButton(R.string.cal_pair, (d, w) -> observe(null)));
        dialog.setOnKeyListener((d, key, event) -> {
            if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int step = event.getRepeatCount() >= 5 ? 10 : 1;
                int delta = key == KeyEvent.KEYCODE_DPAD_LEFT ? -step : step;
                cursor = Math.max(0, Math.min(base.layout().totalLedCount() - 1, cursor + delta));
                dialog.setMessage(seekText());
                lightCursor();
            }
            return true;
        });
        lightCursor();
    }

    private String seekText() { return host.getString(R.string.cal_seek, cursor, base.layout().totalLedCount() - 1); }

    private void lightCursor() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        send(original, physical(cursor), () -> {
            if (cursor > 0) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
            }
        });
    }

    private int[] physical(int index) { return new int[]{original.mapping().physicalToLogical(index)}; }

    private void observe(Side first) {
        int index = first == null ? cursor - 1 : cursor;
        String[] labels = host.getResources().getStringArray(R.array.cal_sides);
        show(builder().setTitle(host.getString(R.string.cal_observe, index)).setItems(labels, (d, which) -> {
            if (!progress.canAnswer()) return;
            Side answer = Side.values()[which];
            if (first == null) { observe(answer); return; }
            ValidationResult<CalibrationSolver.Candidate> solution = CalibrationSolver.solve(base.layout(), cursor, first, answer);
            if (!solution.isValid()) { message(R.string.cal_pair_invalid); seek(); return; }
            candidate = solution.value();
            ConfigDraft draft = editor.draft().copy().set(ConfigFields.LED_INPUT_POSITION, Integer.toString(candidate.inputPosition()))
                    .set(ConfigFields.LED_REVERSE, Boolean.toString(candidate.reverse()));
            ValidationResult<CompiledLayout> result = LayoutDraftCompiler.compileForPreview(draft, width, height);
            if (!result.isValid()) { abort(R.string.cal_unsupported); return; }
            preview = result.value(); progress.startEdges(); review(0);
        }).setNeutralButton(R.string.cal_relocate, (d, w) -> seek()));
        dialog.getListView().setEnabled(false);
        send(original, physical(index), () -> { dialog.getListView().setEnabled(true); dialog.getListView().requestFocus(); });
    }

    private void review(int index) {
        Side side = Side.values()[index];
        String label = host.getResources().getStringArray(R.array.cal_sides)[index];
        show(builder().setMessage(host.getString(R.string.cal_review, index + 1, label))
                .setNeutralButton(R.string.cal_relocate, (d, w) -> seek())
                .setPositiveButton(R.string.cal_matches, (d, w) -> {
                    if (!progress.confirmEdge(index)) return;
                    if (index + 1 < Side.values().length) review(index + 1); else confirm();
                }));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        send(preview, LayoutProbe.logicalForSide(base.layout(), side), () -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();
        });
    }

    private void confirm() {
        if (!progress.readyToSave()) return;
        sender.stop(token);
        main.removeCallbacks(stale);
        show(builder().setMessage(host.getString(R.string.cal_summary, base.layout().inputPosition(),
                direction(base.layout().reverse()), candidate.inputPosition(), direction(candidate.reverse())))
                .setPositiveButton(R.string.cal_save, (d, w) -> {
                    if (closed) return;
                    if (editor.saveCalibration(candidate.inputPosition(), candidate.reverse()).isValid()) {
                        host.setResult(Activity.RESULT_OK);
                        message(R.string.layout_editor_saved); close();
                    } else { message(R.string.cal_save_failed); confirm(); }
                }));
    }
    private String direction(boolean reverse) { return host.getString(reverse ? R.string.cal_reverse : R.string.cal_forward); }

    private void send(CompiledLayout layout, int[] logical, Runnable receipt) {
        if (closed || SystemClock.elapsedRealtime() - verifiedAt >= WledSession.STALE_AFTER_MS || !ScreenOffReceiver.isAwake(host)) {
            abort(R.string.cal_not_ready); return;
        }
        touch(); onAck = receipt;
        LayoutProbeSender.Result result = sender.show(token, progress.nextFrame(), base, layout, logical, this);
        if (result != LayoutProbeSender.Result.STARTED) abort(result == LayoutProbeSender.Result.BUSY
                ? R.string.cal_busy : R.string.cal_not_ready);
    }

    @Override public void onSent(long sequence, boolean ok) {
        main.post(() -> {
            if (closed || !progress.isCurrent(sequence)) return;
            if (!ok) { abort(R.string.cal_not_ready); return; }
            if (progress.acknowledge(sequence) && onAck != null) onAck.run();
        });
    }

    @Override public void onReleased(boolean confirmedOff) {
        main.post(() -> {
            if (!host.isFinishing() && !host.isDestroyed()) message(confirmedOff ? R.string.cal_off : R.string.cal_off_unknown);
            if (!closed && !progress.readyToSave()) close();
        });
    }

    private AlertDialog.Builder builder() {
        return new AlertDialog.Builder(host).setTitle(R.string.cal_title)
                .setNegativeButton(android.R.string.cancel, (d,w) -> close()).setOnCancelListener(d -> close());
    }
    private void show(AlertDialog.Builder builder) {
        if (dialog != null) dialog.dismiss();
        dialog = builder.show();
        touch();
    }
    private void touch() { main.removeCallbacks(idle); main.postDelayed(idle, IDLE_MS); }
    private void message(int text) { Toast.makeText(host, text, Toast.LENGTH_LONG).show(); }
    private void abort(int text) { if (!closed) { message(text); close(); } }
    void close() {
        if (closed) return;
        closed = true; main.removeCallbacksAndMessages(null); sender.stop(token);
        if (dialog != null) dialog.dismiss();
        finished.run();
    }
}
