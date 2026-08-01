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

package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.entity.FirecrackerEntity;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class FirecrackerItem extends Item implements AdventureUsable {
    public FirecrackerItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        if (context.getClickedFace().equals(Direction.UP)) {
            Player player = context.getPlayer();
            Level world = player.level();
            if (!world.isClientSide) {
                FirecrackerEntity firecracker = TMMEntities.FIRECRACKER.create(world);
                Vec3 spawnPos = context.getClickLocation();

                firecracker.setPos(spawnPos.x(), spawnPos.y(), spawnPos.z());
                firecracker.setYRot(player.getYHeadRot());
                world.addFreshEntity(firecracker);
                if (!player.isCreative()) {
                    if (SRE.REPLAY_MANAGER != null) {
                        SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
                    }
                    player.getItemInHand(context.getHand()).shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }
}