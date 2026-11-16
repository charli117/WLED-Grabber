package com.abrenoch.wledgrabber.common.network.wled;

/** Observed channel hint; mixed/CCT buses are never treated as verified homogeneous output. */
public enum ChannelHint {
    RGB,
    RGBW,
    MIXED_OR_CCT,
    UNKNOWN
}
