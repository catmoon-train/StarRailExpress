package org.agmas.noellesroles.game.halloween.village.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.game.halloween.village.VillageNpc;

/** 固定兑换 NPC 和临时发糖 NPC 统一使用万圣残月形象，交互身份由服务端保留。 */
@Environment(EnvType.CLIENT)
public final class VillageNpcRenderer extends MobRenderer<VillageNpc, HalloweenWitchModel> {
    static final ResourceLocation WITCH_SKIN = ResourceLocation.fromNamespaceAndPath(
            "noellesroles", "textures/entity/halloween_canyue_skin.png");

    public VillageNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new HalloweenWitchModel(), .35F);
        addLayer(new WitchAccessoriesLayer(this));
    }

    @Override
    public void render(VillageNpc npc, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int light) {
        poses.pushPose();
        try {
            float age = npc.tickCount + partialTick + (npc.getId() & 31) * 3.7F;
            // 人物与配件一起悬浮，不改变服务端位置和交互箱。
            poses.translate(0, .06F + Mth.sin(age * .065F) * .03F, 0);
            super.render(npc, yaw, partialTick, poses, buffers, light);
        } finally {
            poses.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(VillageNpc entity) {
        return WITCH_SKIN;
    }

    private static final class WitchAccessoriesLayer extends RenderLayer<VillageNpc, HalloweenWitchModel> {
        private final HalloweenWitchAccessories accessories = new HalloweenWitchAccessories();

        WitchAccessoriesLayer(RenderLayerParent<VillageNpc, HalloweenWitchModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poses, MultiBufferSource buffers, int light, VillageNpc npc,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (npc.isInvisible()) return;
            accessories.setupPose(getParentModel().head, getParentModel().rightArm, ageInTicks);
            accessories.render(poses, buffers.getBuffer(RenderType.entityCutoutNoCull(HalloweenWitchAccessories.TEXTURE)),
                    light, LivingEntityRenderer.getOverlayCoords(npc, 0));
        }
    }
}
