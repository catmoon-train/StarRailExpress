package org.agmas.noellesroles.client.renderer;

import org.agmas.noellesroles.client.CommandBlockProgressClient;
import org.agmas.noellesroles.content.entity.CommandBlockMarkerEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 天上的命令方块：全亮方块模型，本地旋转，全程末影龙死亡光线，不走方块展示。 */
public class CommandBlockMarkerRenderer extends EntityRenderer<CommandBlockMarkerEntity> {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float HALF_SQRT_3 = (float) (Math.sqrt(3.0D) / 2.0D);
    /** 停在死亡光线最密、还没开始发白消散的进度，整场事件都保持这一眼。 */
    private static final float RAY_PROGRESS = 0.72F;
    private static final int RAY_OUTER = 0x00FF00FF;
    private final BlockRenderDispatcher blocks;

    public CommandBlockMarkerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(CommandBlockMarkerEntity entity, float yaw, float partialTick, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        if (!CommandBlockProgressClient.isShowing()) {
            return;
        }
        int stage = entity.getStage();
        float size = 8.0F - stage;
        float spin = (entity.tickCount + partialTick) * 1.72F;
        matrices.pushPose();
        if (!(buffers instanceof OutlineBufferSource)) {
            matrices.pushPose();
            matrices.mulPose(Axis.YP.rotationDegrees(spin * 0.35F));
            renderDragonRays(matrices, buffers);
            matrices.popPose();
        }
        matrices.mulPose(Axis.YP.rotationDegrees(spin));
        matrices.scale(size, size, size);
        matrices.translate(-0.5F, -0.5F, -0.5F);
        this.blocks.renderSingleBlock(stageBlock(stage), matrices, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        matrices.popPose();
        super.render(entity, yaw, partialTick, matrices, buffers, light);
    }

    /** 1.21.1 {@code EnderDragonRenderer#renderRays}：颜色层加深度层，外缘品红、中心白。 */
    private static void renderDragonRays(PoseStack matrices, MultiBufferSource buffers) {
        renderRays(matrices, buffers.getBuffer(RenderType.dragonRays()));
        renderRays(matrices, buffers.getBuffer(RenderType.dragonRaysDepth()));
    }

    private static void renderRays(PoseStack poseStack, VertexConsumer consumer) {
        poseStack.pushPose();
        float fade = Math.min(RAY_PROGRESS > 0.8F ? (RAY_PROGRESS - 0.8F) / 0.2F : 0.0F, 1.0F);
        int inner = FastColor.ARGB32.colorFromFloat(1.0F - fade, 1.0F, 1.0F, 1.0F);
        RandomSource random = RandomSource.create(432L);
        Vector3f origin = new Vector3f();
        Vector3f left = new Vector3f();
        Vector3f right = new Vector3f();
        Vector3f tip = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        int count = Mth.floor((RAY_PROGRESS + RAY_PROGRESS * RAY_PROGRESS) / 2.0F * 60.0F);
        for (int i = 0; i < count; i++) {
            rotation.rotationXYZ(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI,
                    random.nextFloat() * Mth.TWO_PI);
            rotation.rotateXYZ(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI,
                    random.nextFloat() * Mth.TWO_PI + RAY_PROGRESS * Mth.HALF_PI);
            poseStack.mulPose(rotation);
            float length = random.nextFloat() * 20.0F + 5.0F + fade * 10.0F;
            float width = random.nextFloat() * 2.0F + 1.0F + fade * 2.0F;
            left.set(-HALF_SQRT_3 * width, length, -0.5F * width);
            right.set(HALF_SQRT_3 * width, length, -0.5F * width);
            tip.set(0.0F, length, width);
            PoseStack.Pose pose = poseStack.last();
            consumer.addVertex(pose, origin).setColor(inner);
            consumer.addVertex(pose, left).setColor(RAY_OUTER);
            consumer.addVertex(pose, right).setColor(RAY_OUTER);
            consumer.addVertex(pose, origin).setColor(inner);
            consumer.addVertex(pose, right).setColor(RAY_OUTER);
            consumer.addVertex(pose, tip).setColor(RAY_OUTER);
            consumer.addVertex(pose, origin).setColor(inner);
            consumer.addVertex(pose, tip).setColor(RAY_OUTER);
            consumer.addVertex(pose, left).setColor(RAY_OUTER);
        }
        poseStack.popPose();
    }

    private static BlockState stageBlock(int stage) {
        return switch (stage) {
            case 1 -> Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
            case 2 -> Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
            case 3 -> Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
            default -> Blocks.COMMAND_BLOCK.defaultBlockState();
        };
    }

    @Override
    public ResourceLocation getTextureLocation(CommandBlockMarkerEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
