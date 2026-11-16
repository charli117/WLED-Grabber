package com.abrenoch.wledgrabber.common.led;

import com.abrenoch.wledgrabber.common.util.FieldError;
import com.abrenoch.wledgrabber.common.util.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure Classic four-side geometry. Uses the clipped full-precision rectangles;
 * four-decimal rounding is only for reference/golden output, never for admission.
 */
public final class ClassicLedLayout {
    private static final double EPS = 1e-12;

    public ValidationResult<CompiledLayout> compile(LedLayoutConfig config,
                                                    int captureWidth, int captureHeight) {
        int physicalLedCount = config == null ? 0 : Math.max(config.effectiveLedCount(), 1);
        return compile(config, physicalLedCount, captureWidth, captureHeight);
    }

    public ValidationResult<CompiledLayout> compile(LedLayoutConfig config, int physicalLedCount,
                                                    int captureWidth, int captureHeight) {
        if (config == null) {
            return fail("layout config missing");
        }
        if (captureWidth <= 0 || captureHeight <= 0) {
            return fail("capture dimensions must be positive");
        }
        int top = config.top();
        int right = config.right();
        int bottom = config.bottom();
        int left = config.left();
        if (top + right + bottom + left <= 0) {
            return fail("at least one side must contain LEDs");
        }

        double tlH = config.cornerTlH() / 100.0;
        double tlV = config.cornerTlV() / 100.0;
        double trH = config.cornerTrH() / 100.0;
        double trV = config.cornerTrV() / 100.0;
        double brH = config.cornerBrH() / 100.0;
        double brV = config.cornerBrV() / 100.0;
        double blH = config.cornerBlH() / 100.0;
        double blV = config.cornerBlV() / 100.0;

        if (!inUnit(tlH) || !inUnit(tlV) || !inUnit(trH) || !inUnit(trV)
                || !inUnit(brH) || !inUnit(brV) || !inUnit(blH) || !inUnit(blV)) {
            return fail("corner coordinates must stay inside [0,1]");
        }
        if (!(trH > tlH && brH > blH && blV > tlV && brV > trV)) {
            return fail("top/bottom must advance in h and left/right in v");
        }
        if (!convexAndNonSelfIntersecting(tlH, tlV, trH, trV, brH, brV, blH, blV)) {
            return fail("corners must form a convex, non-self-intersecting quadrilateral");
        }

        double edgeVGap = config.edgeGap() / 100.0 / 2.0;
        double edgeHGap = edgeVGap * captureHeight / captureWidth;
        double depthH = config.horizontalDepth() / 100.0;
        double depthV = config.verticalDepth() / 100.0;
        double overlap = config.overlap() / 100.0;

        List<FieldError> errors = new ArrayList<>();
        List<LedSampleRect> rects = new ArrayList<>();

        if (top > 0) {
            double available = trH - tlH - 2 * edgeHGap;
            if (available <= EPS) {
                errors.add(error("top edge has no tangential room after Edge Gap"));
            } else {
                double stepH = available / top;
                double stepV = (trV - tlV) / top;
                for (int i = 0; i < top; i++) {
                    double hmin = clamp(tlH + stepH * i + edgeHGap - overlap);
                    double hmax = clamp(tlH + stepH * (i + 1) + edgeHGap + overlap);
                    double vmin = tlV + stepV * i;
                    double vmax = Math.min(1.0, vmin + depthH);
                    addRect(rects, errors, hmin, hmax, vmin, vmax, Side.TOP, i);
                }
            }
        }

        if (right > 0) {
            double available = brV - trV - 2 * edgeVGap;
            if (available <= EPS) {
                errors.add(error("right edge has no tangential room after Edge Gap"));
            } else {
                double stepH = (brH - trH) / right;
                double stepV = available / right;
                for (int i = 0; i < right; i++) {
                    double hmax = trH + stepH * (i + 1);
                    double hmin = Math.max(0.0, hmax - depthV);
                    double vmin = clamp(trV + stepV * i + edgeVGap - overlap);
                    double vmax = clamp(trV + stepV * (i + 1) + edgeVGap + overlap);
                    addRect(rects, errors, hmin, hmax, vmin, vmax, Side.RIGHT, top + i);
                }
            }
        }

        if (bottom > 0) {
            double available = brH - blH - 2 * edgeHGap;
            if (available <= EPS) {
                errors.add(error("bottom edge has no tangential room after Edge Gap"));
            } else {
                double stepH = available / bottom;
                double stepV = (brV - blV) / bottom;
                for (int j = 0; j < bottom; j++) {
                    int i = bottom - 1 - j;
                    double hmin = clamp(blH + stepH * i + edgeHGap - overlap);
                    double hmax = clamp(blH + stepH * (i + 1) + edgeHGap + overlap);
                    double vmax = blV + stepV * i;
                    double vmin = Math.max(0.0, vmax - depthH);
                    addRect(rects, errors, hmin, hmax, vmin, vmax, Side.BOTTOM,
                            top + right + j);
                }
            }
        }

        if (left > 0) {
            double available = blV - tlV - 2 * edgeVGap;
            if (available <= EPS) {
                errors.add(error("left edge has no tangential room after Edge Gap"));
            } else {
                double stepH = (blH - tlH) / left;
                double stepV = available / left;
                for (int j = 0; j < left; j++) {
                    int i = left - 1 - j;
                    double hmin = tlH + stepH * i;
                    double hmax = Math.min(1.0, hmin + depthV);
                    double vmin = clamp(tlV + stepV * i + edgeVGap - overlap);
                    double vmax = clamp(tlV + stepV * (i + 1) + edgeVGap + overlap);
                    addRect(rects, errors, hmin, hmax, vmin, vmax, Side.LEFT,
                            top + right + bottom + j);
                }
            }
        }

        ValidationResult<LedMapping> mappingResult = LedMapping.compile(config, physicalLedCount);
        if (!mappingResult.isValid()) {
            errors.addAll(mappingResult.errors());
        }
        if (!errors.isEmpty()) {
            return ValidationResult.fail(errors);
        }
        return ValidationResult.ok(new CompiledLayout(rects, captureWidth, captureHeight, 0,
                mappingResult.value(), tlH, tlV, trH, trV, brH, brV, blH, blV));
    }

    private static void addRect(List<LedSampleRect> rects, List<FieldError> errors,
                                double hmin, double hmax, double vmin, double vmax,
                                Side side, int logicalIndex) {
        if (!(hmin < hmax && vmin < vmax)) {
            errors.add(error(side.name().toLowerCase() + " slot " + logicalIndex
                    + " becomes empty or inverted after clipping"));
            return;
        }
        rects.add(new LedSampleRect(hmin, hmax, vmin, vmax, side, logicalIndex));
    }

    private static boolean convexAndNonSelfIntersecting(double tlH, double tlV,
                                                        double trH, double trV,
                                                        double brH, double brV,
                                                        double blH, double blV) {
        double[] x = {tlH, trH, brH, blH};
        double[] y = {tlV, trV, brV, blV};
        double area = 0.0;
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            area += x[i] * y[next] - x[next] * y[i];
        }
        if (Math.abs(area) < EPS) {
            return false;
        }
        int sign = 0;
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            int after = (i + 2) % 4;
            double cross = (x[next] - x[i]) * (y[after] - y[next])
                    - (y[next] - y[i]) * (x[after] - x[next]);
            if (Math.abs(cross) < EPS) {
                continue;
            }
            int current = cross > 0 ? 1 : -1;
            if (sign == 0) {
                sign = current;
            } else if (sign != current) {
                return false;
            }
        }
        return true;
    }

    private static double clamp(double value) {
        return Math.min(1.0, Math.max(0.0, value));
    }

    private static boolean inUnit(double value) {
        return value >= 0.0 && value <= 1.0;
    }

    private static FieldError error(String detail) {
        return new FieldError(com.abrenoch.wledgrabber.common.led.ConfigFields.LAYOUT_GEOMETRY,
                FieldError.Code.CROSS_FIELD, detail);
    }

    private static ValidationResult<CompiledLayout> fail(String detail) {
        return ValidationResult.fail(error(detail));
    }
}
