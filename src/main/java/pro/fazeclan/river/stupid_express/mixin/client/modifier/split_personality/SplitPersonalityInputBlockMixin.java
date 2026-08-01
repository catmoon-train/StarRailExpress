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

package pro.fazeclan.river.stupid_express.mixin.client.modifier.split_personality;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 禁止旁观者的任何输入操作
 * 旁观者无法移动、跳跃、攻击或交互
 */
@Mixin(LocalPlayer.class)
public abstract class SplitPersonalityInputBlockMixin {

//    @Inject(
//            method = "tick()V",
//            at = @At("HEAD")
//    )
//    void blockSplitPersonalityObserverInput(CallbackInfo ci) {
//        LocalPlayer player = (LocalPlayer) (Object) this;
//        var component = SplitPersonalityComponent.KEY.get(player);
//        if (component.getTemporaryRevivalStartTick()>0)return;
//        // 如果是旁观者，禁用所有输入
//        if (component != null && component.getMainPersonality() != null && !component.isCurrentlyActive()) {
//            // 禁止移动
//            player.input.up = false;
//            player.input.down = false;
//            player.input.left = false;
//            player.input.right = false;
//            player.input.jumping = false;
//            player.input.shiftKeyDown = false;
//
//            // 禁止飞行
//            player.getAbilities().flying = false;
//
//            // 清除移动向量
//            player.setDeltaMovement(Vec3.ZERO);
//            player.hasImpulse = false;
//        }
//    }
}
