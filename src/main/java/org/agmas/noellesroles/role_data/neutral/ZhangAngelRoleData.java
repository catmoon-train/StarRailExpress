/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMProperties;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MoneyUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 张天使：平民中立。每 10 秒吞电使周围灯闪；黑暗中隐身并加速；
 * 积攒 10 次吞电后花费 100 金币召雷，使目标失一感官 30 秒并缓慢 III 2 分钟。
 */
public class ZhangAngelRoleData extends SimpleRoleData {

    public static final ResourceLocation SKILL_ID = Noellesroles.id("zhang_angel_lightning");

    public static final int SWALLOW_INTERVAL_TICKS = 10 * 20;
    public static final int SWALLOW_NEEDED = 10;
    public static final int LIGHTNING_COST = 100;
    public static final int FLICKER_RADIUS = 12;
    public static final int FLICKER_TICKS = 8;
    public static final int SENSE_LOSS_TICKS = 30 * 20;
    public static final int SLOWNESS_TICKS = 2 * 60 * 20;
    public static final int SLOWNESS_AMPLIFIER = 2;
    public static final int DARKNESS_BUFF_TICKS = 40;
    public static final int DARK_LIGHT_THRESHOLD = 5;

    public int swallowCount;
    public long nextSwallowTick;
    public boolean darknessStealthActive;

    private final List<LightFlicker> flickers = new ArrayList<>();

    public ZhangAngelRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void clear() {
        restoreAllFlickers();
        if (darknessStealthActive && player instanceof ServerPlayer sp) {
            clearDarknessBuffs(sp);
        }
        darknessStealthActive = false;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) {
        return p == this.player;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.ZHANG_ANGEL)) {
            return;
        }

        tickFlickers(sp.serverLevel());

        if (!GameUtils.isPlayerAliveAndSurvival(sp)) {
            restoreAllFlickers();
            if (darknessStealthActive) {
                clearDarknessBuffs(sp);
                darknessStealthActive = false;
                sync();
            }
            return;
        }

        tickSwallow(sp);
        tickDarknessStealth(sp);
    }

    public boolean useLightning(RoleSkillContext context) {
        ServerPlayer sp = context.player();
        if (sp.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isSkillAvailable || !gameWorld.isRole(sp, ModRoles.ZHANG_ANGEL)) {
            return false;
        }
        if (swallowCount < SWALLOW_NEEDED) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.zhang_angel.not_enough_swallows",
                    SWALLOW_NEEDED, swallowCount).withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!MoneyUtils.hasBalance(sp, LIGHTNING_COST)) {
            MoneyUtils.sendNotEnoughtMoneyMessage(sp, LIGHTNING_COST);
            return false;
        }
        ServerPlayer target = context.getTargetAsPlayer();
        if (target == null || target == sp || !GameUtils.isPlayerAliveAndSurvival(target)) {
            context.displayNoTargetMessage();
            return false;
        }

        if (!MoneyUtils.cost(sp, LIGHTNING_COST)) {
            MoneyUtils.sendNotEnoughtMoneyMessage(sp, LIGHTNING_COST);
            return false;
        }

        swallowCount -= SWALLOW_NEEDED;
        sync();

        strikeLightning(sp, target);
        return true;
    }

    public int getSwallowCount() {
        return swallowCount;
    }

    public boolean isDarknessStealthActive() {
        return darknessStealthActive;
    }

    private void tickSwallow(ServerPlayer sp) {
        long now = GameUtils.getTicksFromGameStart(sp.level());
        if (nextSwallowTick <= 0) {
            nextSwallowTick = now + SWALLOW_INTERVAL_TICKS;
            return;
        }
        if (now < nextSwallowTick) {
            return;
        }
        nextSwallowTick = now + SWALLOW_INTERVAL_TICKS;
        swallowElectricity(sp);
    }

    private void swallowElectricity(ServerPlayer sp) {
        boolean wasReady = swallowCount >= SWALLOW_NEEDED;
        if (!wasReady) {
            swallowCount++;
        }
        flickerNearbyLights(sp);
        spawnSwallowFx(sp);
        if (!wasReady) {
            sync();
            sp.displayClientMessage(Component.translatable("message.noellesroles.zhang_angel.swallowed",
                    swallowCount, SWALLOW_NEEDED).withStyle(ChatFormatting.AQUA), true);
            if (swallowCount >= SWALLOW_NEEDED) {
                sp.displayClientMessage(Component.translatable("message.noellesroles.zhang_angel.ready")
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    private void flickerNearbyLights(ServerPlayer sp) {
        var blackout = SREWorldBlackoutComponent.KEY.maybeGet(sp.level()).orElse(null);
        if (blackout != null && blackout.isBlackoutActive()) {
            return;
        }
        ServerLevel level = sp.serverLevel();
        BlockPos origin = sp.blockPosition();
        int radiusSqr = FLICKER_RADIUS * FLICKER_RADIUS;
        for (BlockPos pos : GameUtils.resetPoints) {
            if (pos.distSqr(origin) > radiusSqr) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(BlockStateProperties.LIT) || !state.hasProperty(TMMProperties.ACTIVE)) {
                continue;
            }
            flickers.add(new LightFlicker(pos.immutable(), state.getValue(BlockStateProperties.LIT),
                    state.getValue(TMMProperties.ACTIVE), FLICKER_TICKS));
            setLight(level, pos, false, false);
        }
        if (!flickers.isEmpty()) {
            level.playSound(null, origin, TMMSounds.BLOCK_LIGHT_TOGGLE, SoundSource.BLOCKS, 0.6f, 1.15f);
        }
    }

    private void tickFlickers(ServerLevel level) {
        if (flickers.isEmpty()) {
            return;
        }
        var blackout = SREWorldBlackoutComponent.KEY.maybeGet(level).orElse(null);
        if (blackout != null && blackout.isBlackoutActive()) {
            flickers.clear();
            return;
        }
        Iterator<LightFlicker> it = flickers.iterator();
        while (it.hasNext()) {
            LightFlicker flicker = it.next();
            flicker.ticksLeft--;
            if (flicker.ticksLeft == 6 || flicker.ticksLeft == 2) {
                setLight(level, flicker.pos, true, true);
            } else if (flicker.ticksLeft == 4) {
                setLight(level, flicker.pos, false, false);
            } else if (flicker.ticksLeft <= 0) {
                setLight(level, flicker.pos, flicker.originalLit, flicker.originalActive);
                it.remove();
            }
        }
    }

    private void restoreAllFlickers() {
        if (!(player.level() instanceof ServerLevel level) || flickers.isEmpty()) {
            flickers.clear();
            return;
        }
        for (LightFlicker flicker : flickers) {
            setLight(level, flicker.pos, flicker.originalLit, flicker.originalActive);
        }
        flickers.clear();
    }

    private static void setLight(ServerLevel level, BlockPos pos, boolean lit, boolean active) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.LIT) || !state.hasProperty(TMMProperties.ACTIVE)) {
            return;
        }
        BlockState updated = state.setValue(BlockStateProperties.LIT, lit).setValue(TMMProperties.ACTIVE, active);
        if (updated != state) {
            level.setBlockAndUpdate(pos, updated);
        }
    }

    private void tickDarknessStealth(ServerPlayer sp) {
        boolean inDarkness = isInDarkness(sp);
        if (inDarkness) {
            applyDarknessBuffs(sp);
            if (!darknessStealthActive) {
                darknessStealthActive = true;
                sync();
            }
        } else if (darknessStealthActive) {
            clearDarknessBuffs(sp);
            darknessStealthActive = false;
            sync();
        }
    }

    private static boolean isInDarkness(ServerPlayer sp) {
        int lightLevel = sp.level().getRawBrightness(sp.blockPosition(), 0);
        var blackout = SREWorldBlackoutComponent.KEY.maybeGet(sp.level()).orElse(null);
        return lightLevel <= DARK_LIGHT_THRESHOLD || (blackout != null && blackout.isBlackoutActive());
    }

    private static void applyDarknessBuffs(ServerPlayer sp) {
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, DARKNESS_BUFF_TICKS, 0, false, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, DARKNESS_BUFF_TICKS, 0, false, false, false));
    }

    private static void clearDarknessBuffs(ServerPlayer sp) {
        sp.removeEffect(MobEffects.INVISIBILITY);
        sp.removeEffect(MobEffects.MOVEMENT_SPEED);
    }

    private static void spawnSwallowFx(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, sp.getX(), sp.getY() + 1.0, sp.getZ(),
                18, 0.4, 0.6, 0.4, 0.08);
        level.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.6f);
    }

    private static void strikeLightning(ServerPlayer caster, ServerPlayer target) {
        ServerLevel level = target.serverLevel();
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.moveTo(target.getX(), target.getY(), target.getZ());
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
        }

        SenseLoss sense = SenseLoss.random(level);
        target.addEffect(new MobEffectInstance(sense.effect, SENSE_LOSS_TICKS, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_TICKS, SLOWNESS_AMPLIFIER,
                false, false, true));

        caster.displayClientMessage(Component.translatable("message.noellesroles.zhang_angel.lightning_cast",
                target.getName(), Component.translatable(sense.translationKey)).withStyle(ChatFormatting.GOLD), true);
        target.displayClientMessage(Component.translatable("message.noellesroles.zhang_angel.lightning_hit",
                Component.translatable(sense.translationKey)).withStyle(ChatFormatting.RED), true);
        level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                2.0f, 1.2f);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("swallowCount", swallowCount);
        tag.putLong("nextSwallowTick", nextSwallowTick);
        tag.putBoolean("darknessStealthActive", darknessStealthActive);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        swallowCount = tag.getInt("swallowCount");
        nextSwallowTick = tag.getLong("nextSwallowTick");
        darknessStealthActive = tag.getBoolean("darknessStealthActive");
    }

    private enum SenseLoss {
        SIGHT(ModEffects.BLIND_VISION, "message.noellesroles.zhang_angel.sense.sight"),
        HEARING(ModEffects.DEAFNESS, "message.noellesroles.zhang_angel.sense.hearing");

        final Holder<MobEffect> effect;
        final String translationKey;

        SenseLoss(Holder<MobEffect> effect, String translationKey) {
            this.effect = effect;
            this.translationKey = translationKey;
        }

        static SenseLoss random(ServerLevel level) {
            SenseLoss[] values = values();
            return values[level.random.nextInt(values.length)];
        }
    }

    private static final class LightFlicker {
        final BlockPos pos;
        final boolean originalLit;
        final boolean originalActive;
        int ticksLeft;

        LightFlicker(BlockPos pos, boolean originalLit, boolean originalActive, int ticksLeft) {
            this.pos = pos;
            this.originalLit = originalLit;
            this.originalActive = originalActive;
            this.ticksLeft = ticksLeft;
        }
    }
}
