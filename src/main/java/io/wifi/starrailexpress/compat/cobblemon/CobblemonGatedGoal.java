package io.wifi.starrailexpress.compat.cobblemon;

import net.minecraft.world.entity.ai.goal.Goal;

/** 保留注册的避让目标，关闭配置时通过正常 stop 流程退出，不改动原版其他 AI。 */
public final class CobblemonGatedGoal extends Goal {
    private final Goal delegate;

    public CobblemonGatedGoal(Goal delegate) {
        this.delegate = delegate;
        setFlags(delegate.getFlags());
    }

    @Override public boolean canUse() { return CobblemonMechanics.enabled() && delegate.canUse(); }
    @Override public boolean canContinueToUse() { return CobblemonMechanics.enabled() && delegate.canContinueToUse(); }
    @Override public boolean isInterruptable() { return delegate.isInterruptable(); }
    @Override public boolean requiresUpdateEveryTick() { return delegate.requiresUpdateEveryTick(); }
    @Override public void start() { delegate.start(); }
    @Override public void stop() { delegate.stop(); }
    @Override public void tick() { if (CobblemonMechanics.enabled()) delegate.tick(); }
}
