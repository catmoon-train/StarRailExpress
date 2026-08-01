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

package org.agmas.noellesroles.game.modifier.taxed;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.game.modifier.NRModifiers;

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
