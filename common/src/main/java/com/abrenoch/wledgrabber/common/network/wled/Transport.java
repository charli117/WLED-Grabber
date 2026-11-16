package com.abrenoch.wledgrabber.common.network.wled;

import com.abrenoch.wledgrabber.common.led.Protocol;
import com.abrenoch.wledgrabber.common.led.RgbwMode;

/** Resolved first-version transport; there is deliberately no type-5 option. */
public enum Transport {
    DRGB,
    DNRGB,
    DDP;

    public static Transport resolve(Protocol protocol, RgbwMode rgbwMode, int ledCount) {
        if (protocol == null || rgbwMode == null || rgbwMode == RgbwMode.UNCONFIRMED) {
            return null;
        }
        if (rgbwMode == RgbwMode.RGBW) {
            return protocol == Protocol.DNRGB ? null : DDP;
        }
        if (protocol == Protocol.DDP) {
            return DDP;
        }
        if (protocol == Protocol.DNRGB) {
            return DNRGB;
        }
        if (ledCount <= 0) {
            return null;
        }
        return ledCount == 1 ? DRGB : DNRGB;
    }
}
