package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** 带关节的巨大鬼手，指节按抓握进度弯曲。 */
final class SackGhostHandModel {
    private final ModelPart root;
    private final ModelPart[] fingers = new ModelPart[5];

    SackGhostHandModel() {
        MeshDefinition mesh = new MeshDefinition();
        var palm = mesh.getRoot().addOrReplaceChild("palm", CubeListBuilder.create().texOffs(0, 32)
                .addBox(-5, -3, -2, 10, 9, 4).addBox(-3, 5, -1.5F, 6, 12, 3), PartPose.ZERO);
        for (int i = 0; i < 5; i++) {
            float x = i == 4 ? -6 : -4 + i * 2.6F;
            var finger = palm.addOrReplaceChild("finger" + i, CubeListBuilder.create().texOffs(18, 32)
                    .addBox(-0.9F, -7, -1, 1.8F, 7, 2), PartPose.offsetAndRotation(x, i == 4 ? 2 : -3, 0, 0, 0, i == 4 ? -0.9F : 0));
            finger.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(26, 32)
                    .addBox(-0.75F, -6, -0.8F, 1.5F, 6, 1.6F), PartPose.offset(0, -7, 0));
        }
        root = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("palm");
        for (int i = 0; i < 5; i++) fingers[i] = root.getChild("finger" + i);
    }

    void render(PoseStack poses, MultiBufferSource buffers, float grip, float alpha) {
        for (int i = 0; i < fingers.length; i++) {
            fingers[i].xRot = -grip * (i == 4 ? 0.9F : 1.2F);
            fingers[i].getChild("tip").xRot = -grip * 1.3F;
        }
        root.render(poses, buffers.getBuffer(RenderType.entityTranslucent(SackGhostRenderer.TEXTURE)),
                0x700070, OverlayTexture.NO_OVERLAY, ((int) (alpha * 255) << 24) | 0x493D51);
    }
}
