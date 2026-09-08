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

package pro.fazeclan.river.stupid_express.mixin.modifier.twin_children;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenHandler;

/**
 * Lower twin: 1.1-block collision so the unit cannot crawl into 1-block gaps.
 * Upper twin: 0.7-block collision so weapons from above do not sit inside the
 * rider. The upper twin also cannot sneak-dismount.
 */
@Mixin(Player.class)
public abstract class TwinChildrenPlayerMixin {

    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
    private EntityDimensions stupidExpress$stackedTwinHitbox(EntityDimensions dimensions, Pose pose) {
        Player self = (Player) (Object) this;
        if (TwinChildrenHandler.isStackedLower(self)) {
            float heightScale = TwinChildrenHandler.stackedHeightScale(dimensions.height());
            if (Math.abs(heightScale - 1.0F) <= 1.0e-4F) {
                return dimensions;
            }
            return dimensions.scale(1.0F, heightScale).withEyeHeight(dimensions.eyeHeight());
        }
        if (TwinChildrenHandler.isStackedUpper(self)) {
            float heightScale = TwinChildrenHandler.upperHeightScale(dimensions.height());
            if (Math.abs(heightScale - 1.0F) <= 1.0e-4F) {
                return dimensions;
            }
            return dimensions.scale(1.0F, heightScale);
        }
        return dimensions;
    }

    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void stupidExpress$keepTwinRiding(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (TwinChildrenHandler.shouldStayRiding(self)) {
            cir.setReturnValue(false);
        }
    }
}
