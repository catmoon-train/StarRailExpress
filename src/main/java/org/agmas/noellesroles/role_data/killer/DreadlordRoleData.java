package org.agmas.noellesroles.role_data.killer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.agmas.noellesroles.game.command_block.DreadlordRole;
import org.agmas.noellesroles.game.command_block.LegendFx;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 恐惧魔王的个人状态：凝视进度、疑心、杀戮形态、黑暗游走和受击僵直。
 */
public class DreadlordRoleData extends SimpleRoleData {
    private final Map<UUID, Integer> gaze = new HashMap<>();
    private boolean killing;
    private int killLeft;
    private boolean dark;
    private int darkReadyAt;
    private int darkUntil;
    private int stunLeft;
    private boolean ambushed;
    private int lungeLeft;
    /** 一旦公开过形态，本实例的结束状态也必须继续同步，不能在 killing=false 后只发给本人。 */
    private boolean publicFormSeen;

    public DreadlordRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean isKilling() {
        return killing;
    }

    public boolean isDark() {
        return dark;
    }

    public boolean isStunned() {
        return stunLeft > 0;
    }

    public boolean useForm() {
        if (!(player instanceof ServerPlayer self)) {
            return false;
        }
        if (isStunned()) {
            self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.stunned")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!payForm(self)) {
            return false;
        }
        enterKilling(self);
        return true;
    }

    /** 第二个技能：非狂暴也能进出黑暗，不占用恐惧技能的冷却。 */
    public boolean useDark() {
        if (!(player instanceof ServerPlayer self)) {
            return false;
        }
        if (isStunned()) {
            self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.stunned")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (dark) {
            leaveDark(self, false);
            return true;
        }
        int now = self.tickCount;
        if (now < darkReadyAt) {
            self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.dark_wait")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        enterDark(self);
        return true;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer other) {
        return publicFormSeen || killing || dark || other == this.player;
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer self) {
            if (dark) {
                leaveDark(self, true);
            }
            if (killing) {
                setScale(self, 1.0D);
                SREPlayerPsychoComponent.KEY.get(self).stopPsychoAndSync();
            }
            self.removeEffect(ModEffects.INVINCIBLE);
            self.removeEffect(ModEffects.DREADLORD_DREAD);
        }
        killing = false;
        dark = false;
        darkUntil = 0;
        stunLeft = 0;
        gaze.clear();
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer self) || !(self.level() instanceof ServerLevel level)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(self)) {
            if (killing) {
                leaveKilling(self, level);
            } else if (dark) {
                leaveDark(self, true);
                self.removeEffect(ModEffects.INVINCIBLE);
                self.removeEffect(MobEffects.MOVEMENT_SPEED);
            }
            return;
        }
        tickStun(self);
        // 黑暗游走不能暂停狂暴寿命；无论在哪种形态都在此统一结束并广播复原状态。
        if (killing) {
            killLeft--;
            if (SREPlayerPsychoComponent.KEY.get(self).getPsychoTicks() <= 0 || killLeft <= 0) {
                leaveKilling(self, level);
                return;
            }
        }
        if (dark) {
            tickDark(self, level);
            return;
        }
        if (killing) {
            tickKilling(self, level);
            return;
        }
        tickGaze(self, level);
    }

    public void onHit() {
        if (!(player instanceof ServerPlayer self) || dark || isStunned()) {
            return;
        }
        int stun = killing ? DreadlordRole.KILLING_HIT_STUN : DreadlordRole.HIT_STUN;
        stunLeft = stun;
        self.addEffect(new MobEffectInstance(ModEffects.USED_BANED, stun, 0, false, false, true));
        DreadlordRole.stun(self, stun);
        self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.stunned")
                .withStyle(ChatFormatting.DARK_GRAY), true);
        if (self.level() instanceof ServerLevel level) {
            LegendFx.ring(level, self.position(), 1.2F, LegendFx.DREAD);
            level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    private void tickStun(ServerPlayer self) {
        if (stunLeft <= 0) {
            return;
        }
        stunLeft--;
        self.setDeltaMovement(0.0D, self.getDeltaMovement().y, 0.0D);
        self.hurtMarked = true;
    }

    private void tickGaze(ServerPlayer self, ServerLevel level) {
        boolean changed = false;
        for (ServerPlayer other : level.players()) {
            if (other == self || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            if (!DreadlordRole.lookingAt(self, other)) {
                if (gaze.remove(other.getUUID()) != null) {
                    changed = true;
                }
                continue;
            }
            int next = gaze.getOrDefault(other.getUUID(), 0) + 1;
            gaze.put(other.getUUID(), next);
            changed = true;
            if (next == DreadlordRole.GAZE_TICKS) {
                applyDread(self, other, level);
            }
        }
        if (changed && self.tickCount % 10 == 0) {
            sync();
        }
    }

    private void applyDread(ServerPlayer self, ServerPlayer target, ServerLevel level) {
        int newlyFrightened = 0;
        for (ServerPlayer other : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            boolean alreadyFrightened = other.hasEffect(ModEffects.DREADLORD_DREAD);
            boolean applied = other.addEffect(new MobEffectInstance(ModEffects.DREADLORD_DREAD,
                    DreadlordRole.DREAD_TICKS, 0, false, false, true));
            if (other != self && !alreadyFrightened && applied) {
                newlyFrightened++;
            }
            SREPlayerMoodComponent.KEY.get(other).addMood(-0.02F);
        }
        // 只奖励真正新进入恐惧的其他玩家，续时不重复发钱；一轮只同步一次余额。
        if (newlyFrightened > 0) {
            SREPlayerShopComponent.KEY.get(self).addToBalance(newlyFrightened * 10);
        }
        target.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.watched")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.dread",
                target.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE), true);
        LegendFx.ring(level, target.position(), 2.4F, LegendFx.DREAD);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    private boolean payForm(ServerPlayer self) {
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(self);
        if (shop.balance < DreadlordRole.FORM_COST) {
            self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.no_money",
                    DreadlordRole.FORM_COST).withStyle(ChatFormatting.RED), true);
            return false;
        }
        shop.balance -= DreadlordRole.FORM_COST;
        shop.sync();
        return true;
    }

    private void enterKilling(ServerPlayer self) {
        boolean started = SREPlayerPsychoComponent.KEY.get(self).startPsycho_time(
                DreadlordRole.KILL_FORM_TICKS, 0, true);
        if (!started) {
            return;
        }
        killing = true;
        publicFormSeen = true;
        killLeft = DreadlordRole.KILL_FORM_TICKS;
        setScale(self, DreadlordRole.KILL_SCALE);
        lunge(self, 1.15D);
        self.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, DreadlordRole.KILL_FORM_TICKS, 0,
                false, false, true));
        self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DreadlordRole.KILL_FORM_TICKS, 0,
                false, false, true));
        if (self.level() instanceof ServerLevel level) {
            LegendFx.ring(level, self.position(), 3.0F, LegendFx.DREAD);
            ParticleFx.sphere(level, ParticleTypes.SQUID_INK, self.position().add(0.0D, 1.0D, 0.0D), 24, 2.4D, 0.02D);
        }
        self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.killing")
                .withStyle(ChatFormatting.DARK_RED), true);
        sync();
    }

    /** 沿视线平地冲出一小段。 */
    public void lunge(ServerPlayer self, double speed) {
        Vec3 look = self.getLookAngle();
        double flat = Math.sqrt(look.x * look.x + look.z * look.z);
        if (flat < 1.0E-3) {
            look = Vec3.directionFromRotation(0.0F, self.getYRot());
            flat = Math.sqrt(look.x * look.x + look.z * look.z);
        }
        self.setDeltaMovement(look.x / flat * speed, Math.max(self.getDeltaMovement().y, 0.08D), look.z / flat * speed);
        self.hurtMarked = true;
        self.hasImpulse = true;
        lungeLeft = 6;
    }

    private void tickKilling(ServerPlayer self, ServerLevel level) {
        if (lungeLeft > 0) {
            lungeLeft--;
            if (self.tickCount % 2 == 0) {
                ParticleFx.sphere(level, ParticleTypes.SQUID_INK, self.position().add(0.0D, 0.6D, 0.0D), 6, 0.8D, 0.01D);
            }
        }
        if (self.tickCount % 10 == 0) {
            ParticleFx.sphere(level, ParticleTypes.SQUID_INK, self.position().add(0.0D, 1.2D, 0.0D), 8, 2.2D, 0.01D);
        }
    }

    private void leaveKilling(ServerPlayer self, ServerLevel level) {
        if (dark) {
            leaveDark(self, true);
        }
        boolean wasKilling = killing;
        killing = false;
        killLeft = 0;
        setScale(self, 1.0D);
        self.removeEffect(ModEffects.INVINCIBLE);
        self.removeEffect(MobEffects.MOVEMENT_SPEED);
        SREPlayerPsychoComponent.KEY.get(self).stopPsychoAndSync();
        if (wasKilling) {
            LegendFx.ring(level, self.position(), 1.6F, LegendFx.DREAD);
            self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.form_end")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        sync();
    }

    private void enterDark(ServerPlayer self) {
        dark = true;
        publicFormSeen = true;
        ambushed = false;
        darkReadyAt = self.tickCount + DreadlordRole.DARK_COOLDOWN;
        darkUntil = self.tickCount + DreadlordRole.DARK_DURATION;
        self.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DreadlordRole.DARK_DURATION, 0,
                false, false, false));
        self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DreadlordRole.DARK_DURATION, 1,
                false, false, false));
        self.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, 40, 0, false, false, false));
        self.level().playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.WARDEN_DIG,
                SoundSource.PLAYERS, 0.7F, 0.7F);
        self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.dark")
                .withStyle(ChatFormatting.DARK_GRAY), true);
        sync();
    }

    private void tickDark(ServerPlayer self, ServerLevel level) {
        if (self.tickCount >= darkUntil) {
            leaveDark(self, false);
            return;
        }
        self.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, 40, 0, false, false, false));
        self.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
        if (self.tickCount % 4 == 0) {
            LegendFx.ring(level, self.position(), 0.7F, LegendFx.DREAD);
            ParticleFx.sphere(level, ParticleTypes.SQUID_INK, self.position().add(0.0D, 0.2D, 0.0D), 4, 1.1D, 0.0D);
            level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.SOUL_ESCAPE,
                    SoundSource.PLAYERS, 0.25F, 0.5F);
        }
        if (!ambushed) {
            ServerPlayer target = DreadlordRole.nearestLiving(self, DreadlordRole.AMBUSH_RANGE);
            if (target != null) {
                ambushed = true;
                DreadlordRole.stun(target, DreadlordRole.AMBUSH_LOCK);
                LegendFx.ring(level, target.position(), 1.4F, LegendFx.DREAD);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
                        SoundSource.PLAYERS, 0.4F, 1.4F);
                target.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.ambush")
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
        }
    }

    private void leaveDark(ServerPlayer self, boolean silent) {
        dark = false;
        darkUntil = 0;
        ambushed = false;
        self.removeEffect(MobEffects.INVISIBILITY);
        self.removeEffect(MobEffects.MOVEMENT_SPEED);
        self.removeEffect(ModEffects.INVINCIBLE);
        // 退出二技能只撤销黑暗效果；尚未结束的狂暴继续使用自身剩余时长。
        if (killing && killLeft > 0) {
            self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, killLeft, 0, false, false, true));
            self.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, killLeft, 0, false, false, true));
        }
        self.level().playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.WARDEN_EMERGE,
                SoundSource.PLAYERS, 0.6F, 1.1F);
        if (!silent) {
            self.displayClientMessage(Component.translatable("message.noellesroles.dreadlord.dark_end")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        sync();
    }

    private static void setScale(ServerPlayer self, double scale) {
        var attr = self.getAttribute(Attributes.SCALE);
        if (attr != null) {
            attr.setBaseValue(scale);
        }
        self.refreshDimensions();
    }

    public boolean canSwing() {
        return killing && !dark && !isStunned() && player instanceof ServerPlayer self
                && !self.getCooldowns().isOnCooldown(ModItems.REAPER_SCYTHE);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("Killing", killing);
        tag.putBoolean("Dark", dark);
        tag.putInt("KillLeft", killLeft);
        tag.putInt("StunLeft", stunLeft);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        boolean wasKilling = killing;
        killing = tag.getBoolean("Killing");
        dark = tag.getBoolean("Dark");
        killLeft = tag.getInt("KillLeft");
        stunLeft = tag.getInt("StunLeft");
        if (wasKilling != killing && player != null) {
            player.refreshDimensions();
        }
    }
}
