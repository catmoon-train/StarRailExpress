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

package io.wifi.starrailexpress.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.model.entity.PurpleMonsterModel;
import io.wifi.starrailexpress.content.entity.PurpleMonsterEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class PurpleMonsterRenderer extends MobRenderer<PurpleMonsterEntity, PurpleMonsterModel> {
    private static final ResourceLocation TEXTURE = SRE.id("textures/entity/purple_monster.png");
    private static final ResourceLocation EYES = SRE.id("textures/entity/purple_monster_eyes.png");

    public PurpleMonsterRenderer(EntityRendererProvider.Context context) {
        super(context, new PurpleMonsterModel(context.bakeLayer(PurpleMonsterModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(EYES);
            }
        });
    }

    @Override
    protected void renderNameTag(PurpleMonsterEntity entity, Component component, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, float partialTick) {
    }

    @Override
    public ResourceLocation getTextureLocation(PurpleMonsterEntity entity) {
        return TEXTURE;
    }
}
