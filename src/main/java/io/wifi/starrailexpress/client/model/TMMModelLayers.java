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

package io.wifi.starrailexpress.client.model;

import org.agmas.noellesroles.init.ModSceneBlocks;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.model.entity.PlayerSkeletonEntityModel;
import io.wifi.starrailexpress.client.render.block_entity.PlaneSmallDoorBlockEntityRenderer;
import io.wifi.starrailexpress.client.render.block_entity.SmallDoorBlockEntityRenderer;
import io.wifi.starrailexpress.client.render.block_entity.UpSmallDoorBlockEntityRenderer;
import io.wifi.starrailexpress.client.render.block_entity.WheelBlockEntityRenderer;
import io.wifi.starrailexpress.index.SREBlocks;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.RenderType;

public interface TMMModelLayers {
    ModelLayerLocation SMALL_DOOR = layer("small_door");
    ModelLayerLocation PLANE_SMALL_DOOR = layer("plane_small_door");
    ModelLayerLocation UP_SMALL_DOOR = layer("up_small_door");
    ModelLayerLocation PLAYER_BODY = layer("player_body");
    ModelLayerLocation PLAYER_BODY_SLIM = layer("player_body_slim");
    ModelLayerLocation WHEEL = layer("wheel");
    ModelLayerLocation PLAYER_SKELETON = layer("player_skeleton");

    static void initialize() {
        BlockRenderLayerMap.INSTANCE.putBlock(ModSceneBlocks.BREAKING_BRIDGE, RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(ModSceneBlocks.FAKE_BLOCK, RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(SREBlocks.TRAIN_TORCH, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(SREBlocks.TRAIN_SOUL_LANTERN, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(SREBlocks.TRAIN_VANILLA_LANTERN, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(SREBlocks.WALL_TRAIN_TORCH, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(SREBlocks.TRAIN_TORCH_LEVER, RenderType.cutout());
        EntityModelLayerRegistry.registerModelLayer(SMALL_DOOR, SmallDoorBlockEntityRenderer::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(UP_SMALL_DOOR,
                UpSmallDoorBlockEntityRenderer::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(PLANE_SMALL_DOOR,
                PlaneSmallDoorBlockEntityRenderer::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(PLAYER_BODY,
                () -> LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64));
        EntityModelLayerRegistry.registerModelLayer(PLAYER_BODY_SLIM,
                () -> LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64));
        EntityModelLayerRegistry.registerModelLayer(WHEEL, WheelBlockEntityRenderer::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(PLAYER_SKELETON, PlayerSkeletonEntityModel::getTexturedModelData);
    }

    public static ModelLayerLocation layer(String id, String name) {
        return new ModelLayerLocation(SRE.watheId(id), name);
    }

    public static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(SRE.watheId(id), "main");
    }
}
