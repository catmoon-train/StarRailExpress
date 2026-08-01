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

import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.entity.CanyuesaHorseEntity;

/**
 * 残月萨马渲染器：复用原版马模型，纹理换成残月萨皮肤。
 */
public class CanyuesaHorseRenderer
        extends AbstractHorseRenderer<CanyuesaHorseEntity, HorseModel<CanyuesaHorseEntity>> {

    private static final ResourceLocation TEXTURE = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "textures/entity/canyuesa_horse.png");

    public CanyuesaHorseRenderer(EntityRendererProvider.Context context) {
        super(context, new HorseModel<>(context.bakeLayer(ModelLayers.HORSE)), 1.1F);
    }

    @Override
    public ResourceLocation getTextureLocation(CanyuesaHorseEntity entity) {
        return TEXTURE;
    }
}
