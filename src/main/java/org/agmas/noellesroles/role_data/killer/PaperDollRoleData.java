package org.agmas.noellesroles.role_data.killer;

import org.agmas.noellesroles.game.command_block.CommandBlockDirector;
import org.agmas.noellesroles.game.command_block.LegendFx;
import org.agmas.noellesroles.game.command_block.PaperDollRole;
import org.agmas.noellesroles.game.command_block.StandSpots;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** 纸人：受击后暂缓结算半秒，在窗口内使用技能才能消除此击。 */
public class PaperDollRoleData extends SimpleRoleData {
    public static final int REACTION_TICKS = 10;
    public static final int WEAK_TICKS = 3 * 20;
    public static final int GLOW_TICKS = 2 * 20;
    public static final double BLINK = 4.0D;

    private long reactionUntil;
    /** 仅服务端保存原结算，不把杀手或伤害来源广播到客户端。 */
    private Runnable pendingDeath;
    private Player pendingAttacker;
    private boolean resolving;
    private long dodgedAt = Long.MIN_VALUE;

    public PaperDollRoleData(RoleDataContext context) {
        super(context);
    }

    /** 普通伤害保留原 DamageSource；超时后由同一来源结算死亡。 */
    public boolean deferDamage(ServerPlayer self, DamageSource source, float amount) {
        if (!(amount > 0.0F) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        Player attacker = source.getEntity() instanceof Player hitting ? hitting : null;
        return defer(self, attacker, () -> {
            self.invulnerableTime = 0;
            self.hurt(source, Float.MAX_VALUE);
        });
    }

    /** 保存原死亡参数；由已解析真实杀手的早期死亡事件调用。 */
    public boolean deferDeath(ServerPlayer self, boolean spawnBody, Player killer, ResourceLocation reason) {
        return defer(self, killer, () -> GameUtils.killPlayer(self, spawnBody, killer, reason, false));
    }

    private boolean defer(ServerPlayer self, Player attacker, Runnable death) {
        if (resolving || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        long now = self.serverLevel().getGameTime();
        if (now == dodgedAt) {
            return true;
        }
        if (pendingDeath != null) {
            // 同一窗口的后续命中既不刷新时限，也不覆盖第一击的杀手和死因。
            return true;
        }
        if (!SREAbilityPlayerComponent.KEY.get(self).canUseSkill(PaperDollRole.SKILL_ID)) {
            return false;
        }
        pendingDeath = death;
        pendingAttacker = attacker;
        reactionUntil = now + REACTION_TICKS;
        sync();
        self.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0F, 1.7F);
        return true;
    }

    public int reactionTicksLeft() {
        return (int) Math.max(0L, Math.min(REACTION_TICKS, reactionUntil - player.level().getGameTime()));
    }

    /** 只有窗口内实际消除此击才返回成功，由 RoleSkill 统一消耗技能冷却。 */
    public boolean dodge(ServerPlayer self) {
        if (pendingDeath == null || reactionTicksLeft() <= 0 || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return false;
        }
        Player attacker = pendingAttacker;
        clearPending();
        dodgedAt = self.serverLevel().getGameTime();
        sync();
        Vec3 from = self.position();
        ServerLevel level = self.serverLevel();
        Vec3 spot = StandSpots.find(level, from, StandSpots.horizontal(self, -1.0D), BLINK);
        if (spot != null) {
            StandSpots.place(self, spot);
        }
        self.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 0, false, false, true));
        self.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false, true));
        level.sendParticles(ParticleTypes.CLOUD, from.x, from.y + 1.0D, from.z, 16, 0.25D, 0.45D, 0.25D, 0.02D);
        LegendFx.ring(level, from.add(0.0D, 0.2D, 0.0D), 1.2F, LegendFx.PAPER);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.45F, 1.45F);
        self.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.dodged")
                .withStyle(ChatFormatting.WHITE), true);
        if (attacker != null && attacker != self) {
            attacker.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.hit_paper")
                    .withStyle(ChatFormatting.WHITE), true);
        }
        return true;
    }

    @Override
    public void serverTick() {
        if (pendingDeath == null || !(player instanceof ServerPlayer self)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(self) || !CommandBlockDirector.isActive(self.serverLevel())) {
            clearPending();
            sync();
            return;
        }
        if (reactionTicksLeft() > 0) {
            return;
        }
        Runnable death = pendingDeath;
        clearPending();
        sync();
        // 只跳过纸人自己的拦截，不改为强制死亡，不绕过其他正常死亡规则。
        resolving = true;
        try {
            death.run();
        } finally {
            resolving = false;
        }
    }

    private void clearPending() {
        pendingDeath = null;
        pendingAttacker = null;
        reactionUntil = 0L;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer receiver) {
        return receiver == player;
    }

    @Override
    public void clear() {
        clearPending();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("reactionUntil", reactionUntil);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        reactionUntil = tag.getLong("reactionUntil");
    }
}
