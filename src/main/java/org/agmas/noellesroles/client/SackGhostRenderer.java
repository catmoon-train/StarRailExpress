package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 玩家黑雾替身入口；独立保留诡术所用的人形 draw，不泄露装备和名牌。 */
public final class SackGhostRenderer {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/entity/sack_ghost.png");
    private static SackGhostModel model;

    private SackGhostRenderer() {}

    /** 返回 true 表示连同名牌、护甲、披风及手持物一起取消原玩家渲染。 */
    public static boolean replace(AbstractClientPlayer player, float partial, PoseStack poses,
                                  MultiBufferSource buffers, int light) {
        int flags = SackGhostClient.formFlags(player);
        if (flags == 0) return false;
        boolean hidden = (flags & 4) != 0;
        boolean reveal = SackGhostClient.shouldReveal(player);
        if (hidden && !reveal) return true;
        if ((flags & 3) == 0 && !reveal) return true;
        Minecraft mc = Minecraft.getInstance();
        double distance = mc.getCameraEntity() == null ? 0 : mc.getCameraEntity().distanceToSqr(player);
        if (distance > 64 * 64) return true;
        partial = SackGhostClient.visualPartial(partial);
        int detail = distance > 32 * 32 ? 0 : SackGhostEffects.detail();
        // 连续、上宽下收的黑雾剪影，不再用公转烟球或硬质人形堆叠主体。
        // 隐身只向合法法器持有者提交原生红色轮廓，不提交可见雾体。
        SackGhostMist.body(player, poses, buffers, partial, SackGhostClient.visualTime(partial), detail, hidden);
        return true;
    }

    public static void drawFirstPerson(PoseStack poses, MultiBufferSource buffers, float partial) {
        float time = SackGhostClient.visualTime(partial);
        // 常态鬼影只留屏幕下缘的轻雾，实体鬼手仅在真正施术时出现。
        for (int side : new int[]{-1, 1}) {
            for (int i = 0; i < 3; i++) {
                SackGhostMist.viewPuff(poses, buffers, side * (0.72F + i * 0.08F),
                        -0.62F + i * 0.05F + Mth.sin(time * 0.025F + i) * 0.012F,
                        -0.96F - i * 0.08F, 0.09F + i * 0.015F, 0.23F - i * 0.02F,
                        side * 0.22F, 0.14F * (1 - i * 0.1F));
            }
        }
    }

    /** 坐标原点为脚底；所有模型都走原版实体渲染层和深度测试。 */
    public static void draw(PoseStack poses, MultiBufferSource buffers, int light, float time,
                            float yaw, float pitch, float scale, float alpha, float scream, float reach) {
        if (alpha <= 0.005F) return;
        if (model == null) model = new SackGhostModel();
        model.animate(time, 0, pitch, scream, reach);
        poses.pushPose();
        poses.translate(0, 1.75 * scale + Mth.sin(time * 0.065F) * 0.035, 0);
        poses.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        poses.scale(-scale, -scale, scale);
        int color = (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | 0x8C8394;
        model.renderToBuffer(poses, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light,
                OverlayTexture.NO_OVERLAY, color);
        // 带透明度的拖影不用加法眼层，避免黑暗中堆叠成过亮光斑。
        model.renderEyes(poses, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)),
                ((int) ((color >>> 24) * 0.65F) << 24) | 0xB02B39);
        poses.popPose();
    }
}
