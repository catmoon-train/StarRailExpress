package net.exmo.sre.camera;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 高级相机轨道中的单个关键帧节点。
 *
 * <p>一条轨道由若干节点组成。播放时相机会从上一节点的状态在 {@link #durationTicks} 个 tick 内平滑过渡到本节点，
 * 到达后再停留 {@link #holdTicks} 个 tick，随后进入下一节点。第一个节点通常 {@code durationTicks=0}（瞬间到位）。
 */
public final class AdvancedCameraNode {

    /** 从上一节点过渡到本节点所需的时间（tick）。第一个节点通常为 0（瞬间到位）。 */
    public final int durationTicks;
    /** 到达本节点后停留的时间（tick）。 */
    public final int holdTicks;
    /** 相机世界坐标位置；为 {@code null} 时沿用上一节点（或玩家眼睛）位置。 */
    @Nullable
    public final Vec3 pos;
    /** 相机注视的世界坐标点；非空时忽略 {@link #yaw}/{@link #pitch}，实时朝向该点。 */
    @Nullable
    public final Vec3 lookAt;
    /** 显式偏航角（度）；仅在 {@link #lookAt} 为空时使用，{@code null} 表示沿用上一节点角度。 */
    @Nullable
    public final Float yaw;
    /** 显式俯仰角（度）；仅在 {@link #lookAt} 为空时使用，{@code null} 表示沿用上一节点角度。 */
    @Nullable
    public final Float pitch;
    /** FOV 覆盖值（度）；{@code <=0} 表示不覆盖，使用默认 FOV。 */
    public final float fov;

    public AdvancedCameraNode(int durationTicks, int holdTicks, @Nullable Vec3 pos, @Nullable Vec3 lookAt,
                              @Nullable Float yaw, @Nullable Float pitch, float fov) {
        this.durationTicks = Math.max(0, durationTicks);
        this.holdTicks = Math.max(0, holdTicks);
        this.pos = pos;
        this.lookAt = lookAt;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fov = fov;
    }

    /** 本节点占用的总时长（过渡 + 停留）。 */
    public int totalTicks() {
        return durationTicks + holdTicks;
    }
}
