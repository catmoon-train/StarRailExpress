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
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.ThrownBambooEntity;
import org.agmas.noellesroles.init.ModEffects;

/** 投掷竹子：沿飞行方向绘制带竹节和尖头的 3D 竹杆。 */
public class ThrownBambooRenderer extends EntityRenderer<ThrownBambooEntity> {

    private static final float RADIUS = 0.085F;
    private static final float TIP_LENGTH = 0.4F;

    public ThrownBambooRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(ThrownBambooEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(ModEffects.TIME_STOP)
                && !TimeStopEffect.clientCanMovePlayers.contains(player.getUUID())) {
            return;
        }

        Vec3 direction = Vec3.directionFromRotation(0.0F, entity.getRenderYaw(partialTick));
        poseStack.pushPose();
        BambooPoleGeometry.orient(poseStack, direction);
        BambooPoleGeometry.render(poseStack, bufferSource, packedLight, -ThrownBambooEntity.TAIL_REACH,
                ThrownBambooEntity.TIP_REACH, RADIUS, TIP_LENGTH);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(ThrownBambooEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
