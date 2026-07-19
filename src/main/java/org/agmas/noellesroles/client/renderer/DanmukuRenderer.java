package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.DanmukuEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

/**
 * 飞斧渲染器 —— 直接绘制飞斧「物品模型」，沿飞行方向端对端翻滚（像真正被扔出的斧头），
 * 钉墙后停止翻滚保持嵌入姿态。用 {@link ItemRenderer#renderStatic} 渲染物品，
 * 因此看起来是斧头而非箭矢渲染器画出的「飞剑」。
 */
public class DanmukuRenderer extends EntityRenderer<DanmukuEntity> {

    /** 翻滚速度（度/tick）。约 2 圈/秒。 */
    private static final float SPIN_DEG_PER_TICK = 40.0f;

    private final ItemRenderer itemRenderer;

    public DanmukuRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(DanmukuEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        // 时停守卫：时停期间不可移动的玩家看不到飞行中的飞斧（沿用飞刀渲染器逻辑）。
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(ModEffects.TIME_STOP)
                && !TimeStopEffect.canMovePlayers.contains(player.getUUID())) {
            return;
        }

        poseStack.pushPose();

        // 朝向飞行方向（与箭矢渲染器一致：绕 Y、X 旋转后局部 +X 指向飞行方向）。
        float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch));

        // 端对端翻滚：绕局部 Z 轴（垂直于飞行方向的水平轴）旋转；钉墙后冻结角度。
        float spinTick = entity.isStuck() && entity.getStuckTick() >= 0
                ? entity.getStuckTick()
                : entity.tickCount + partialTick;
        poseStack.mulPose(Axis.ZP.rotationDegrees(spinTick * SPIN_DEG_PER_TICK));

        poseStack.scale(1.4f, 1.4f, 1.4f);

        this.itemRenderer.renderStatic(
                ModItems.DANMUKU.getDefaultInstance(), ItemDisplayContext.FIXED,
                packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, bufferSource,
                entity.level(), entity.getId());

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(DanmukuEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
