package org.agmas.noellesroles.game.halloween.pumpkin;

/** 所有成功转入量统一按入账位判断；已领取物的转移永远为零。 */
public final class PumpkinAcquisition {
    private PumpkinAcquisition() {}
    public static int creditable(boolean settled, int before, int remaining) {
        if (settled || before <= 0) return 0;
        return Math.max(0, before - Math.max(0, Math.min(before, remaining)));
    }
}
