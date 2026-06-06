package org.agmas.noellesroles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.cca.C4BackComponent;
import org.agmas.noellesroles.game.c4.C4PlacementPreset;
import org.agmas.noellesroles.init.ModItems;

import com.mojang.blaze3d.vertex.PoseStack;

public class C4BackFeatureRenderer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final ItemStack c4Stack;

    public C4BackFeatureRenderer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> context) {
        super(context);
        this.c4Stack = new ItemStack(ModItems.C4);
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, int light,
            AbstractClientPlayer entity, float limbAngle, float limbDistance,
            float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (entity.isInvisible()) return;
        if (!C4BackComponent.hasC4(entity)) return;

        matrices.pushPose();
        this.getParentModel().body.translateAndRotate(matrices);
        C4PlacementPreset preset = C4PlacementPreset.DEFAULT;
        C4ModelTransforms.applyPlacement(matrices, preset);

        Minecraft mc = Minecraft.getInstance();
        ItemRenderer ir = mc.getItemRenderer();
        ir.renderStatic(this.c4Stack, ItemDisplayContext.FIXED, light,
            OverlayTexture.NO_OVERLAY, matrices, vertexConsumers, entity.level(), 0);
        matrices.popPose();
    }
}
