package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.neutral.priest.PriestHeavenManager;
import org.agmas.noellesroles.game.roles.neutral.priest.PriestLyrics;
import org.jetbrains.annotations.NotNull;

public class PriestRoleData extends SimpleRoleData {

    public int lyricIndex;
    public int sprintTicks;
    /** 打开咏诵 GUI 后自动奔跑 */
    public boolean autoSprint;
    public PriestRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayer serverPlayer && PriestHeavenManager.isPriest(serverPlayer)) {
            PriestHeavenManager.tickMobility(serverPlayer);
        }
    }
    @Override
    public void clientTick() {
        if (autoSprint) {
            player.setSprinting(true);
        }
        if (PriestHeavenManager.isMovingForSpeedRamp(player) || autoSprint) {
            sprintTicks = Math.min(PriestHeavenManager.SPEED_RAMP_TICKS, sprintTicks + 1);
        } else {
            sprintTicks = Math.max(0, sprintTicks - 2);
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("lyricIndex", lyricIndex);
        tag.putBoolean("autoSprint", autoSprint);
        tag.putInt("sprintTicks", sprintTicks);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        lyricIndex = tag.contains("lyricIndex") ? tag.getInt("lyricIndex") : 0;
        if (lyricIndex < 0 || lyricIndex > PriestLyrics.COUNT) {
            lyricIndex = 0;
        }
        autoSprint = tag.contains("autoSprint") && tag.getBoolean("autoSprint");
        sprintTicks = tag.contains("sprintTicks") ? tag.getInt("sprintTicks") : sprintTicks;
    }
}
