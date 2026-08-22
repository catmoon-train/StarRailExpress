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

import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.killer.dream.DreamPlayerComponent;

/**
 * Dream 的船（商店 100 金币）。
 *
 * <p>对地面右键放置一条船：{@code dreamBoatDurationSeconds}（默认 10s）内
 * 半径 {@code dreamBoatRadius} 格的玩家会被强制拽上船（挣脱也会被拽回来），
 * 到点后船消失。使用冷却 {@code dreamBoatCooldownSeconds}（默认 60s），物品不消耗。
 * 乘坐/消失逻辑见 {@link DreamPlayerComponent#placeBoat}。
 * 实现 {@code AdventureUsable} 以通过冒险模式的物品对方块使用门禁。
 */
public class DreamBoatItem extends Item implements AdventureUsable {
    public DreamBoatItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 pos = Vec3.atBottomCenterOf(placePos);
        if (!DreamPlayerComponent.KEY.get(sp).placeBoat(sp, pos)) {
            return InteractionResult.FAIL;
        }
        player.getCooldowns().addCooldown(this,
                NoellesRolesConfig.HANDLER.instance().dreamBoatCooldownSeconds * 20);
        return InteractionResult.SUCCESS;
    }
}
