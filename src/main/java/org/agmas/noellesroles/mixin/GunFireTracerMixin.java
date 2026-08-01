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

package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.gunfx.GunTracers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 枪械开火（左轮/德林加/处刑枪等 {@code GunShootPayload}）成功后（方法尾部）广播弹道轨迹
 * （{@link GunTracers}，客户端 {@code GunTracerRenderer} 渲染）。
 */
@Mixin(GunShootPayload.Receiver.class)
public abstract class GunFireTracerMixin {

    @Inject(method = "receive(Lio/wifi/starrailexpress/network/original/GunShootPayload;"
            + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At("TAIL"))
    private void noellesroles$gunTracer(GunShootPayload payload, ServerPlayNetworking.Context context,
            CallbackInfo ci) {
        ServerPlayer player = context.player();
        if (!player.getMainHandItem().is(TMMItemTags.GUNS)) {
            return;
        }
        Entity hit = payload.target() >= 0 ? player.serverLevel().getEntity(payload.target()) : null;
        GunTracers.broadcast(player, hit, 30.0D);
    }
}
