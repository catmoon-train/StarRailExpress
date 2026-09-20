package org.agmas.noellesroles.api;

import net.minecraft.world.phys.Vec3;

/**
 * 单个玩家的攀爬状态。
 *
 * <p>刻意**不放进 RoleData**，而是和体力条（{@code PlayerEntityMixin.sprintingTicks}）一样
 * 挂在 Player 自己身上（见 {@link PlayerClimbState} / {@code PlayerClimbStateMixin}）：
 * <ul>
 * <li>任何职业只要 {@code SRERole#canClimbWalls(Player)} 返回 true 就能用，不必绑定攀爬用的 RoleData；</li>
 * <li>已经有自己 RoleData 的职业（冒险家）不用为了攀爬去改自己的 RoleData。</li>
 * </ul>
 *
 * <p>两端各自持有一份、互不同步：客户端只用 {@link #predicting} 做本地预判，
 * 服务端只用 {@link #climbing} 维护权威状态 —— 与体力条「两端各自模拟、不过度发包」的思路一致。
 */
public final class ClimbState {

    /** 服务端：该玩家是否正在攀爬（服务端权威） */
    public boolean climbing = false;

    /** 客户端：本地预判自己正在攀爬 */
    public boolean predicting = false;

    /** 当前贴住的墙面法线（由墙指向玩家，水平单位向量） */
    public Vec3 normal = Vec3.ZERO;

    // ── 这一端的位移采样，用来判断「这一 tick 实际往哪个方向动了」 ──
    public double lastX = 0d;
    public double lastY = 0d;
    public double lastZ = 0d;
    public boolean hasLastPos = false;

    /** 这一端是否处于攀爬中（客户端看预判，服务端看权威状态） */
    public boolean active() {
        return climbing || predicting;
    }

    public void reset() {
        climbing = false;
        predicting = false;
        normal = Vec3.ZERO;
        hasLastPos = false;
    }

    /** 取出离上一个采样点的位移并刷新采样点（尚未采样时返回 0,0,0） */
    public double[] pollMovement(double x, double y, double z) {
        if (!hasLastPos) {
            markPosition(x, y, z);
            return new double[] { 0d, 0d, 0d };
        }
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        markPosition(x, y, z);
        return new double[] { dx, dy, dz };
    }

    public void markPosition(double x, double y, double z) {
        lastX = x;
        lastY = y;
        lastZ = z;
        hasLastPos = true;
    }
}
