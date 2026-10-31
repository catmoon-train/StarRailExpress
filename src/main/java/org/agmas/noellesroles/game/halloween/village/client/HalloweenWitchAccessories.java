package org.agmas.noellesroles.game.halloween.village.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 独立于玩家皮肤的魔女帽、扫帚与手持南瓜，全部使用原版模型单位和 box UV。 */
@Environment(EnvType.CLIENT)
final class HalloweenWitchAccessories {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "noellesroles", "textures/entity/halloween_canyue_accessories.png");
    static final int TEXTURE_SIZE = 128;
    static final float HAT_DROP = 1.25F;
    final ModelPart root;
    final ModelPart witchHat;
    final ModelPart broom;
    final ModelPart bristles;
    final ModelPart pumpkinHand;
    final ModelPart pumpkin;

    HalloweenWitchAccessories() {
        root = createLayer().bakeRoot();
        witchHat = root.getChild("witch_hat");
        broom = root.getChild("broom");
        bristles = broom.getChild("bristles");
        pumpkinHand = root.getChild("pumpkin_hand");
        pumpkin = pumpkinHand.getChild("pumpkin");
    }

    static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition hatMount = mesh.getRoot().addOrReplaceChild("witch_hat", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition hat = hatMount.addOrReplaceChild("fitted_hat", CubeListBuilder.create(),
                PartPose.offset(0, HAT_DROP, 0));
        // 贴近皮肤帽层的八边形宽帽檐；酒红衬里与黑色上表面错开，避免共面闪点。
        box(hat, "brim_front", 0, 0, -7, -9.125F, -7, 14, .5F, 14);
        box(hat, "brim_middle", 0, 0, -9, -9.125F, -5, 18, .5F, 10);
        box(hat, "brim_lining", 64, 24, -8.75F, -8.625F, -4.75F, 17.5F, .25F, 9.5F);
        box(hat, "lining_front", 64, 24, -6.75F, -8.625F, -6.75F, 13.5F, .25F, 13.5F);
        box(hat, "crown_base", 0, 0, -4.5F, -12.75F, -4, 9, 3.625F, 8);
        // 帽冠用少量可编辑分段收尖并折弯，不加载外部人物骨骼。
        PartDefinition crown = hat.addOrReplaceChild("crown", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.75F, -3.75F, -3.25F, 7.5F, 4.25F, 6.5F),
                PartPose.offsetAndRotation(.4F, -12.3F, 0, 0, 0, .16F));
        PartDefinition tip = crown.addOrReplaceChild("tip", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-2.5F, -3.2F, -2.25F, 5, 3.7F, 4.5F),
                PartPose.offsetAndRotation(.25F, -3.1F, 0, 0, 0, .32F));
        PartDefinition curl = tip.addOrReplaceChild("curl", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-1.5F, -2.8F, -1.5F, 3, 3.3F, 3),
                PartPose.offsetAndRotation(.15F, -2.65F, 0, 0, 0, .50F));
        curl.addOrReplaceChild("point", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-.65F, -2.3F, -.75F, 1.3F, 2.8F, 1.5F),
                PartPose.offsetAndRotation(0, -2.35F, 0, 0, 0, .80F));
        box(hat, "band_front", 64, 0, -4.6F, -10.625F, -4.125F, 9.2F, 1.125F, .25F);
        box(hat, "band_back", 64, 0, -4.6F, -10.625F, 3.875F, 9.2F, 1.125F, .25F);
        box(hat, "band_left", 64, 0, -4.625F, -10.625F, -4, .25F, 1.125F, 8);
        box(hat, "band_right", 64, 0, 4.375F, -10.625F, -4, .25F, 1.125F, 8);
        PartDefinition bow = hat.addOrReplaceChild("bow", CubeListBuilder.create(),
                PartPose.offset(4.25F, -10.25F, -4.5F));
        bow.addOrReplaceChild("bow_left", CubeListBuilder.create().texOffs(64, 0)
                .addBox(-2.6F, -.9F, -.25F, 2.5F, 1.8F, .5F), PartPose.rotation(0, 0, .25F));
        bow.addOrReplaceChild("bow_right", CubeListBuilder.create().texOffs(64, 0)
                .addBox(.1F, -.9F, -.25F, 2.5F, 1.8F, .5F), PartPose.rotation(0, 0, -.25F));
        box(bow, "knot", 64, 0, -.45F, -.5F, -.5F, .9F, 1, .5F);
        box(bow, "ribbon_a", 64, 0, -.6F, .4F, 0, .65F, 2.8F, .25F);
        box(bow, "ribbon_b", 64, 0, .35F, .4F, 0, .65F, 2.25F, .25F);
        // 花饰与金色星纹只做配件，不遮挡皮肤脸部。
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * 2 / 5;
            float x = (float) Math.sin(angle) * .75F;
            float y = (float) Math.cos(angle) * .75F;
            box(bow, "petal_" + i, 32, 24, x - .4F, y - .4F, -.875F, .8F, .8F, .3F);
        }
        box(bow, "flower_center", 0, 24, -.3F, -.3F, -1, .6F, .6F, .25F);
        box(hat, "star_vertical", 0, 24, -3.125F, -12.5F, -4.15F, .25F, 1.25F, .25F);
        box(hat, "star_horizontal", 0, 24, -3.5F, -12, -4.15F, 1, .25F, .25F);

        PartDefinition broom = mesh.getRoot().addOrReplaceChild("broom", CubeListBuilder.create(),
                PartPose.offset(0, 12.3F, 0));
        box(broom, "shaft", 0, 48, -18, -.5F, -.5F, 36, 1, 1);
        box(broom, "handle_cap", 0, 48, -18.5F, -.625F, -.625F, 2.5F, 1.25F, 1.25F);
        PartDefinition bristles = broom.addOrReplaceChild("bristles", CubeListBuilder.create(),
                PartPose.offset(13, 0, 0));
        box(bristles, "brush_root", 0, 64, 0, -.75F, -.75F, 4, 1.5F, 1.5F);
        box(bristles, "brush_middle", 0, 64, 2.5F, -1.375F, -1.375F, 6, 2.75F, 2.75F);
        box(bristles, "brush_end", 0, 64, 6, -1.875F, -1.75F, 5.5F, 3.75F, 3.5F);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI * 2 / 12;
            float y = (float) Math.sin(angle);
            float z = (float) Math.cos(angle);
            bristles.addOrReplaceChild("straw_" + i, CubeListBuilder.create().texOffs(0, 80)
                    .addBox(0, -.375F, -.375F, 11.5F + (i % 3) * .5F, .75F, .75F),
                    PartPose.offsetAndRotation(1.5F, y * .9F, z * .9F, 0, -z * .13F, y * .13F));
        }
        box(broom, "binding_a", 64, 0, 13.25F, -1.125F, -1.125F, .65F, 2.25F, 2.25F);
        box(broom, "binding_b", 0, 24, 14.5F, -1.375F, -1.375F, .5F, 2.75F, 2.75F);
        box(broom, "broom_bow", 64, 0, 13, -2, -1.625F, 3, 1, .5F);
        box(broom, "broom_ribbon", 64, 0, 14, -1, -1.625F, .65F, 2.5F, .5F);

        // 原版 Alex 右手末端局部坐标；南瓜中心略高于掌心，呈托举而非悬空姿势。
        PartDefinition hand = mesh.getRoot().addOrReplaceChild("pumpkin_hand", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition pumpkin = hand.addOrReplaceChild("pumpkin", CubeListBuilder.create(),
                PartPose.offset(-.5F, 10, -2.75F));
        box(pumpkin, "pumpkin_body", 0, 96, -3, -2.5F, -2.5F, 6, 5, 5);
        box(pumpkin, "pumpkin_cap", 32, 96, -2.25F, -2.875F, -2.25F, 4.5F, .375F, 4.5F);
        box(pumpkin, "stem", 64, 96, -.375F, -4, -.375F, .75F, 1.25F, .75F);
        pumpkin.addOrReplaceChild("leaf", CubeListBuilder.create().texOffs(64, 96)
                .addBox(0, -.125F, -.375F, 1.5F, .25F, .75F),
                PartPose.offsetAndRotation(.25F, -3.25F, 0, 0, 0, -.25F));
        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private static void box(PartDefinition parent, String name, int u, int v,
                            float x, float y, float z, float w, float h, float d) {
        parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d),
                PartPose.ZERO);
    }

    /** 帽子跟头、南瓜跟手；扫帚不随头转，且每帧恢复初始姿态避免偏移累积。 */
    void setupPose(ModelPart head, ModelPart rightArm, float ageInTicks) {
        root.getAllParts().forEach(ModelPart::resetPose);
        witchHat.copyFrom(head);
        pumpkinHand.copyFrom(rightArm);
        // 抵消托举手臂的前倾，使南瓜立在掌心，不随手臂一起倒伏。
        pumpkin.xRot = -rightArm.xRot;
        broom.zRot = -.045F + Mth.sin(ageInTicks * .05F) * .01F;
        bristles.xRot = Mth.sin(ageInTicks * .06F) * .02F;
    }

    void render(PoseStack poses, VertexConsumer vertices, int light, int overlay) {
        root.render(poses, vertices, light, overlay, -1);
    }
}
