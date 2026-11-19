package com.abrenoch.wledgrabber.common.scene;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.abrenoch.wledgrabber.common.led.ViewingScene;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** The TV user's last successful choice. Selecting never interacts with the capture service. */
public final class SceneSelectionStore {
    public static final class Selection {
        public final ViewingScene scene;
        public final long revision;
        private Selection(ViewingScene scene, long revision) { this.scene = scene; this.revision = revision; }
    }
    public interface Listener { void changed(Selection selection); }
    public interface Completion { void complete(boolean success); }
    interface Write { boolean commit(String id, long revision); }

    private static SceneSelectionStore instance;
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private final Executor writer, replies;
    private final Write write;
    private volatile Selection saved;

    public static synchronized SceneSelectionStore get(Context context) {
        if (instance == null) {
            SharedPreferences prefs = context.getApplicationContext().getSharedPreferences("viewing_scene", Context.MODE_PRIVATE);
            String id = "";
            long revision = 0;
            try {
                if (prefs.getInt("schema", 0) == 1) {
                    id = prefs.getString("id", ""); revision = prefs.getLong("revision", 0);
                }
            } catch (ClassCastException ignored) { id = ""; revision = 0; }
            Handler main = new Handler(Looper.getMainLooper());
            instance = new SceneSelectionStore(id, revision, (choice, rev) -> prefs.edit().putInt("schema", 1)
                    .putString("id", choice).putLong("revision", rev).commit(),
                    Executors.newSingleThreadExecutor(), main::post);
        }
        return instance;
    }

    SceneSelectionStore(String id, long revision, Write write, Executor serialWriter, Executor replies) {
        ViewingScene scene;
        try { scene = ViewingScene.valueOf(id == null ? "" : id); }
        catch (IllegalArgumentException ignored) { scene = ViewingScene.CUSTOM; }
        saved = new Selection(scene, Math.max(0L, revision));
        this.write = write; this.writer = serialWriter; this.replies = replies;
    }

    public Selection current() { return saved; }
    /** Replays the committed selection; listeners must discard older revisions across subscribe races. */
    public void observe(Listener listener) { listeners.add(listener); listener.changed(saved); }
    public void remove(Listener listener) { listeners.remove(listener); }

    public void select(ViewingScene scene, Completion callback) {
        writer.execute(() -> {
            Selection old = saved;
            boolean ok = scene != null;
            if (ok && scene != old.scene) {
                long revision = old.revision + 1;
                try { ok = revision > 0 && write.commit(scene.name(), revision); }
                catch (RuntimeException error) { ok = false; }
                if (ok) {
                    Selection next = new Selection(scene, revision);
                    saved = next;
                    for (Listener listener : listeners) listener.changed(next);
                }
            }
            boolean result = ok;
            if (callback != null) replies.execute(() -> callback.complete(result));
        });
    }
}
