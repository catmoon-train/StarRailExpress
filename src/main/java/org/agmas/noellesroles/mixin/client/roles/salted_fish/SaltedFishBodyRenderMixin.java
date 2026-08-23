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

package org.agmas.noellesroles.mixin.client.roles.salted_fish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.render.entity.PlayerBodyEntityRenderer;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.content.entity.SaltedFishBodyEntity;
import org.agmas.noellesroles.role_data.innocence.SaltedFishRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerBodyEntityRenderer.class)
public abstract class SaltedFishBodyRenderMixin {
    @Inject(method = "setupRotations(Lio/wifi/starrailexpress/content/entity/PlayerBodyEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void noellesroles$setupSaltedFishRotations(PlayerBodyEntity body, PoseStack poseStack,
            float animationProgress, float bodyYaw, float tickDelta, float scale, CallbackInfo ci) {
        if (!(body instanceof SaltedFishBodyEntity)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || body.getPlayerUuid() == null) {
            return;
        }
        Player owner = client.level.getPlayerByUUID(body.getPlayerUuid());
        SaltedFishRoleData component = owner == null ? null
                : RoleData.getNullable(SaltedFishRoleData.class, owner);
        if (component == null || !component.isActive()) {
            return;
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(90.0f - bodyYaw));
        poseStack.translate(1.0f, 0.2f + component.getRenderBounce(tickDelta), 0.0f);
        // Z=90: 平躺（与默认尸体渲染一致）
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
        // X 轴翻转：0° = 脸朝上，180° = 脸朝下
        poseStack.mulPose(Axis.XP.rotationDegrees(component.getRenderRoll(tickDelta)));
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
        ci.cancel();
    }
}
