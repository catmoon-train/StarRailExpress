package org.agmas.noellesroles.role_data.killer;

import org.agmas.noellesroles.game.command_block.CommandBlockCombat;
import org.agmas.noellesroles.game.command_block.FarlandsRole;
import org.agmas.noellesroles.game.command_block.LegendFx;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** 远境：交换后短暂打不出手，裂隙要自己走开。 */
public class FarlandsRoleData extends SimpleRoleData {
    public static final int SETTLE_TICKS = 5 * 20;
    public static final int RIFT_DELAY = 30;
    public static final double RIFT_RADIUS = 2.2D;

    private long settleUntil;
    private boolean riftArmed;
    private double riftX;
    private double riftY;
    private double riftZ;
    private long riftAt;

    public FarlandsRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean settling(long now) {
        return now < settleUntil;
    }

    public void settle(long now) {
        settleUntil = now + SETTLE_TICKS;
        sync();
    }

    public boolean armRift(Vec3 pos, long now) {
        if (riftArmed) {
            return false;
        }
        riftArmed = true;
        riftX = pos.x;
        riftY = pos.y;
        riftZ = pos.z;
        riftAt = now + RIFT_DELAY;
        sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer self) || !riftArmed || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return;
        }
        long now = self.serverLevel().getGameTime();
        if (now < riftAt) {
            return;
        }
        riftArmed = false;
        Vec3 at = new Vec3(riftX, riftY, riftZ);
        ServerLevel level = self.serverLevel();
        LegendFx.ring(level, at, 2.6F, LegendFx.BOOM);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.35F, 1.4F);
        int caught = 0;
        for (ServerPlayer other : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(other) || other.distanceToSqr(at) > RIFT_RADIUS * RIFT_RADIUS) {
                continue;
            }
            boolean killed = CommandBlockCombat.tryKill(self, other, FarlandsRole.DEATH_RIFT, false,
                    other == self ? null : Component.translatable("message.noellesroles.farlands.blocked"));
            if (killed) {
                caught++;
            }
        }
        self.displayClientMessage(Component.translatable(
                caught == 0 ? "message.noellesroles.farlands.rift_empty" : "message.noellesroles.farlands.rift_hit",
                caught).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        sync();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("settle", settleUntil);
        tag.putBoolean("rift", riftArmed);
        tag.putDouble("rx", riftX);
        tag.putDouble("ry", riftY);
        tag.putDouble("rz", riftZ);
        tag.putLong("riftAt", riftAt);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        settleUntil = tag.getLong("settle");
        riftArmed = tag.getBoolean("rift");
        riftX = tag.getDouble("rx");
        riftY = tag.getDouble("ry");
        riftZ = tag.getDouble("rz");
        riftAt = tag.getLong("riftAt");
    }
}
