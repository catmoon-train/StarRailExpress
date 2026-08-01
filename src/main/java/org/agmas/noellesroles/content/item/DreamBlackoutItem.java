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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.config.NoellesRolesConfig;

/**
 * Dream 的范围关灯（商店 150 金币，一次性）。
 *
 * <p>右键使用：以自己为中心、半径 {@code dreamBlackoutRadius}（默认 30 格）内的灯
 * 全部熄灭（复用 {@link SREWorldBlackoutComponent} 的区域关灯），并触发标准关灯
 * 音效/致盲流程。使用后物品消耗。
 */
public class DreamBlackoutItem extends Item {
    public DreamBlackoutItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }
        if (!(player instanceof ServerPlayer sp) || sp.isSpectator()) {
            return InteractionResultHolder.fail(stack);
        }
        var config = NoellesRolesConfig.HANDLER.instance();
        SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(level);
        blackout.triggerBlackout(sp.blockPosition(), config.dreamBlackoutRadius, true,
                SREWorldBlackoutComponent.getMaxDuration(level));
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }
}
