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

/** 玩家渲染入口的替代模型；不调用 PlayerRenderer，避免残影递归和装备泄露。 */
public final class SackGhostRenderer {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("noellesroles", "textures/entity/sack_ghost.png");
    private static SackGhostModel model;
    private static SackGhostHandModel hands;

    private SackGhostRenderer() {}

    /** 返回 true 表示连同名牌、护甲、披风及手持物一起取消原玩家渲染。 */
    public static boolean replace(AbstractClientPlayer player, float partial, PoseStack poses,
                                  MultiBufferSource buffers, int light) {
        int flags = SackGhostClient.formFlags(player);
        if (flags == 0) return false;
        boolean hidden = (flags & 4) != 0;
        if (hidden && !SackGhostClient.shouldReveal(player)) return true;
        if ((flags & 3) == 0 && !SackGhostClient.shouldReveal(player)) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != null && mc.getCameraEntity().distanceToSqr(player) > 64 * 64) return true;
        partial = SackGhostClient.visualPartial(partial);
        float time = SackGhostClient.visualTime(partial);
        float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        draw(poses, buffers, light, time, yaw, player.getXRot() * 0.35F, 1, 1, 0, 0.15F);
        // 扭曲拖影是可裁剪的装饰；主体不受“最少粒子”影响。
        int trails = SackGhostEffects.detail();
        if (!hidden && trails > 0) {
            for (int i = trails; i > 0; i--) {
                poses.pushPose();
                double dx = Mth.lerp(partial, player.xo, player.getX()) - player.xOld;
                double dz = Mth.lerp(partial, player.zo, player.getZ()) - player.zOld;
                double length = Math.sqrt(dx * dx + dz * dz);
                double scale = length > 0.001 ? Math.min(0.45 / length, 3) * i : 0;
                poses.translate(-dx * scale + Mth.sin(time * 0.14F + i) * 0.05F, -i * 0.04F, -dz * scale);
                draw(poses, buffers, light, time - i * 3, yaw + i * 5, 0, 1 + i * 0.025F,
                        0.13F / i, 0, 0.2F);
                poses.popPose();
            }
        }
        return true;
    }

    public static void drawFirstPerson(PoseStack poses, MultiBufferSource buffers, float partial) {
        if (hands == null) hands = new SackGhostHandModel();
        float time = SackGhostClient.visualTime(partial);
        for (int side : new int[]{-1, 1}) {
            poses.pushPose();
            poses.translate(side * 0.48, -0.42 + Mth.sin(time * 0.08F) * 0.025, -0.8);
            poses.mulPose(Axis.XP.rotationDegrees(-35));
            poses.mulPose(Axis.ZP.rotationDegrees(side * 18));
            poses.scale(0.65F, -0.65F, 0.65F);
            hands.render(poses, buffers, 0.3F, 0.9F);
            poses.popPose();
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
        int color = (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | 0xFFFFFF;
        model.renderToBuffer(poses, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light,
                OverlayTexture.NO_OVERLAY, color);
        // 带透明度的拖影不用加法眼层，避免黑暗中堆叠成过亮光斑。
        model.renderEyes(poses, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)),
                (color & 0xFF000000) | 0xFF142B);
        poses.popPose();
    }
}
