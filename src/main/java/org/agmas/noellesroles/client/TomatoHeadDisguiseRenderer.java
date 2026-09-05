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

package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.mixin.roles.tomato_head.ItemEntityAgeAccessor;
import org.agmas.noellesroles.role_data.innocence.TomatoHeadRoleData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TomatoHeadDisguiseRenderer {

    private static final Map<UUID, ItemEntity> DROPS = new HashMap<>();

    public static boolean shouldDisguise(AbstractClientPlayer player) {
        return TomatoHeadRoleData.isTomatoForm(player);
    }

    public static boolean render(AbstractClientPlayer player, float yaw, float tickDelta, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCameraEntity() == player && minecraft.options.getCameraType().isFirstPerson()) {
            return true;
        }
        ItemEntity dummy = getDrop(player);
        if (dummy == null) {
            return false;
        }
        dummy.setPos(player.getX(), player.getY(), player.getZ());
        dummy.xo = player.xo;
        dummy.yo = player.yo;
        dummy.zo = player.zo;
        dummy.tickCount = player.tickCount;
        ((ItemEntityAgeAccessor) dummy).noellesroles$setAge(player.tickCount);
        dummy.setInvisible(player.isInvisible());
        EntityRenderer<? super ItemEntity> renderer = minecraft.getEntityRenderDispatcher().getRenderer(dummy);
        renderer.render(dummy, yaw, tickDelta, poseStack, bufferSource, packedLight);
        return true;
    }

    private static ItemEntity getDrop(AbstractClientPlayer player) {
        if (player.level() == null) {
            return null;
        }
        ItemEntity dummy = DROPS.get(player.getUUID());
        if (dummy == null || dummy.level() != player.level()) {
            dummy = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(),
                    new ItemStack(ModItems.TOMATO));
            dummy.setNeverPickUp();
            dummy.setNoGravity(true);
            DROPS.put(player.getUUID(), dummy);
        }
        dummy.setItem(new ItemStack(ModItems.TOMATO));
        return dummy;
    }
}
