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

package org.agmas.noellesroles.mixin.client.roles.manipulator;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.role_data.killer.ManipulatorRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 操纵师附身期间，禁止操纵师"本体"触发原版的攻击 / 使用交互。
 *
 * <p>附身时相机绑定在目标身上，操纵师按下左/右键本意是驱动目标（开门等通过
 * {@link org.agmas.noellesroles.packet.ManipulatorControlInputC2SPacket} 转发到服务端、以目标身份执行）；
 * 若不拦截，原版仍会让操纵师本体就地攻击/使用，可能产生重复交互或误伤。
 */
@Mixin(Minecraft.class)
public abstract class ManipulatorUseSuppressMixin {

    private static boolean noe$manipulatorControlling() {
        Minecraft client = Minecraft.getInstance();
        if (client == null)
            return false;
        LocalPlayer player = client.player;
        if (player == null)
            return false;
        ManipulatorRoleData comp = RoleData.getNullable(ManipulatorRoleData.class, player);
        return comp != null && comp.isControlling && comp.target != null;
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void noe$cancelControllerUse(CallbackInfo ci) {
        if (noe$manipulatorControlling())
            ci.cancel();
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void noe$cancelControllerAttack(CallbackInfoReturnable<Boolean> cir) {
        if (noe$manipulatorControlling())
            cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void noe$cancelControllerContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (noe$manipulatorControlling())
            ci.cancel();
    }
}
