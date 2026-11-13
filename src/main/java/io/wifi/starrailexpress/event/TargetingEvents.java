package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 目标候选过滤：在射线选取和技能分发之前调用，否决不会吞掉后方候选。 */
public final class TargetingEvents {
    private TargetingEvents() {}

    /** 双端枪械射线的候选门禁；不影响刀、普通交互或方块碰撞。 */
    public static final Event<AllowTarget> ALLOW_GUN_TARGET = targetGate();
    /** 双端技能选人及服务端指定 UUID 分发的门禁；不用于已开始的小游戏结算。 */
    public static final Event<AllowTarget> ALLOW_SKILL_TARGET = targetGate();

    static Event<AllowTarget> targetGate() {
        return EventFactory.createArrayBacked(AllowTarget.class, listeners -> (actor, target) -> {
            for (AllowTarget listener : listeners) {
                if (!listener.allow(actor, target)) return false;
            }
            return true;
        });
    }

    @FunctionalInterface
    public interface AllowTarget {
        boolean allow(Player actor, Entity target);
    }
}
