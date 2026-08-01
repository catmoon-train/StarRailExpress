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

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 操纵师附身期间冻结操纵师本体：
 * 在 {@link KeyboardInput#tick} 之后清零本体的移动输入，使按键只用于"远程驱动目标"，
 * 而不会让操纵师自己的身体移动。注意这里不修改 {@code KeyMapping#isDown}，
 * 因此驱动逻辑仍能读取到（经效果拦截后的）按键状态。
 */
@Mixin(KeyboardInput.class)
public abstract class ManipulatorControllerInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void noe$freezeControllerBody(boolean isSneaking, float sneakSpeed, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null)
            return;
        ManipulatorPlayerComponent comp = ManipulatorPlayerComponent.KEY.get(client.player);
        if (comp.isControlling && comp.target != null) {
            Input self = (Input) (Object) this;
            self.up = false;
            self.down = false;
            self.left = false;
            self.right = false;
            self.jumping = false;
            self.shiftKeyDown = false;
            self.forwardImpulse = 0f;
            self.leftImpulse = 0f;
        }
    }
}
