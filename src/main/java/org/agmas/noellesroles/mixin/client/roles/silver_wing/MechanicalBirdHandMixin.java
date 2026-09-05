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

package org.agmas.noellesroles.mixin.client.roles.silver_wing;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.agmas.noellesroles.content.entity.MechanicalBirdEntity;
import org.agmas.noellesroles.content.entity.NiaoshoushouMissileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 相机绑在机械小鸟 / 巡飞弹上时，取消第一人称手臂渲染。
 * 只把物品换成 EMPTY 仍会画出玩家手臂。
 */
@Mixin(ItemInHandRenderer.class)
public class MechanicalBirdHandMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void noellesroles$hideHandsWhileCameraIsVehicle(float partialTick, PoseStack poseStack,
            MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int packedLight, CallbackInfo ci) {
        var camera = Minecraft.getInstance().getCameraEntity();
        if (camera instanceof MechanicalBirdEntity || camera instanceof NiaoshoushouMissileEntity) {
            ci.cancel();
        }
    }
}
