package org.agmas.noellesroles.role_data.killer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.CommandBlockCombat;
import org.agmas.noellesroles.game.command_block.NullFx;
import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREMonitorWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Null 的局内状态。
 *
 * <p>
 * 三件事：
 * <ol>
 * <li>被动：断电时加速，被列车照明照到时变慢。</li>
 * <li>抹除：前摇期间持续锁定，时间到再判定目标是否还在视线里；断电中前摇减半。</li>
 * <li>涂黑：熄灭周围一圈灯，并让监控一起失效。</li>
 * </ol>
 */
public class EntityNullRoleData extends SimpleRoleData {
    /** 抹除次数的上限（同时持有）；不是一局总数，会按时间恢复。 */
    public static final int MAX_ERASES = 2;
    /** 抹除前摇：正常 1.25 秒。 */
    public static final int WINDUP_TICKS = 25;
    /** 抹除前摇：断电中只要 1 秒（黑暗里动手更快）。 */
    public static final int WINDUP_TICKS_DARK = 20;
    /** 起手能锁定的最远距离。 */
    public static final double ERASE_RANGE = 12.0D;
    /** 前摇结束时目标仍需在视线与这个距离内。 */
    public static final double ERASE_KEEP = 14.0D;
    /** 涂黑：熄灯半径。 */
    public static final int REDACT_RADIUS = 20;
    /** 涂黑：熄灯时长。 */
    public static final int REDACT_TICKS = 6 * 20;
    /** 涂黑：监控失效时长。 */
    public static final int REDACT_MONITOR_TICKS = 6 * 20;
    /** 抹除次数恢复间隔：1 分钟恢复 1 次。 */
    public static final int REGEN_TICKS = 60 * 20;
    public static final ResourceLocation DEATH = Noellesroles.id("entity_null_erase");

    private int erasesLeft = MAX_ERASES;
    private UUID windupTarget;
    private long windupAt;
    private long nextRegenAt;
    public static final int REACTION_TICKS = 10;
    public static final double SELF_ERASE_RADIUS = 4.0D;
    private long reactionUntil;
    private Runnable pendingHit;
    private boolean resolvingHit;
    private boolean selfEraseUsed;
    private long eraserBuyReadyAt;

    /** 只暂缓第一击；连续命中不延长反击窗口。 */
    public boolean deferHit(ServerPlayer self, Runnable hit) {
        if (resolvingHit || selfEraseUsed || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        if (pendingHit != null) {
            return true;
        }
        if (!io.wifi.starrailexpress.cca.SREAbilityPlayerComponent.KEY.get(self)
                .canUseSkill(org.agmas.noellesroles.game.command_block.EntityNullRole.SELF_ERASE_SKILL)) {
            return false;
        }
        pendingHit = hit;
        reactionUntil = self.level().getGameTime() + REACTION_TICKS;
        sync();
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.react"), true);
        self.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
        return true;
    }

    /** 普通玩家攻击保留原伤害，环境伤害及强制伤害不开放窗口。 */
    public boolean deferDamage(ServerPlayer self, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!(amount > 0.0F) || !(source.getEntity() instanceof ServerPlayer attacker) || attacker == self
                || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return deferHit(self, () -> {
            self.invulnerableTime = 0;
            self.hurt(source, amount);
        });
    }

    /** 每局一次，只有受击后的十 tick 内可以释放；不产生方块爆炸。 */
    public boolean selfErase(ServerPlayer self) {
        if (selfEraseUsed || pendingHit == null || self.level().getGameTime() >= reactionUntil
                || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        selfEraseUsed = true;
        clearPendingHit();
        windupTarget = null;
        windupAt = 0;
        sync();
        ServerLevel level = self.serverLevel();
        var origin = self.position();
        var area = self.getBoundingBox().inflate(SELF_ERASE_RADIUS);
        double radiusSqr = SELF_ERASE_RADIUS * SELF_ERASE_RADIUS;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, origin.x, origin.y + 1, origin.z,
                1, 0, 0, 0, 0);
        NullFx.erase(level, origin);
        level.playSound(null, self.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
        for (ServerPlayer target : new ArrayList<>(level.players())) {
            if (target != self && GameUtils.isPlayerAliveAndSurvival(target)
                    && target.distanceToSqr(origin) <= radiusSqr) {
                GameUtils.killPlayer(target, false, self, DEATH, false);
            }
        }
        // 自身必死，先结算玩家，再清理包括死亡时新掉落的物品。
        GameUtils.killPlayer(self, false, self, DEATH, true);
        for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area,
                item -> item.isAlive() && item.distanceToSqr(origin) <= radiusSqr)) {
            item.discard();
        }
        return true;
    }

    private void clearPendingHit() {
        pendingHit = null;
        reactionUntil = 0;
    }

    private void tickPendingHit(ServerPlayer self, long now) {
        if (pendingHit == null || now < reactionUntil) {
            return;
        }
        Runnable hit = pendingHit;
        clearPendingHit();
        sync();
        resolvingHit = true;
        try {
            hit.run();
        } finally {
            resolvingHit = false;
        }
    }

    public boolean canBuyEraser() {
        return player.level().getGameTime() >= eraserBuyReadyAt;
    }

    public void onBuyEraser() {
        eraserBuyReadyAt = player.level().getGameTime() + 120 * 20;
        sync();
    }

    @Override
    public void clear() {
        clearPendingHit();
        windupTarget = null;
        windupAt = 0;
    }

    public EntityNullRoleData(RoleDataContext context) {
        super(context);
    }

    public int erasesLeft() {
        return erasesLeft;
    }

    public boolean isErasing() {
        return windupTarget != null;
    }

    public boolean beginErase(ServerPlayer self, ServerPlayer target, long now) {
        if (erasesLeft <= 0 || windupTarget != null) {
            return false;
        }
        int windup = windupTicks(self.serverLevel());
        windupTarget = target.getUUID();
        windupAt = now + windup;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, windup, 3, false, false, true));
        self.addEffect(new MobEffectInstance(MobEffects.GLOWING, windup + 10, 0, false, false, true));
        self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, windup + 10, 1, false, false, true));
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.charging", target.getName())
                .withStyle(ChatFormatting.DARK_GRAY), true);
        NullFx.mark(self.serverLevel(), target.position());
        sync();
        return true;
    }

    /** 断电中前摇缩短到一秒：黑暗里没人看得见他在做什么。 */
    private static int windupTicks(ServerLevel level) {
        return SREWorldBlackoutComponent.KEY.get(level).isBlackoutActive() ? WINDUP_TICKS_DARK : WINDUP_TICKS;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer self)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(self)) {
            // 死了就把没结算的前摇丢掉，避免复活后技能一直被占着
            windupTarget = null;
            windupAt = 0;
            clearPendingHit();
            return;
        }
        ServerLevel level = self.serverLevel();
        long now = level.getGameTime();
        tickPendingHit(self, now);
        if (!GameUtils.isPlayerAliveAndSurvival(self)) {
            return;
        }
        boolean dark = SREWorldBlackoutComponent.KEY.get(level).isBlackoutActive();
        if (now % 20 == 0) {
            tickPassive(self, dark);
        }
        tickRegen(self, level, now);
        if (windupTarget == null) {
            return;
        }
        if (now < windupAt) {
            tickWindupFx(level, now, windupTarget);
            return;
        }
        UUID targetId = windupTarget;
        windupTarget = null;
        windupAt = 0;
        finishErase(self, level, targetId);
    }

    /** 断电是 NULL 的主场；列车照明反而会勾出他的轮廓。 */
    private static void tickPassive(ServerPlayer self, boolean dark) {
        if (dark) {
            self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, false, false));
            return;
        }
        if (self.level().getRawBrightness(self.blockPosition(), 0) >= 12) {
            self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
        }
    }

    /** 抹除次数每 60 秒恢复 1 次，最多回到上限。 */
    private void tickRegen(ServerPlayer self, ServerLevel level, long now) {
        if (erasesLeft >= MAX_ERASES) {
            if (nextRegenAt != 0) {
                nextRegenAt = 0;
                sync();
            }
            return;
        }
        if (nextRegenAt == 0) {
            nextRegenAt = now + REGEN_TICKS;
            return;
        }
        if (now < nextRegenAt) {
            return;
        }
        erasesLeft++;
        nextRegenAt = erasesLeft >= MAX_ERASES ? 0 : now + REGEN_TICKS;
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.regen_done")
                .withStyle(ChatFormatting.DARK_GRAY), true);
        sync();
    }

    /** 距离下一次恢复还有几秒；已满或不计时返回 0。 */
    public int secondsUntilRegen(long now) {
        if (erasesLeft >= MAX_ERASES || nextRegenAt <= now) {
            return 0;
        }
        return (int) ((nextRegenAt - now + 19) / 20);
    }

    /** 前摇期间在目标身上冒一点灵魂火，给双方一个「正在被注销」的提示。 */
    private static void tickWindupFx(ServerLevel level, long now, UUID targetId) {
        if (now % 5 != 0 || targetId == null) {
            return;
        }
        ServerPlayer target = level.getServer() == null ? null
                : level.getServer().getPlayerList().getPlayer(targetId);
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return;
        }
        ParticleFx.region(level, ParticleTypes.SOUL_FIRE_FLAME,
                GazeRays.boxAround(target.position().add(0.0D, 1.0D, 0.0D), 0.7D), 8, 0.02D);
    }

    private void finishErase(ServerPlayer self, ServerLevel level, UUID targetId) {
        ServerPlayer target = level.getServer() == null ? null
                : level.getServer().getPlayerList().getPlayer(targetId);
        boolean kept = target != null && GameUtils.isPlayerAliveAndSurvival(target)
                && self.distanceToSqr(target) <= ERASE_KEEP * ERASE_KEEP
                && self.hasLineOfSight(target);
        if (!kept) {
            self.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 4 * 20, 0, false, false, true));
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.slipped")
                    .withStyle(ChatFormatting.GRAY), true);
            sync();
            return;
        }
        boolean killed = CommandBlockCombat.tryKill(self, target, DEATH, false,
                Component.translatable("message.noellesroles.entity_null.blocked"));
        if (!killed) {
            sync();
            return;
        }
        erasesLeft--;
        if (erasesLeft < MAX_ERASES && nextRegenAt == 0) {
            nextRegenAt = level.getGameTime() + REGEN_TICKS;
        }
        NullFx.erase(level, target.position());
        level.playSound(null, target.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.9F, 0.4F);
        level.playSound(null, target.blockPosition(), NRSounds.SHORT_CIRCUIT, SoundSource.PLAYERS, 0.8F, 0.6F);
        self.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 8 * 20, 1, false, false, true));
        pay(self);
        broadcast(level, Component.translatable("message.noellesroles.entity_null.removed", target.getName())
                .withStyle(ChatFormatting.DARK_GRAY));
        int seconds = secondsUntilRegen(level.getGameTime());
        if (seconds > 0) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.left_regen",
                    erasesLeft, seconds).withStyle(ChatFormatting.DARK_GRAY), true);
        } else {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.left", erasesLeft)
                    .withStyle(ChatFormatting.DARK_GRAY), true);
        }
        sync();
    }

    /** 涂黑：把这一段的记录连同灯一起抹掉。 */
    public boolean redact(ServerPlayer self) {
        ServerLevel level = self.serverLevel();
        SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(level);
        if (blackout.isBlackoutActive()) {
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.redact_busy")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        blackout.triggerBlackout(self.blockPosition(), REDACT_RADIUS, true, REDACT_TICKS);
        SREMonitorWorldComponent monitor = SREMonitorWorldComponent.KEY.get(level);
        if (!monitor.isBroken()) {
            monitor.triggerBroken(false, REDACT_MONITOR_TICKS);
        }
        level.playSound(null, self.blockPosition(), NRSounds.SHORT_CIRCUIT, SoundSource.PLAYERS, 1.2F, 1.1F);
        NullFx.redact(level, self.position(), 8.0F);
        broadcast(level, Component.translatable("message.noellesroles.entity_null.redacted")
                .withStyle(ChatFormatting.DARK_GRAY));
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.redact_done")
                .withStyle(ChatFormatting.DARK_GRAY), true);
        sync();
        return true;
    }

    private static void pay(ServerPlayer self) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < self.getInventory().getContainerSize(); i++) {
            ItemStack stack = self.getInventory().getItem(i);
            if (!stack.isEmpty() && !stack.is(ModItems.VOID_STEP)) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) {
            self.hurt(self.damageSources().magic(), 6.0F);
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.paid_self")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        int slot = slots.get(self.getRandom().nextInt(slots.size()));
        self.getInventory().getItem(slot).shrink(1);
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.paid_item")
                .withStyle(ChatFormatting.DARK_GRAY), true);
    }

    private static void broadcast(ServerLevel level, Component message) {
        if (level.getServer() == null) {
            return;
        }
        level.getServer().getPlayerList().broadcastSystemMessage(message, false);
    }

    public static ServerPlayer lookTarget(ServerPlayer self) {
        List<ServerPlayer> hit = GazeRays.playersAlong(self, ERASE_RANGE, 0.4D);
        return hit.isEmpty() ? null : hit.get(0);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("left", erasesLeft);
        tag.putLong("regenAt", nextRegenAt);
        tag.putLong("windupAt", windupAt);
        tag.putLong("reactionUntil", reactionUntil);
        tag.putBoolean("selfEraseUsed", selfEraseUsed);
        tag.putLong("eraserBuyReadyAt", eraserBuyReadyAt);
        if (windupTarget != null) {
            tag.putUUID("windup", windupTarget);
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        erasesLeft = tag.contains("left") ? tag.getInt("left") : MAX_ERASES;
        windupAt = tag.getLong("windupAt");
        windupTarget = tag.contains("windup") ? tag.getUUID("windup") : null;
        nextRegenAt = tag.getLong("regenAt");
        reactionUntil = tag.getLong("reactionUntil");
        selfEraseUsed = tag.getBoolean("selfEraseUsed");
        eraserBuyReadyAt = tag.getLong("eraserBuyReadyAt");
    }
}
