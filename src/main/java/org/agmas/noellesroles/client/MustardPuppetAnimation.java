package org.agmas.noellesroles.client;

import java.util.Map;
import java.util.WeakHashMap;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.mixin.client.command_block.IronGolemAnimationAccessor;

/** 攻击事件早于渲染临时实体创建也能保留；按玩家实例弱引用避免跨服残留。 */
public final class MustardPuppetAnimation {
    private static final Map<Player, Long> ATTACKS = new WeakHashMap<>();

    private MustardPuppetAnimation() {
    }

    public static void onAttack(Player player) {
        if (EntityDisguise.get(player).type() == EntityType.IRON_GOLEM) {
            ATTACKS.put(player, player.level().getGameTime());
        }
    }

    public static void apply(Player player, Entity dummy) {
        if (!(dummy instanceof IronGolem)) {
            ATTACKS.remove(player);
            return;
        }
        Long started = ATTACKS.get(player);
        long elapsed = started == null ? 10 : player.level().getGameTime() - started;
        int remaining = elapsed >= 0 && elapsed < 10 ? 10 - (int) elapsed : 0;
        ((IronGolemAnimationAccessor) dummy).mustard$setAttackAnimationTick(remaining);
        if (remaining == 0) ATTACKS.remove(player);
    }
}
