package net.exmo.sre.repair.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.exmo.sre.repair.content.block_entity.HunterCageBlockEntity;
import net.exmo.sre.repair.state.RepairModeState;
import org.joml.Matrix4f;

public class HunterCageBlockEntityRenderer implements BlockEntityRenderer<HunterCageBlockEntity> {
    public HunterCageBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HunterCageBlockEntity entity, float tickDelta, PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, int packedOverlay) {
        if (entity.getTrialEntries().isEmpty()) {
            return;
        }
        poseStack.pushPose();
        // try/finally 保证 popPose 一定执行：ModernUI 的 SDF 文字渲染在 drawInBatch 里可能抛
        // UnsupportedOperationException（fastutil 不可变表），未兜底会漏 pop → 帧末「Pose stack not empty」崩溃。
        try {
            poseStack.translate(0.5D, 2.65D, 0.5D);
            poseStack.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            poseStack.scale(-0.018F, -0.018F, 0.018F);
            Matrix4f matrix = poseStack.last().pose();
            int y = -18;
            for (HunterCageBlockEntity.TrialEntry entry : entity.getTrialEntries()) {
                float pct = Math.min(1.0F, entry.progress / (float) RepairModeState.TRIAL_EXECUTION_TICKS);
                int barWidth = 64;
                fill(bufferSource, matrix, -barWidth / 2, y, barWidth / 2, y + 5, 0xAA180508, packedLight);
                fill(bufferSource, matrix, -barWidth / 2 + 1, y + 1,
                        -barWidth / 2 + 1 + (int) ((barWidth - 2) * pct), y + 4, 0xFFE11D48, packedLight);
                Component text = Component.translatable("hud.noellesroles.repair.trial_timer",
                        Math.max(0, (RepairModeState.TRIAL_EXECUTION_TICKS - entry.progress) / 20));
                Minecraft.getInstance().font.drawInBatch(text, -Minecraft.getInstance().font.width(text) / 2.0F, y - 10,
                        0xFFFFD6D6, false, matrix, bufferSource, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, packedLight);
                y += 18;
            }
        } finally {
            poseStack.popPose();
        }
    }

    private static void fill(MultiBufferSource bufferSource, Matrix4f matrix, int minX, int minY, int maxX, int maxY,
            int color, int light) {
        float a = ((color >>> 24) & 0xFF) / 255.0F;
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float g = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        var consumer = bufferSource.getBuffer(net.minecraft.client.renderer.RenderType.gui());
        consumer.addVertex(matrix, minX, maxY, 0).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, maxX, maxY, 0).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, maxX, minY, 0).setColor(r, g, b, a).setLight(light);
        consumer.addVertex(matrix, minX, minY, 0).setColor(r, g, b, a).setLight(light);
    }
}
