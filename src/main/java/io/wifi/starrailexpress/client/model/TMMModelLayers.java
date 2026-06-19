package io.wifi.starrailexpress.client.model;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.model.entity.CustomPlayerPlushModel;
import io.wifi.starrailexpress.client.model.entity.PlayerSkeletonEntityModel;
import io.wifi.starrailexpress.client.render.block_entity.PlaneSmallDoorBlockEntityRenderer;
import io.wifi.starrailexpress.client.render.block_entity.SmallDoorBlockEntityRenderer;
import io.wifi.starrailexpress.client.render.block_entity.UpSmallDoorBlockEntityRenderer;
import io.wifi.starrailexpress.client.render.block_entity.WheelBlockEntityRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

public interface TMMModelLayers {
    ModelLayerLocation SMALL_DOOR = layer("small_door");
    ModelLayerLocation PLANE_SMALL_DOOR = layer("plane_small_door");
    ModelLayerLocation UP_SMALL_DOOR = layer("up_small_door");
    ModelLayerLocation PLAYER_BODY = layer("player_body");
    ModelLayerLocation PLAYER_BODY_SLIM = layer("player_body_slim");
    ModelLayerLocation WHEEL = layer("wheel");
    ModelLayerLocation PLAYER_SKELETON = layer("player_skeleton");
    ModelLayerLocation CUSTOM_PLAYER_PLUSH = layer("custom_player_plush");

    static void initialize() {
        EntityModelLayerRegistry.registerModelLayer(CUSTOM_PLAYER_PLUSH, CustomPlayerPlushModel::createBodyLayer);

        EntityModelLayerRegistry.registerModelLayer(SMALL_DOOR, SmallDoorBlockEntityRenderer::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(UP_SMALL_DOOR, UpSmallDoorBlockEntityRenderer::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(PLANE_SMALL_DOOR, PlaneSmallDoorBlockEntityRenderer::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(PLAYER_BODY, () -> LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64));
        EntityModelLayerRegistry.registerModelLayer(PLAYER_BODY_SLIM, () -> LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, true), 64, 64));
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
