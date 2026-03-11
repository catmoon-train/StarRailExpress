package org.agmas.noellesroles.entity;

import org.agmas.noellesroles.Noellesroles;

// Made with Blockbench 5.0.7
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class WheelchairEntityModel extends EntityModel<WheelchairEntity> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = 
        new ModelLayerLocation(Noellesroles.id("wheelchair"), "main");
	private final ModelPart bb_main;

	public WheelchairEntityModel(ModelPart root) {
		this.bb_main = root.getChild("bb_main");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition bb_main = partdefinition.addOrReplaceChild("bb_main", CubeListBuilder.create().texOffs(32, 32).addBox(6.0F, -1.0F, -2.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(38, 46).addBox(6.0F, -7.0F, 3.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(32, 39).addBox(6.0F, -7.0F, -3.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(46, 40).addBox(6.0F, -6.0F, -3.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(32, 30).addBox(-7.0F, -4.0F, 0.0F, 14.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(42, 46).addBox(-7.0F, -7.0F, 3.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(46, 0).addBox(-7.0F, -7.0F, -3.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(46, 7).addBox(-7.0F, -1.0F, -2.0F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
		.texOffs(46, 47).addBox(-7.0F, -6.0F, -3.0F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 49).addBox(-7.0F, -6.0F, 0.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(4, 49).addBox(6.0F, -6.0F, 0.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-5.0F, -6.0F, -7.0F, 10.0F, 2.0F, 13.0F, new CubeDeformation(0.0F))
		.texOffs(0, 15).addBox(5.0F, -13.0F, -6.0F, 1.0F, 2.0F, 15.0F, new CubeDeformation(0.0F))
		.texOffs(0, 32).addBox(-6.0F, -13.0F, -6.0F, 1.0F, 2.0F, 15.0F, new CubeDeformation(0.0F))
		.texOffs(32, 46).addBox(-6.0F, -11.0F, -1.0F, 1.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(46, 32).addBox(5.0F, -11.0F, -1.0F, 1.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition cube_r1 = bb_main.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(32, 15).addBox(-5.0F, -9.0F, -1.0F, 10.0F, 13.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -8.0F, 7.0F, -0.3927F, 0.0F, 0.0F));

		PartDefinition cube_r2 = bb_main.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(12, 49).addBox(0.0F, -1.0F, 0.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(8, 49).addBox(13.0F, -1.0F, 0.0F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-7.0F, -3.0F, -1.0F, 1.5708F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(WheelchairEntity entity, float f, float g, float h, float i, float j) {
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int j, int k) {
		bb_main.render(poseStack, vertexConsumer, i, j, k);
	}
}