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

package org.agmas.noellesroles.content.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public class WheelchairEntityRenderer extends LivingEntityRenderer<WheelchairEntity, WheelchairEntityModel> {
    private static final ResourceLocation TEXTURE = Noellesroles.id("textures/entity/wheelchair.png");

    public WheelchairEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new WheelchairEntityModel(context.bakeLayer(WheelchairEntityModel.LAYER_LOCATION)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(WheelchairEntity entity) {
        return TEXTURE;
    }
}