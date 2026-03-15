package org.agmas.noellesroles.modifier.taxed;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.modifier.NRModifiers;

/**
 * 纳税修饰符处理器
 * 效果：
 * - 从击杀和被动收入中获得的金币减少25%
 */
public final class TaxedModifier {

    private TaxedModifier() {
    }

    private static final float COIN_REDUCTION = 0.25f;

    public static void init() {
        // 初始化方法，修饰符逻辑通过Mixin实现
    }

    /**
     * 应用税收
     * @param amount 原始金币数量
     * @return 扣税后的金币数量
     */
    public static int applyTax(int amount) {
        if (amount <= 0) {
            return amount;
        }
        return (int) Math.floor(amount * (1.0f - COIN_REDUCTION));
    }

    /**
     * 检查玩家是否应该被征税
     */
    public static boolean shouldApplyTax(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }

        net.minecraft.server.level.ServerLevel world = player.serverLevel();
        var gameWorld = SREGameWorldComponent.KEY.get(world);

        if (!gameWorld.isRunning()) {
            return false;
        }

        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(world);
        return worldModifierComponent.isModifier(player, NRModifiers.TAXED);
    }
}
