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

package io.wifi.starrailexpress.api;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.compat.CrosshairaddonsCompat;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * LooseEndRole
 */
public class LooseEndRole extends OriginalRole {
    public ArrayList<MobEffectInstance> playerEffects = new ArrayList<>();

    public LooseEndRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime, List<MobEffectInstance> playerEffects) {
        this(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.playerEffects.addAll(playerEffects);
    }

    public LooseEndRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime, MobEffectInstance playerEffects) {
        this(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.playerEffects.add(playerEffects);
    }

    public LooseEndRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    public ArrayList<MobEffectInstance> getEffects() {
        return playerEffects;
    }

    public LooseEndRole removeEffect(MobEffectInstance effect) {
        playerEffects.remove(effect);
        return this;
    }

    public LooseEndRole addEffect(MobEffectInstance effect) {
        playerEffects.add(effect);
        return this;
    }

    public MobEffectInstance getNewEffectInstance(MobEffectInstance instance) {
        return new MobEffectInstance(instance);
    }

    @Override
    public void serverTick(ServerPlayer player) {
        if (player.level().getGameTime() % 20 == 0) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                for (var eff : playerEffects) {
                    if (!player.hasEffect(eff.getEffect()) || player.getEffect(eff.getEffect()).getDuration() <= 21) {
                        player.addEffect(getNewEffectInstance(eff));
                    }
                }
            }
        }
    }

    @Override
    public void onKill(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation deathReason) {
        super.onKill(victim, spawnBody, killer, deathReason);
        if (killer == null)
            return;
        if (!killer.hasEffect(MobEffects.WEAVING))
            return;
        CrosshairaddonsCompat.onAttack(victim);
        if (killer != null && killer.level() instanceof ServerLevel serverLevel) {
            specialEffect(victim, killer, serverLevel);
        }
    }

    private static void specialEffect(Player victim, @NonNls Player killer, ServerLevel serverLevel) {
        Vec3 victimPos = victim.position();
        Vec3 killerPos = killer.position();

        // 1. 自身小特效 - 在击杀者周围生成红色粒子环绕
        for (int i = 0; i < 20; i++) {
            double angle = (Math.PI * 2 * i) / 20;
            double offsetX = Math.cos(angle) * 1.5;
            double offsetZ = Math.sin(angle) * 1.5;
            serverLevel.sendParticles(
                    ParticleTypes.CRIMSON_SPORE,
                    killerPos.x + offsetX, killerPos.y + 1.5, killerPos.z + offsetZ,
                    1, 0.1, 0.1, 0.1, 0.0);
        }

        // 2. 击中人的夸张特效 - 在受害者位置生成爆炸和粒子爆发
        // CRIT粒子（暴击效果）
        serverLevel.sendParticles(
                ParticleTypes.CRIT,
                victimPos.x, victimPos.y + 1, victimPos.z,
                15, 0.5, 0.5, 0.5, 0.3);

        // SOUL_FIRE_FLAME（灵魂火焰）- 增加恐怖感
        serverLevel.sendParticles(
                ParticleTypes.SOUL_FIRE_FLAME,
                victimPos.x, victimPos.y + 0.5, victimPos.z,
                15, 0.4, 0.6, 0.4, 0.05);

        // LARGE_SMOKE（大烟雾）
        serverLevel.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                victimPos.x, victimPos.y + 0.8, victimPos.z,
                10, 0.3, 0.4, 0.3, 0.02);

        // 3. 播放音效 - 多层音效叠加
        // 主音效：刀刺声
        serverLevel.playSound(null, victimPos.x, victimPos.y, victimPos.z,
                TMMSounds.ITEM_KNIFE_STAB, SoundSource.PLAYERS, 1.5f, 0.8f);

        // 回声效果：锁链击中声
        serverLevel.playSound(null, victimPos.x, victimPos.y, victimPos.z,
                SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0f, 1.2f);

        // 环境层：恶魂尖叫（增加恐怖氛围）
        serverLevel.playSound(null, victimPos.x, victimPos.y, victimPos.z,
                SoundEvents.GHAST_SCREAM, SoundSource.PLAYERS, 0.6f, 0.7f);
    }
}
