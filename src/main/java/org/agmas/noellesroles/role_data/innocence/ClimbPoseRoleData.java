package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;

/**
 * 攀爬职业的「动作姿态」数据（站立 / 匍匐）。
 *
 * <p>攀爬本身的运行状态已经不放在 RoleData 里了（见 {@code ClimbState} / {@code PlayerClimbState}，
 * 挂在 Player 上）；这里只保留**技能切换出来的姿态**——它是职业功能、
 * 而且需要服务端同步给本人，所以仍然用 RoleData。
 */
public class ClimbPoseRoleData extends SimpleRoleData {

    /** 默认动作（站立，交由原版 {@code updatePlayerPose} 决定） */
    public static final int POSE_DEFAULT = 0;
    /** 匍匐姿态（原版趴下 / 游泳姿态） */
    public static final int POSE_PRONE = 1;

    private static final Pose[] POSES = {
            Pose.STANDING, Pose.SWIMMING
    };

    private int poseId = POSE_DEFAULT;

    public ClimbPoseRoleData(RoleDataContext context) {
        super(context);
    }

    public int poseId() {
        return poseId;
    }

    /** 是否有自定义姿态（默认姿态交给原版处理） */
    public boolean hasCustomPose() {
        return poseId > POSE_DEFAULT && poseId < POSES.length;
    }

    /** 当前应当被强制保持的姿态 */
    public Pose currentPose() {
        return POSES[Math.floorMod(poseId, POSES.length)];
    }

    /** 设置姿态并同步（服务端调用） */
    public void setPoseId(int id) {
        int clamped = Math.floorMod(id, POSES.length);
        if (this.poseId == clamped) {
            return;
        }
        this.poseId = clamped;
        if (player.level() != null && !player.level().isClientSide()) {
            // 立刻应用到服务端实体（updatePlayerPose 的取消由 ScoutPoseMixin 负责），再同步给本人
            player.setPose(currentPose());
            sync();
        }
    }

    @Override
    public void clear() {
        poseId = POSE_DEFAULT;
        player.setPose(Pose.STANDING);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer serverPlayer) {
        return serverPlayer == this.player;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("pose", poseId);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        poseId = tag.getInt("pose");
    }
}
