package com.abrenoch.wledgrabber.common.scene;

import com.abrenoch.wledgrabber.common.led.ViewingScene;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class SceneSelectionStoreTest {
    @Test public void observersOnlyReceiveSuccessfulDistinctChoicesAndNewcomersGetTheLastChoice() {
        List<String> writes = new ArrayList<>();
        SceneSelectionStore store = new SceneSelectionStore("future-mode", 7, (id, rev) -> {
            writes.add(id + rev); return !id.equals("CINEMA");
        }, Runnable::run, Runnable::run);
        List<ViewingScene> seen = new ArrayList<>();
        SceneSelectionStore.Listener listener = selected -> seen.add(selected.scene);
        store.observe(listener);
        store.select(ViewingScene.NIGHT, success -> assertTrue(success));
        store.select(ViewingScene.NIGHT, success -> assertTrue(success));
        store.select(ViewingScene.CINEMA, success -> assertFalse(success));
        assertEquals(Arrays.asList("NIGHT8", "CINEMA9"), writes);
        assertEquals(Arrays.asList(ViewingScene.CUSTOM, ViewingScene.NIGHT), seen);
        assertEquals(8, store.current().revision);
        store.remove(listener);
        store.select(ViewingScene.GAME, success -> assertTrue(success));
        store.observe(selected -> assertEquals(ViewingScene.GAME, selected.scene));
        assertEquals(2, seen.size());
    }
}
