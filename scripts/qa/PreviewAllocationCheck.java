import com.abrenoch.wledgrabber.common.led.ClassicLedLayout;
import com.abrenoch.wledgrabber.common.led.CompiledLayout;
import com.abrenoch.wledgrabber.common.led.GapMode;
import com.abrenoch.wledgrabber.common.led.LedLayoutConfig;
import com.abrenoch.wledgrabber.common.views.PreviewModel;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;

/** Host JDK allocation probe; does not measure Android frame latency. */
public final class PreviewAllocationCheck {
    private static PreviewModel model;
    private static volatile int checksum;

    private static void exercise(CompiledLayout layout, int steps, boolean rebuild) {
        for (int i = 0; i < steps; i++) {
            int physical = i % 274;
            model = rebuild ? PreviewModel.build(layout, physical) : model.select(physical);
            checksum += model.selectedLogical();
        }
    }

    public static void main(String[] args) {
        LedLayoutConfig config = new LedLayoutConfig(86, 90, 49, 49, 0, 0, 161, true,
                GapMode.SKIP, 8, 5, 0, 0, 0, 0, 100, 0, 100, 100, 0, 100);
        CompiledLayout layout = new ClassicLedLayout().compile(config, 274, 640, 360).value();
        ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        for (boolean rebuild : new boolean[] {true, false}) {
            model = PreviewModel.build(layout, 0);
            checksum = 0;
            exercise(layout, 10_000, rebuild);
            long before = bean.getThreadAllocatedBytes(thread);
            exercise(layout, 50_000, rebuild);
            long delta = bean.getThreadAllocatedBytes(thread) - before;
            System.out.printf("%s: %.1f bytes/step, checksum=%d%n",
                    rebuild ? "rebuild" : "select", delta / 50_000.0, checksum);
        }
    }
}
