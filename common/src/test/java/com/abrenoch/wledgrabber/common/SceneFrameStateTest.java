package com.abrenoch.wledgrabber.common;

import com.abrenoch.wledgrabber.common.led.ViewingScene;
import org.junit.Test;
import static org.junit.Assert.*;

public class SceneFrameStateTest {
    @Test public void completedRenderIsRefusedAfterResizeNewSelectionOrStop() {
        SceneFrameState frames = new SceneFrameState();
        frames.start(31); frames.choose(31, 4, ViewingScene.NIGHT);
        assertTrue(frames.canPublish(31, 0, 4));
        frames.resize(1);
        assertFalse(frames.canPublish(31, 0, 4));
        frames.choose(31, 5, ViewingScene.GAME);
        assertFalse(frames.canPublish(31, 1, 4));
        assertTrue(frames.canPublish(31, 1, 5));
        frames.stop();
        assertFalse(frames.canPublish(31, 1, 5));
        frames.start(32); frames.choose(32, 5, ViewingScene.GAME);
        assertFalse(frames.canPublish(31, 0, 5));
    }

    @Test public void onlyAnAcceptedCurrentRevisionInTheActiveSessionCanBeReported() {
        SceneFrameState frames = new SceneFrameState();
        frames.start(17);
        assertTrue(frames.choose(17, 2, ViewingScene.NIGHT));
        assertFalse(frames.choose(17, 1, ViewingScene.GAME));
        assertFalse(frames.accept(17, 2, false));
        assertEquals(-1, frames.applied());
        assertTrue(frames.accept(17, 2, true));
        assertFalse(frames.accept(17, 2, true)); // heartbeat: no duplicate acknowledgement
        frames.resize(1);
        assertEquals(-1, frames.applied());
        frames.stop(); frames.start(18);
        assertFalse(frames.choose(17, 3, ViewingScene.GAME));
        assertFalse(frames.accept(17, 2, true));
        assertEquals(ViewingScene.CUSTOM, frames.scene());
    }
}
