package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * 中控员的每人状态。
 *
 * <p>
 * 只记录「属于玩家自己、且统一技能系统不负责」的信息：
 * <ul>
 * <li>激光模块本局是否已经安装过（一局仅 1 次，与单个摄像头的 120 秒冷却互相独立）。</li>
 * </ul>
 * 三个技能的冷却走统一技能系统（{@code RoleSkill} / {@code SREAbilityPlayerComponent}），
 * 这里不再重复记录。
 *
 * <p>
 * 摄像头被改造后的状态也不在这里 —— 它属于方块状态，存在 {@code CameraBlockEntity} 上，
 * 并随每局开始 / 结束重置。
 */
public class CentralControlRoleData extends SimpleRoleData {

    /** 激光模块本局是否已经用掉（一局仅 1 次）。 */
    public boolean laserModuleUsed = false;

    public CentralControlRoleData(RoleDataContext context) {
        super(context);
    }

    /** 只同步给本人：「本局是否已用」只是自己要看的。 */
    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("laser_used", this.laserModuleUsed);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.laserModuleUsed = tag.getBoolean("laser_used");
    }
}
