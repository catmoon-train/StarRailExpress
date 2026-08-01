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

import io.wifi.starrailexpress.network.original.SniperShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.agmas.noellesroles.gunfx.GunTracers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 狙击枪开火（{@code SniperShootPayload} 的 SHOOT 分支）广播弹道轨迹
 * （注入 SHOOT 分支的枪声播放点 ordinal 0；装填/装卸倍镜声不受影响）。
 */
@Mixin(SniperShootPayload.Receiver.class)
public abstract class SniperFireTracerMixin {

    @Inject(method = "receive(Lio/wifi/starrailexpress/network/original/SniperShootPayload;"
            + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;"
                            + "DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
                    ordinal = 0))
    private void noellesroles$sniperTracer(SniperShootPayload payload, ServerPlayNetworking.Context context,
            CallbackInfo ci) {
        ServerPlayer player = context.player();
        Entity hit = payload.targetOrShooterId() >= 0
                ? player.serverLevel().getEntity(payload.targetOrShooterId()) : null;
        GunTracers.broadcast(player, hit, 200.0D);
    }
}
