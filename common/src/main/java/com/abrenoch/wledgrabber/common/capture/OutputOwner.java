package com.abrenoch.wledgrabber.common.capture;

/** Only one owner may drive the physical output at a time. */
public enum OutputOwner {
    NONE,
    CAPTURE,
    TEST
}
