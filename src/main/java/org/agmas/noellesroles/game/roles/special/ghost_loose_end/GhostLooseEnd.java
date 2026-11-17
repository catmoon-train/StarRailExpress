package org.agmas.noellesroles.game.roles.special.ghost_loose_end;

import io.wifi.starrailexpress.api.NormalRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 幽灵亡命徒：以限时隐身作为生存时间，击杀可按边际递减延长隐身。
 */
public final class GhostLooseEnd extends NormalRole {
    /** 初始隐身时长：2 分钟 */
    public static final int INITIAL_INVISIBILITY_TICKS = 2 * 60 * 20;
    /**
     * 隐身时长的渐近上限：8 分钟
     * <p>
     * 该数值对应超级亡命徒最高速度等级在邪恶战争中完整衰减后的总持续时间
     */
    public static final int MAX_INVISIBILITY_TICKS = 8 * 60 * 20;
    /** 每次击杀补充当前时长与上限差值的四分之一 */
    private static final int KILL_EXTENSION_DIVISOR = 4;

    public GhostLooseEnd(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public void onInit(MinecraftServer server, ServerPlayer player) {
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                Integer.MAX_VALUE,
                2,
                false,
                false));

        player.removeEffect(MobEffects.INVISIBILITY);
        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                INITIAL_INVISIBILITY_TICKS,
                0,
                false,
                false));
    }

    @Override
    public void onKill(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {
        if (killer == null) {
            return;
        }
        MobEffectInstance invisibility = killer.getEffect(MobEffects.INVISIBILITY);
        if (invisibility == null) {
            return;
        }

        int currentDuration = Math.min(invisibility.getDuration(), MAX_INVISIBILITY_TICKS);
        int remaining = MAX_INVISIBILITY_TICKS - currentDuration;
        if (remaining <= 0) {
            return;
        }

        int extension = Math.max(1, remaining / KILL_EXTENSION_DIVISOR);
        int newDuration = Math.min(MAX_INVISIBILITY_TICKS, currentDuration + extension);
        killer.removeEffect(MobEffects.INVISIBILITY);
        killer.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                newDuration,
                invisibility.getAmplifier(),
                false,
                false));
    }
}
