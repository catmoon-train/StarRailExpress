package io.wifi.starrailexpress.api;

import java.util.ArrayList;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 这个 Role 会自带药水效果，每1s更新一次。
 */
public class ExtraEffectRole extends NormalRole {
    public ExtraEffectRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime, ArrayList<MobEffectInstance> playerEffects) {
        this(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.playerEffects = playerEffects;
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

    public ArrayList<MobEffectInstance> playerEffects = new ArrayList<>();

    @Override
    public void serverTick(ServerPlayer player) {
        if (player.level().getGameTime() % 20 == 0) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                for (var eff : playerEffects) {
                    if (!player.hasEffect(eff.getEffect()) || player.getEffect(eff.getEffect()).getDuration() <= 21) {
                        player.addEffect(eff);
                    }
                }
            }
        }
    }
}
