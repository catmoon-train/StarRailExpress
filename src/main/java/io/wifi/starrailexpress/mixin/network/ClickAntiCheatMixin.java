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

package io.wifi.starrailexpress.mixin.network;

import io.wifi.starrailexpress.anticheat.ClickAntiCheat;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 统计并在超限后拦截左右键相关数据包。对空左键只能从挥手包识别。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ClickAntiCheatMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
    private void sre$clickAcSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        if (ClickAntiCheat.onClick(this.player, ClickAntiCheat.ClickType.LEFT)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void sre$clickAcUse(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (ClickAntiCheat.onClick(this.player, ClickAntiCheat.ClickType.RIGHT)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void sre$clickAcUseOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (ClickAntiCheat.onClick(this.player, ClickAntiCheat.ClickType.RIGHT)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void sre$clickAcInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        ClickAntiCheat.ClickType type = sre$isAttack(packet)
                ? ClickAntiCheat.ClickType.LEFT
                : ClickAntiCheat.ClickType.RIGHT;
        if (ClickAntiCheat.onClick(this.player, type)) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void sre$clickAcAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ServerboundPlayerActionPacket.Action action = packet.getAction();
        if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            if (ClickAntiCheat.onClick(this.player, ClickAntiCheat.ClickType.LEFT)) {
                ci.cancel();
            }
            return;
        }
        if ((action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK
                || action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)
                && ClickAntiCheat.isLocked(this.player)) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean sre$isAttack(ServerboundInteractPacket packet) {
        boolean[] attack = {false};
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
            }

            @Override
            public void onInteraction(InteractionHand hand, Vec3 pos) {
            }

            @Override
            public void onAttack() {
                attack[0] = true;
            }
        });
        return attack[0];
    }
}
