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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 同步旁观者的位置到活跃人格
 * 旁观者和活跃人格保持相同的位置、视角、速度
 */
@Mixin(LocalPlayer.class)
public abstract class SplitPersonalityPositionSyncMixin {

    @Inject(
            method = "tick()V",
            at = @At("HEAD")
    )
    void syncSplitPersonalityPosition(CallbackInfo ci) {
//        LocalPlayer player = (LocalPlayer) (Object) this;
//        var component = SplitPersonalityComponent.KEY.get(player);
//
//        if (component == null || component.getMainPersonality() == null || component.getSecondPersonality() == null) {
//            return;
//        }
//        if (component.getTemporaryRevivalStartTick()>0)return;
//
//        // 如果是旁观者，同步位置到活跃人格
//        if (!component.isCurrentlyActive()) {
//            AbstractClientPlayer activePlayer = (AbstractClientPlayer) player.level().getPlayerByUUID(component.getCurrentActivePerson());
//            if (activePlayer != null && activePlayer != player) {
//                // 同步位置 - 确保旁观者始终在活跃人格的位置
//                player.setPos(activePlayer.getX(), activePlayer.getY(), activePlayer.getZ());
//                player.xRotO = activePlayer.xRotO;
//                player.yRotO = activePlayer.yRotO;
//                player.setXRot(activePlayer.getXRot());
//                player.setYRot(activePlayer.getYRot());
//
//                // 同步眼睛高度
//
//                // 清除所有移动输入
//                player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
//            }
//        }
    }
}
