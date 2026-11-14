package org.agmas.noellesroles.role_data.killer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.agmas.noellesroles.game.command_block.LegendFx;
import org.agmas.noellesroles.game.command_block.PendulumRole;
import org.agmas.noellesroles.game.command_block.PendulumRules;
import org.agmas.noellesroles.init.ModEffects;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
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

/** 钟摆单局状态：定钟轨迹、永久加时、逐层充能以及互不覆盖的定摆标记。 */
public class PendulumRoleData extends SimpleRoleData {
    private record Position(long tick, ServerLevel level, Vec3 pos) {
    }

    private record Mark(ServerLevel level, Vec3 spot, long returnAt) {
    }

    private record Returned(ServerLevel level, long until) {
    }

    private final List<Position> history = new ArrayList<>();
    private final Map<UUID, Mark> pending = new HashMap<>();
    private final Map<UUID, Returned> returned = new HashMap<>();
    private PendulumRules.Charges charges = new PendulumRules.Charges(PendulumRules.MAX_CHARGES, 0L);
    private volatile boolean clockActive;
    private long clockStartedAt;
    private volatile long clockEndsAt;
    private long clockHits;
    private long bonusTicks;

    public PendulumRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean hasActiveClock() {
        return clockActive;
    }

    /** 公共击杀入口可能异步读取，因此保护开关与结束时刻使用可见性保证。 */
    public boolean isClockProtecting(long now) {
        return clockActive && now < clockEndsAt;
    }

    public boolean startClock(long now) {
        if (!(player instanceof ServerPlayer self) || clockActive) {
            return false;
        }
        history.clear();
        clockStartedAt = now;
        clockEndsAt = now + PendulumRole.CLOCK_DURATION + bonusTicks;
        clockHits = 0L;
        recordPosition(self, now);
        clockActive = true;
        self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.clock_started",
                (PendulumRole.CLOCK_DURATION + bonusTicks) / 20L).withStyle(ChatFormatting.GOLD), true);
        sync();
        return true;
    }

    /** 伤害在事件中被否决，不依赖减伤药水；同一 tick 的多次独立攻击也分别计数。 */
    public boolean absorbClockHit(long now) {
        if (!isClockProtecting(now) || !(player instanceof ServerPlayer self)) {
            return false;
        }
        clockHits++;
        self.addEffect(ModEffects.of(ModEffects.MOVE_BANED, 20, 0, false, true, true));
        sync();
        return true;
    }

    /** 钟锤在本局永久叠加；若仍在运行，也给当前这次的结束时刻增加相同的五秒。 */
    public void addClockDuration(long now) {
        if (clockActive && now >= clockEndsAt) {
            finishClock(now);
        }
        bonusTicks += PendulumRole.HAMMER_EXTENSION;
        if (clockActive) {
            clockEndsAt += PendulumRole.HAMMER_EXTENSION;
        }
        player.displayClientMessage(Component.translatable("message.noellesroles.pendulum.hammer_extended",
                (PendulumRole.CLOCK_DURATION + bonusTicks) / 20L).withStyle(ChatFormatting.GOLD), true);
        sync();
    }

    public void finishClock(long now) {
        if (!clockActive || !(player instanceof ServerPlayer self)) {
            return;
        }
        recordPosition(self, now);
        long elapsed = Math.max(0L, now - clockStartedAt);
        long rewind = PendulumRules.rewindTicks(elapsed, clockHits);
        long destinationTick = now - rewind;
        clockActive = false;
        if (rewind > 0L && !history.isEmpty()) {
            Position destination = history.get(0);
            for (Position position : history) {
                if (position.tick() > destinationTick) {
                    break;
                }
                destination = position;
            }
            // 只保存和恢复位置，不触碰背包、血量、金币、药水或其他职业数据。
            teleport(self, destination.level(), destination.pos());
        }
        history.clear();
        self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.clock_ended")
                .withStyle(ChatFormatting.GOLD), true);
        sync();
    }

    private void recordPosition(ServerPlayer self, long now) {
        if (!history.isEmpty() && history.get(history.size() - 1).tick() == now) {
            return;
        }
        history.add(new Position(now, self.serverLevel(), self.position()));
    }

    public boolean isPending() {
        return !pending.isEmpty();
    }

    public boolean canFinish(ServerPlayer target, long now) {
        Returned window = target == null ? null : returned.get(target.getUUID());
        return window != null && now < window.until() && target.serverLevel() == window.level()
                && GameUtils.isPlayerAliveAndSurvival(target);
    }

    public boolean mark(ServerPlayer target, long now) {
        if (!(player instanceof ServerPlayer self) || target == self || target.serverLevel() != self.serverLevel()
                || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        charges = charges.recover(now);
        if (charges.count() == 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.recharging",
                    (charges.readyAt() - now + 19L) / 20L), true);
            return false;
        }
        if (pending.containsKey(target.getUUID())) {
            self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.already"), true);
            return false;
        }
        charges = charges.spend(now);
        pending.put(target.getUUID(), new Mark(target.serverLevel(), target.position(), now + PendulumRole.SWING_DELAY));
        returned.remove(target.getUUID());
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, PendulumRole.SWING_DELAY, 0));
        target.displayClientMessage(Component.translatable("message.noellesroles.pendulum.marked_you")
                .withStyle(ChatFormatting.GOLD), true);
        self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.marked", target.getDisplayName())
                .withStyle(ChatFormatting.GOLD), true);
        LegendFx.pair(self.serverLevel(), self.position(), target.position(), 1.1F, LegendFx.SWING);
        sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer self)) {
            return;
        }
        if (!PendulumRole.canUse(self)) {
            clear();
            return;
        }
        long now = self.level().getGameTime();
        if (clockActive) {
            recordPosition(self, now);
            if (now >= clockEndsAt) {
                finishClock(now);
            }
        }
        PendulumRules.Charges restored = charges.recover(now);
        if (restored != charges) {
            charges = restored;
            sync();
        }
        mirrorMarkSkill(self, now);
        returned.entrySet().removeIf(entry -> {
            ServerPlayer target = self.server.getPlayerList().getPlayer(entry.getKey());
            return now >= entry.getValue().until() || target == null
                    || target.serverLevel() != entry.getValue().level() || !GameUtils.isPlayerAliveAndSurvival(target);
        });
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Mark mark = entry.getValue();
            if (now < mark.returnAt()) {
                continue;
            }
            iterator.remove();
            ServerPlayer target = self.server.getPlayerList().getPlayer(entry.getKey());
            if (target == null || target.serverLevel() != mark.level() || !GameUtils.isPlayerAliveAndSurvival(target)
                    || target.distanceToSqr(mark.spot()) > PendulumRole.RETURN_LIMIT * PendulumRole.RETURN_LIMIT) {
                self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.failed")
                        .withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            teleport(target, mark.level(), mark.spot());
            target.addEffect(ModEffects.of(ModEffects.MOVE_BANED, PendulumRole.RETURN_BIND, 0, false, true, true));
            returned.put(target.getUUID(), new Returned(mark.level(), now + PendulumRole.RETURN_WINDOW));
            mark.level().playSound(null, mark.spot().x, mark.spot().y, mark.spot().z,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.7F);
            if (self.serverLevel() == mark.level()) {
                LegendFx.pair(mark.level(), self.position(), mark.spot(), 1.4F, LegendFx.SWING);
            }
            target.displayClientMessage(Component.translatable("message.noellesroles.pendulum.pulled")
                    .withStyle(ChatFormatting.GOLD), true);
            self.displayClientMessage(Component.translatable("message.noellesroles.pendulum.returned",
                    target.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** 核心 charges 不自动恢复，故以职业数据为准；有库存时允许在恢复计时期间继续定摆。 */
    private void mirrorMarkSkill(ServerPlayer self, long now) {
        var ability = SREAbilityPlayerComponent.KEY.get(self);
        var state = ability.getSkillState(PendulumRole.MARK_SKILL);
        int remaining = charges.count() == PendulumRules.MAX_CHARGES ? 0
                : (int) Math.max(0L, charges.readyAt() - now);
        boolean changed = state.charges != charges.count() || state.maxCharges != PendulumRules.MAX_CHARGES
                || Math.abs(state.cooldown - remaining) > 1;
        state.charges = charges.count();
        state.maxCharges = PendulumRules.MAX_CHARGES;
        state.cooldown = remaining;
        if (changed) {
            ability.sync();
        }
    }

    private static void teleport(ServerPlayer target, ServerLevel level, Vec3 pos) {
        target.stopRiding();
        target.teleportTo(level, pos.x, pos.y, pos.z, target.getYRot(), target.getXRot());
        target.resetFallDistance();
        target.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void clear() {
        clockActive = false;
        history.clear();
        pending.clear();
        returned.clear();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("ClockActive", clockActive);
        tag.putLong("ClockStartedAt", clockStartedAt);
        tag.putLong("ClockEndsAt", clockEndsAt);
        tag.putLong("ClockHits", clockHits);
        tag.putLong("BonusTicks", bonusTicks);
        tag.putInt("Charges", charges.count());
        tag.putLong("RechargeAt", charges.readyAt());
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        clockActive = tag.getBoolean("ClockActive");
        clockStartedAt = tag.getLong("ClockStartedAt");
        clockEndsAt = tag.getLong("ClockEndsAt");
        clockHits = tag.getLong("ClockHits");
        bonusTicks = tag.getLong("BonusTicks");
        charges = new PendulumRules.Charges(tag.contains("Charges") ? tag.getInt("Charges") : PendulumRules.MAX_CHARGES,
                tag.getLong("RechargeAt"));
    }
}
