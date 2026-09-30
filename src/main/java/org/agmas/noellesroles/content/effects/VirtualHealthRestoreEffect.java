package org.agmas.noellesroles.content.effects;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;

/**
 * 虚拟血量恢复（护士体系）。
 *
 * <p>拥有此效果的玩家<b>每秒恢复 1 点虚拟血量</b>（{@link DreamHealthComponent}）；
 * 等级越高每秒恢复越多（1 级 = 1 点/秒，2 级 = 2 点/秒，以此类推）。
 * 虚拟血量已满时本跳不回血，但效果仍然保留（满血的玩家也能持有），
 * 因此效果持续期间一旦受伤就会继续回血，直到时长自然走完。
 */
public class VirtualHealthRestoreEffect extends SimpleMobEffect {
    /** 结算周期：每秒一次。 */
    private static final int TICK_INTERVAL = 20;

    public VirtualHealthRestoreEffect(MobEffectCategory category, int color) {
        super(category, color);
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
        int amount = 1 + amplifier;
        // 满血时 restore 返回 false，但这里必须返回 true：
        // 返回值表示「是否继续保留该效果」，返回 false 会让原版立刻移除效果，
        // 导致满血玩家拿不到「虚拟血量恢复」，效果持续期间再受伤也无法回血。
        DreamHealthComponent.KEY.get(player).restore(amount);
        return true;
    }
}
