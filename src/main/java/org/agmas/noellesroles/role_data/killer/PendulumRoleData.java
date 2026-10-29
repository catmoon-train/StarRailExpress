package org.agmas.noellesroles.role_data.killer;

import java.util.UUID;

import org.agmas.noellesroles.game.command_block.LegendFx;
import org.agmas.noellesroles.game.command_block.PendulumRole;
import org.agmas.noellesroles.game.command_block.StandSpots;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** 钟摆：五秒后把标记摆回原点。摆成之后有一小段时间能用秒针收掉。 */
public class PendulumRoleData extends SimpleRoleData {
    private UUID marked;
    private Vec3 spot;
    private boolean pending;
    private long returnAt;
    private long returnedUntil;

    public PendulumRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean isPending() {
        return pending;
    }

    public boolean canFinish(ServerPlayer target, long now) {
        return target != null && marked != null && marked.equals(target.getUUID()) && now <= returnedUntil;
    }

    public boolean mark(ServerPlayer target, long now) {
        marked = target.getUUID();
        spot = target.position();
        pending = true;
        returnAt = now + PendulumRole.SWING_DELAY;
        returnedUntil = 0;
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, PendulumRole.SWING_DELAY, 0));
        target.displayClientMessage(Component.translatable("message.noellesroles.pendulum.marked_you")
                .withStyle(ChatFormatting.GOLD), true);
        if (player instanceof ServerPlayer self) {
            self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.marked",
                    target.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            if (self.level() instanceof ServerLevel level) {
                LegendFx.pair(level, self.position(), target.position(), 1.1F, LegendFx.SWING);
            }
        }
        return true;
    }

    @Override
    public void serverTick() {
        if (!pending || !(player instanceof ServerPlayer self) || !(self.level() instanceof ServerLevel level)) {
            return;
        }
        if (level.getGameTime() < returnAt) {
            return;
        }
        pending = false;
        ServerPlayer target = marked == null ? null : level.getServer().getPlayerList().getPlayer(marked);
        if (target == null || spot == null || !GameUtils.isPlayerAliveAndSurvival(target)
                || target.distanceToSqr(spot) > PendulumRole.RETURN_LIMIT * PendulumRole.RETURN_LIMIT) {
            fail(self);
            return;
        }
        Vec3 back = StandSpots.find(level, spot, new Vec3(0.0D, 0.0D, 1.0D), 1.0D);
        if (back == null) {
            back = spot;
        }
        target.teleportTo(back.x, back.y, back.z);
        if (target.connection != null) {
            target.connection.teleport(back.x, back.y, back.z, target.getYRot(), target.getXRot());
        }
        target.resetFallDistance();
        target.setDeltaMovement(Vec3.ZERO);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0));
        self.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0));
        returnedUntil = level.getGameTime() + PendulumRole.RETURN_WINDOW;
        level.playSound(null, back.x, back.y, back.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.7F);
        LegendFx.pair(level, self.position(), back, 1.4F, LegendFx.SWING);
        target.displayClientMessage(Component.translatable("message.noellesroles.pendulum.pulled")
                .withStyle(ChatFormatting.GOLD), true);
        self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.returned",
                target.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
    }

    private void fail(ServerPlayer self) {
        self.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
        self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.failed")
                .withStyle(ChatFormatting.GRAY), true);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("Pending", pending);
        tag.putLong("ReturnAt", returnAt);
        tag.putLong("ReturnedUntil", returnedUntil);
        tag.putBoolean("HasMark", marked != null);
        if (marked != null) {
            tag.putUUID("Marked", marked);
        }
        tag.putBoolean("HasSpot", spot != null);
        if (spot != null) {
            tag.putDouble("X", spot.x);
            tag.putDouble("Y", spot.y);
            tag.putDouble("Z", spot.z);
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        pending = tag.getBoolean("Pending");
        returnAt = tag.getLong("ReturnAt");
        returnedUntil = tag.getLong("ReturnedUntil");
        marked = tag.getBoolean("HasMark") && tag.hasUUID("Marked") ? tag.getUUID("Marked") : null;
        spot = tag.getBoolean("HasSpot") ? new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")) : null;
    }
}
