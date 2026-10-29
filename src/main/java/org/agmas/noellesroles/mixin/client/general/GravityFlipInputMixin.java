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

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.agmas.noellesroles.content.effects.GravityFlipEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 重力颠倒的操作修正：视野被 180° 滚转（{@code GravityFlipCameraMixin}）后，
 * 画面左右相对观察者是互换的，这里把左右移动输入取反，让操作恢复「符合画面」：
 * <ul>
 *     <li>前后：原版即朝准星（画面正中央），滚转不改变径向，无需处理；</li>
 *     <li>左右：滚转后按 A 会走向画面右侧，取反 leftImpulse 后 A 重新对应画面左侧；</li>
 *     <li>鼠标：见 {@code GravityFlipMouseMixin}（俯仰与偏航同样取反）；</li>
 *     <li>上下：重力反向（向天坠落、跳跃向下蹬）是反重力本身，保持翻转。</li>
 * </ul>
 * 最终效果：世界上下颠倒，但玩家的操作相对画面完全正常。
 */
@Mixin(KeyboardInput.class)
public abstract class GravityFlipInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void noellesroles$uninvertSideways(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !GravityFlipEffect.isFlipped(client.player)) {
            return;
        }
        Input input = (Input) (Object) this;
        input.leftImpulse = -input.leftImpulse;
    }
}
