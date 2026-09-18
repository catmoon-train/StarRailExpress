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

package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.BambooSpearEntity;
import org.agmas.noellesroles.init.ModEffects;

/** 竹枪：从持有者手部沿视线伸出，长度在客户端插值，伸缩看起来连续。 */
public class BambooSpearRenderer extends EntityRenderer<BambooSpearEntity> {

    private static final float RADIUS = 0.08F;
    private static final float TIP_LENGTH = 0.38F;
    private static final float MIN_LENGTH = 0.08F;

    public BambooSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(BambooSpearEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer != null && viewer.hasEffect(ModEffects.TIME_STOP)
                && !TimeStopEffect.clientCanMovePlayers.contains(viewer.getUUID())) {
            return;
        }

        Player owner = entity.getOwner();
        float length = Math.max(MIN_LENGTH, entity.getInterpolatedLength(partialTick));
        Vec3 origin;
        Vec3 direction;
        if (owner != null) {
            direction = owner.getViewVector(partialTick);
            origin = owner.getEyePosition(partialTick).add(0.0, -0.22, 0.0).add(direction.scale(0.28));
            if (owner == viewer && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                origin = origin.add(direction.scale(0.45));
            }
        } else {
            float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            direction = entity.calculateViewVector(pitch, yaw);
            origin = entity.getPosition(partialTick);
        }

        Vec3 entityPos = entity.getPosition(partialTick);
        poseStack.pushPose();
        poseStack.translate(origin.x - entityPos.x, origin.y - entityPos.y, origin.z - entityPos.z);
        BambooPoleGeometry.orient(poseStack, direction);
        BambooPoleGeometry.render(poseStack, bufferSource, packedLight, 0.0F, length, RADIUS, TIP_LENGTH);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(BambooSpearEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
