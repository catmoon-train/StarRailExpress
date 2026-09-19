package org.agmas.noellesroles.role_data.vigilante;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.role.vigilante.CavalryRole;
import org.jetbrains.annotations.NotNull;

/**
 * 骑兵（Cavalry）的职业数据。
 * <p>
 * 记录商店购买状态，以及骑乘模组坐骑时的临时护盾状态。
 */
public class CavalryRoleData extends SimpleRoleData {

    /** 是否已经为下界合金矛附魔过突进（一局一次）。 */
    public boolean lungeBought = false;

    /** 当前由骑兵被动持有的护盾是否仍存在。 */
    public boolean mountedShieldActive = false;
    /** 护盾在骑乘期间被击破后，本局不再补充。 */
    public boolean mountedShieldBroken = false;
    private boolean wasRidingCavalryMount = false;
    private long mountedShieldExpiry = -1L;

    public CavalryRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        this.lungeBought = tag.getBoolean("LungeBought");
        this.mountedShieldActive = tag.getBoolean("MountedShieldActive");
        this.mountedShieldBroken = tag.getBoolean("MountedShieldBroken");
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        tag.putBoolean("LungeBought", this.lungeBought);
        tag.putBoolean("MountedShieldActive", this.mountedShieldActive);
        tag.putBoolean("MountedShieldBroken", this.mountedShieldBroken);
    }

    @Override
    public void serverTick() {
        if (!(this.player instanceof ServerPlayer serverPlayer) || !serverPlayer.isAlive()) {
            return;
        }

        boolean ridingCavalryMount = CavalryRole.isCavalryMount(serverPlayer.getVehicle());
        if (ridingCavalryMount && !this.mountedShieldBroken && !this.mountedShieldActive) {
            SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(serverPlayer);
            // 用很长的限时护盾覆盖本次骑乘，离开坐骑时按记录的过期时间精确移除。
            // 这样不会清掉其他职业可能添加的限时护盾。
            armor.addTimedArmor(1, Integer.MAX_VALUE, true);
            this.mountedShieldExpiry = armor.timedArmor.lastKey();
            this.mountedShieldActive = true;
            this.sync();
        } else if (!ridingCavalryMount && this.wasRidingCavalryMount && this.mountedShieldActive) {
            removeMountedShield(serverPlayer);
            this.mountedShieldActive = false;
            this.sync();
        }
        this.wasRidingCavalryMount = ridingCavalryMount;
    }

    /** 只移除骑兵自己添加的限时护盾，不清理其他护盾。 */
    private void removeMountedShield(ServerPlayer serverPlayer) {
        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(serverPlayer);
        if (this.mountedShieldExpiry >= 0 && armor.timedArmor.remove(this.mountedShieldExpiry) != null) {
            armor.sync();
        }
        this.mountedShieldExpiry = -1L;
    }

    /** 护盾事件只在仍骑乘模组坐骑时锁定“已破盾”状态。 */
    public void onShieldBrokenWhileMounted() {
        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(this.player);
        // 事件可能来自其他限时/常驻护盾；只有记录的骑兵护盾条目已经被移除时才锁定。
        boolean ownShieldWasRemoved = this.mountedShieldExpiry >= 0
                && !armor.timedArmor.containsKey(this.mountedShieldExpiry);
        if (this.mountedShieldActive && ownShieldWasRemoved
                && CavalryRole.isCavalryMount(this.player.getVehicle())) {
            if (this.player instanceof ServerPlayer serverPlayer) {
                removeMountedShield(serverPlayer);
            }
            this.mountedShieldActive = false;
            this.mountedShieldBroken = true;
            this.sync();
        }
    }

    @Override
    public void clear() {
        if (this.mountedShieldActive && this.player instanceof ServerPlayer serverPlayer) {
            removeMountedShield(serverPlayer);
        }
        this.mountedShieldActive = false;
        this.wasRidingCavalryMount = false;
        this.mountedShieldExpiry = -1L;
    }
}
