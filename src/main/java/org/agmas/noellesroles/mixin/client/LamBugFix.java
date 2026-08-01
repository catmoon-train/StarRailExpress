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

package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 使用 @Pseudo 标记表示这是一个伪 Mixin，当目标类不存在时不会导致编译失败
@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.LambDynLights")
public class LamBugFix {
    @Inject(method = "getLivingEntityLuminanceFromItems", at = @At("HEAD"), cancellable = true)
    private static void getLivingEntityLuminanceFromItems(LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof PlayerBodyEntity) {
            cir.setReturnValue(3);
            cir.cancel();
        }
    }
}