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

package org.agmas.noellesroles.mixin.roles.trapper;

import io.wifi.starrailexpress.network.original.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import org.agmas.noellesroles.content.entity.TripwireTrapEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 设陷者绊线的唯一移除方式：被枪打掉。
 * 客户端枪械射线拾取到 {@link TripwireTrapEntity} 时（见 RevolverItem#getGunTarget），
 * 在服务端把该绊线击落。不取消原处理，枪声/冷却照常。
 */
@Mixin(GunShootPayload.Receiver.class)
public class TrapperTrapGunPayloadMixin {
    @Inject(method = "receive", at = @At("HEAD"))
    private void handleTripwireTarget(GunShootPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        ServerPlayer player = context.player();
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(TMMItemTags.GUNS)) {
            return;
        }
        if (player.serverLevel().getEntity(payload.target()) instanceof TripwireTrapEntity wire) {
            wire.shotDown(player);
        }
    }
}
