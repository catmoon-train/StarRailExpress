package org.agmas.noellesroles.role_data.killer;

import java.util.UUID;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** 丝线人：线上只有一个人，过期就断。 */
public class StringerRoleData extends SimpleRoleData {
    private UUID mark;
    private long markUntil;

    public StringerRoleData(RoleDataContext context) {
        super(context);
    }

    public void mark(UUID id, long until) {
        mark = id;
        markUntil = until;
        sync();
    }

    public void extend(long until) {
        if (mark == null) {
            return;
        }
        markUntil = Math.max(markUntil, until);
        sync();
    }

    public ServerPlayer current(ServerPlayer self) {
        if (self == null || mark == null) {
            return null;
        }
        long now = self.serverLevel().getGameTime();
        if (now >= markUntil) {
            return null;
        }
        var server = self.level().getServer();
        if (server == null) {
            return null;
        }
        if (!(server.getPlayerList().getPlayer(mark) instanceof ServerPlayer target)) {
            return null;
        }
        if (target.level() != self.level() || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return null;
        }
        return target;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("has", mark != null);
        if (mark != null) {
            tag.putUUID("mark", mark);
        }
        tag.putLong("until", markUntil);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        markUntil = tag.getLong("until");
        mark = tag.getBoolean("has") && tag.hasUUID("mark") ? tag.getUUID("mark") : null;
    }
}