package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.KnifeItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.FakeKnifeItem;

/** 仅渲染期间改变刀的光泽，不修改真实附魔、物品组件或玩法状态。 */
public final class KnifeGlintContext {
    private record Held(Player player, ItemStack stack) {}
    private static final ThreadLocal<Held> CURRENT = new ThreadLocal<>();
    private KnifeGlintContext() {}

    public static void render(LivingEntity entity, ItemStack stack, Runnable action) {
        Held previous = CURRENT.get();
        // 显式覆盖空上下文，防止GUI/掉落物嵌套渲染继承另一个玩家的身份。
        if (entity instanceof Player player) CURRENT.set(new Held(player, stack));
        else CURRENT.remove();
        try { action.run(); }
        finally { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }

    /** null 表示沿用原版；假刀在持握渲染中始终不增加光泽。鸟兽兽军刀继承 KnifeItem。 */
    public static Boolean foil(ItemStack stack) {
        Held held = CURRENT.get();
        if (held == null || held.stack != stack || stack.isEmpty()) return null;
        boolean fake = stack.getItem() instanceof FakeKnifeItem;
        boolean knife = stack.getItem() instanceof KnifeItem;
        if (!knife && !fake) return null;
        RoleTeam team = RoleTeam.of(SREGameWorldComponent.KEY.get(held.player.level()).getRole(held.player));
        boolean eligible = team == RoleTeam.CIVILIAN || team != null && switch (team) {
            case NEUTRAL, NEUTRAL_INNOCENT, NEUTRAL_KILLER, NEUTRAL_SPECIAL,
                    NEUTRAL_EVENT, NEUTRAL_INDEPENDENT_WIN -> true;
            default -> false;
        };
        return KnifeGlintRules.foil(knife, fake, eligible);
    }
}
