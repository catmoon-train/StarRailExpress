package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.SRE;

public class BarrierViewer extends dev.doctor4t.wathe.util.BarrierViewer {
    public static boolean isBarrierVisible() {
        if (SRE.canSeeBarrier())
            return true;
        return dev.doctor4t.wathe.util.BarrierViewer.isBarrierVisible();
    }
}
