package com.abrenoch.wledgrabber.common.fixtures;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class RecordingDatagramSinkTest {

    @Test
    public void recordsCopiesOfOffsetsAndLengths() {
        RecordingDatagramSink sink = new RecordingDatagramSink();
        byte[] source = new byte[]{9, 8, 7, 6, 5};

        sink.send(source, 1, 3);
        assertEquals(1, sink.count());
        assertArrayEquals(new byte[]{8, 7, 6}, sink.last());

        source[1] = 0;
        assertArrayEquals(new byte[]{8, 7, 6}, sink.last());
        assertNotSame(source, sink.last());

        sink.clear();
        assertEquals(0, sink.count());
    }
}
