package org.agmas.noellesroles.role_data.killer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.agmas.noellesroles.game.command_block.CandleShadeRole;
import org.agmas.noellesroles.game.command_block.LegendFx;
import org.agmas.noellesroles.game.command_block.item.GazeRays;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** 烛影：烛火亮着时，对方看着你会把火吹灭，并让你短暂失明。 */
public class CandleShadeRoleData extends SimpleRoleData {
    private final Set<UUID> marked = new HashSet<>();
    private long markUntil;

    public CandleShadeRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean hasMark() {
        return !marked.isEmpty() && player.level().getGameTime() < markUntil;
    }

    public boolean isMark(ServerPlayer target, long now) {
        return target != null && marked.contains(target.getUUID()) && now < markUntil;
    }

    public boolean lookingAtFlame(ServerPlayer self, ServerPlayer target) {
        return target != null && self != null
                && target.distanceToSqr(self) <= CandleShadeRole.SNUFF_RANGE * CandleShadeRole.SNUFF_RANGE
                && GazeRays.lookingAt(target, self, CandleShadeRole.SNUFF_DOT);
    }

    public boolean light(ServerPlayer target, long now) {
        if (now >= markUntil) {
            clearMark();
        }
        marked.add(target.getUUID());
        markUntil = now + CandleShadeRole.MARK_TICKS;
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, CandleShadeRole.MARK_TICKS, 0));
        target.displayClientMessage(Component.translatable("message.noellesroles.candle_shade.lit_you")
                .withStyle(ChatFormatting.GOLD), true);
        if (player instanceof ServerPlayer self) {
            self.displayClientMessage(Component.translatable("message.noellesroles.candle_shade.lit",
                    target.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            if (self.level() instanceof ServerLevel level) {
                LegendFx.pair(level, self.position(), target.position(), 1.1F, LegendFx.CANDLE);
            }
        }
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.5F, 1.4F);
        return true;
    }

    public boolean extend(ServerPlayer self, long now) {
        if (!hasMark()) {
            return false;
        }
        markUntil = Math.max(markUntil, now) + 4 * 20;
        int left = (int) Math.max(1L, markUntil - now);
        for (UUID id : marked) {
            ServerPlayer target = self.serverLevel().getServer().getPlayerList().getPlayer(id);
            if (target != null && GameUtils.isPlayerAliveAndSurvival(target)) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, left, 0));
            }
        }
        self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0));
        self.displayClientMessage(Component.translatable("message.noellesroles.candle_shade.oiled")
                .withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    @Override
    public void serverTick() {
        if (marked.isEmpty() || !(player instanceof ServerPlayer self) || !(self.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now >= markUntil) {
            clearMark();
            return;
        }
        for (UUID id : Set.copyOf(marked)) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(id);
            if (target == null || target.level() != level || !GameUtils.isPlayerAliveAndSurvival(target)) {
                marked.remove(id);
                continue;
            }
            if (lookingAtFlame(self, target)) {
                snuff(self, target);
            }
        }
    }

    private void snuff(ServerPlayer self, ServerPlayer watcher) {
        marked.remove(watcher.getUUID());
        watcher.removeEffect(MobEffects.GLOWING);
        self.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20, 0));
        if (self.level() instanceof ServerLevel level) {
            LegendFx.ring(level, self.position(), 1.0F, LegendFx.CANDLE);
        }
        self.level().playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.FIRE_EXTINGUISH,
                SoundSource.PLAYERS, 0.6F, 1.2F);
        self.displayClientMessage(Component.translatable("message.noellesroles.candle_shade.snuffed",
                watcher.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
        watcher.displayClientMessage(Component.translatable("message.noellesroles.candle_shade.blew_out")
                .withStyle(ChatFormatting.GOLD), true);
    }

    private void clearMark() {
        marked.clear();
        markUntil = 0;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putLong("MarkUntil", markUntil);
        ListTag targets = new ListTag();
        for (UUID id : marked) {
            CompoundTag target = new CompoundTag();
            target.putUUID("UUID", id);
            targets.add(target);
        }
        tag.put("MarkedPlayers", targets);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        markUntil = tag.getLong("MarkUntil");
        marked.clear();
        ListTag targets = tag.getList("MarkedPlayers", Tag.TAG_COMPOUND);
        for (int i = 0; i < targets.size(); i++) {
            CompoundTag target = targets.getCompound(i);
            if (target.hasUUID("UUID")) {
                marked.add(target.getUUID("UUID"));
            }
        }
    }
}