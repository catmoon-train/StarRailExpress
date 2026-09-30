package org.agmas.noellesroles.content.effects;

import io.wifi.starrailexpress.game.data.MapStatusBarType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.scene.MapStatusBarRuntime;

/**
 * 地图状态条类药水效果：每秒按等级改变饥饿 / 口渴 / 保暖 / 污染四条状态中的一条。
 *
 * <p>每级 1 点：1 级每秒变化 1 点，2 级每秒 2 点，以此类推；
 * {@code deltaPerLevel} 为正表示增加、为负表示减少。
 *
 * <p>数值的实际增减由 {@link MapStatusBarRuntime} 处理：地图没有配置该状态条、
 * 游戏未运行或该玩家不被追踪时，调用会被静默忽略。
 */
public class MapStatusBarEffect extends SimpleMobEffect {
    /** 结算周期：每秒一次。 */
    private static final int TICK_INTERVAL = 20;

    private final MapStatusBarType statusType;
    private final int deltaPerLevel;

    public MapStatusBarEffect(MobEffectCategory category, int color, MapStatusBarType statusType, int deltaPerLevel) {
        super(category, color);
        this.statusType = statusType;
        this.deltaPerLevel = deltaPerLevel;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // 剩余 tick 对 20 取模为 0 时结算一次，即「每秒」一跳
        return duration % TICK_INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayer player)) {
            return false;
        }
        int amount = (1 + Math.max(0, amplifier)) * deltaPerLevel;
        switch (statusType) {
            case HUNGER -> MapStatusBarRuntime.addHunger(player, amount);
            case THIRST -> MapStatusBarRuntime.addThirst(player, amount);
            case WARMTH -> MapStatusBarRuntime.addWarmth(player, amount);
            case POLLUTION -> MapStatusBarRuntime.addPollution(player, amount);
            case NONE -> {
            }
        }
        // 必须返回 true：返回值表示「是否继续保留该效果」，与本次是否真的改动数值无关，
        // 返回 false 会让原版立刻移除效果。
        return true;
    }
}
