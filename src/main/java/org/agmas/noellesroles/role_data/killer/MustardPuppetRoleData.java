package org.agmas.noellesroles.role_data.killer;

import java.util.UUID;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.command_block.MustardRole;

/** 玩家傀儡的主人与升级次数，随当前职业的生命周期清理。 */
public class MustardPuppetRoleData extends SimpleRoleData {
    public UUID owner;
    public int upgrades;

    public MustardPuppetRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer self) || !GameUtils.isPlayerAliveAndSurvival(self)
                || !io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(self.level()).isRunning()) {
            return;
        }
        ServerPlayer master = owner == null ? null : self.server.getPlayerList().getPlayer(owner);
        if (master == null || !GameUtils.isPlayerAliveAndSurvival(master)
                || !MustardRole.isMustard(master)) {
            MustardRole.endPuppet(self);
        }
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer self) {
            EntityDisguise.clear(self);
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Upgrades", upgrades);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        upgrades = tag.getInt("Upgrades");
    }
}
