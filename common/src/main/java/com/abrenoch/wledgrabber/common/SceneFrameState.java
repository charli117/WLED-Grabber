package com.abrenoch.wledgrabber.common;

import com.abrenoch.wledgrabber.common.led.ViewingScene;

/** Capture lock owns this state; persisted choice is independent of local writer acceptance. */
final class SceneFrameState {
    private long epoch = -1, revision = -1, applied = -1;
    private int generation;
    private ViewingScene scene = ViewingScene.CUSTOM;
    void start(long session) { stop(); epoch = session; generation = 0; }
    void stop() { epoch = revision = applied = -1; scene = ViewingScene.CUSTOM; }
    boolean choose(long session, long rev, ViewingScene next) {
        if (epoch < 0 || epoch != session || rev <= revision) return false;
        revision = rev; scene = next; invalidate(); return true;
    }
    void resize(int currentGeneration) { generation = currentGeneration; invalidate(); }
    boolean canPublish(long session, int layoutGeneration, long rev) {
        return epoch >= 0 && epoch == session && generation == layoutGeneration && revision == rev;
    }
    private void invalidate() { applied = -1; }
    boolean accept(long session, long rev, boolean offered) {
        if (!offered || epoch < 0 || epoch != session || rev != revision || rev == applied) return false;
        applied = rev; return true;
    }
    long applied() { return applied; }
    long revision() { return revision; }
    ViewingScene scene() { return scene; }
}
