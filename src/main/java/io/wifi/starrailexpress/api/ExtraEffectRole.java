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

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;

/**
 * 这个 Role 会自带药水效果，每1s更新一次。
 */
public class ExtraEffectRole extends NormalRole {
    public ArrayList<MobEffectInstance> playerEffects = new ArrayList<>();

    public ExtraEffectRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime, ArrayList<MobEffectInstance> playerEffects) {
        this(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.playerEffects.addAll(playerEffects);
    }

    public ExtraEffectRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime, MobEffectInstance playerEffects) {
        this(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.playerEffects.add(playerEffects);
    }

    public ExtraEffectRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    public ArrayList<MobEffectInstance> getEffects() {
        return playerEffects;
    }

    public ExtraEffectRole removeEffect(MobEffectInstance effect) {
        playerEffects.remove(effect);
        return this;
    }

    public ExtraEffectRole addEffect(MobEffectInstance effect) {
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
}
