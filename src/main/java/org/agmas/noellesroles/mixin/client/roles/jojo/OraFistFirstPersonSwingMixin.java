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

package org.agmas.noellesroles.mixin.client.roles.jojo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.role_data.vigilante.JojoRoleData;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 欧拉连打时第一人称左右手分别按相反相位挥拳。
 */
@Mixin(ItemInHandRenderer.class)
public class OraFistFirstPersonSwingMixin {

    @WrapMethod(method = "renderArmWithItem")
    private void noellesroles$oraBothHands(AbstractClientPlayer player, float partialTicks, float pitch,
            InteractionHand hand, float swingProgress, ItemStack stack, float equippedProgress, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, Operation<Void> original) {
        if (player != null && JojoRoleData.isRushing(player)) {
            float wave = (player.tickCount + partialTicks) * 1.85f;
            float phase = hand == InteractionHand.OFF_HAND ? wave + (float) Math.PI : wave;
            swingProgress = (Mth.sin(phase) + 1.0f) * 0.5f;
        }
        original.call(player, partialTicks, pitch, hand, swingProgress, stack, equippedProgress, poseStack, buffer,
                packedLight);
    }
}
