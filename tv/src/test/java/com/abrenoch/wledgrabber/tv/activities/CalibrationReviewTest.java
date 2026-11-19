package com.abrenoch.wledgrabber.tv.activities;

import org.junit.Test;
import static org.junit.Assert.*;

public class CalibrationReviewTest {
    @Test public void oldReceiptsAndDuplicateAnswersCannotSkipAnEdge() {
        CalibrationReview review = new CalibrationReview();
        review.startEdges();
        long stale = review.nextFrame();
        long current = review.nextFrame();
        assertFalse(review.acknowledge(stale));
        assertFalse(review.confirmEdge(0));
        assertTrue(review.acknowledge(current));
        assertFalse(review.confirmEdge(1));
        assertTrue(review.confirmEdge(0));
        assertFalse(review.acknowledge(current));
        assertFalse(review.confirmEdge(0));
        assertFalse(review.confirmEdge(1));
        for (int edge = 1; edge < 4; edge++) {
            assertFalse(review.readyToSave());
            assertTrue(review.acknowledge(review.nextFrame()));
            assertTrue(review.confirmEdge(edge));
        }
        assertTrue(review.readyToSave());
        review.seek();
        assertFalse(review.readyToSave());
        assertFalse(review.acknowledge(current));
    }
}
