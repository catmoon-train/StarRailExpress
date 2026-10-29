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

package org.agmas.noellesroles.mixin.client.general;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.content.effects.GravityFlipEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 重力颠倒的鼠标修正：视野被 180° 滚转后，鼠标的偏航/俯仰相对画面都是反的
 * （鼠标右转视角却向左偏、鼠标上抬画面却向下看），这里把两个轴一起取反，
 * 让视角转动重新符合画面方向。配合 {@code GravityFlipInputMixin}，
 * 最终「世界上下颠倒、操作相对画面完全正常」。
 */
@Mixin(MouseHandler.class)
public abstract class GravityFlipMouseMixin {

    @WrapOperation(method = "turnPlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void noellesroles$uninvertMouseTurn(LocalPlayer player, double yawDelta, double pitchDelta,
            Operation<Void> original) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && GravityFlipEffect.isFlipped(client.player)) {
            yawDelta = -yawDelta;
            pitchDelta = -pitchDelta;
        }
        original.call(player, yawDelta, pitchDelta);
    }
}
