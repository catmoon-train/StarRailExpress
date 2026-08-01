/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

import java.awt.Color;

/**
 * {@link ModEffects#POINTER} 效果的世界内指引渲染：
 * <ul>
 * <li>指针悬停在玩家 / 实体上时绘制金色脉冲包围框；悬停在方块上时绘制米白描边（复用
 * {@link TaskBlockOverlayRenderer#renderBlockOverlay} 的合并 AABB 逻辑）。</li>
 * <li>主手持枪（{@link TMMItemTags#GUNS}）时，从视线起点沿瞄准方向绘制一条红色激光射线
 * （默认 {@value #GUN_RAY_RANGE} 格，命中方块/实体则截断并在终点画十字标记）。</li>
 * </ul>
 * 玩家处于失明 / 黑暗 / 黑屏类状态时（见 {@link #isVisionObscured}），全部指引静默关闭，
 * 保证这些负面状态不被指引信息穿透。
 */
public final class PointerGuidanceRenderer {

    /** 枪械射线默认长度（格）。 */
    private static final double GUN_RAY_RANGE = 20.0D;
    private static final double RAY_END_MARK = 0.18D;
    private static final Color PLAYER_HIGHLIGHT = new Color(0xD4, 0xAF, 0x37);
    private static final Color ENTITY_HIGHLIGHT = new Color(0xFF, 0xE0, 0x82);
    private static final Color BLOCK_HIGHLIGHT = new Color(0xFF, 0xF4, 0xDC);
    private static final Color GUN_RAY_COLOR = new Color(0xE0, 0x6B, 0x65);

    private PointerGuidanceRenderer() {
    }

    public static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null)
            return;
        if (!player.hasEffect(ModEffects.POINTER))
            return;
        if (isVisionObscured(player))
            return;

        renderTargetHighlight(context, client, player);
        renderGunRay(context, client, player);
    }

    /** 失明 / 黑暗 / 黑屏类状态下不提供任何指引。 */
    public static boolean isVisionObscured(LocalPlayer player) {
        return player.hasEffect(MobEffects.BLINDNESS)
                || player.hasEffect(MobEffects.DARKNESS)
                // 摄影师闪光等黑屏（BlindnessEffectMixin 用 UNLUCK / RAID_OMEN 实现全屏黑）
                || player.hasEffect(MobEffects.UNLUCK)
                || player.hasEffect(MobEffects.RAID_OMEN)
                || player.hasEffect(ModEffects.BLACK_MONITOR);
    }

    private static void renderTargetHighlight(WorldRenderContext context, Minecraft client, LocalPlayer player) {
        Entity entity = PointerClientHandle.currentTargetEntity();
        if (entity != null && entity.isAlive() && entity != player) {
            float pulse = 0.55F + 0.35F * Mth.sin((Util.getMillis() % 1200L) / 1200.0F * Mth.TWO_PI);
            Color color = entity instanceof Player ? PLAYER_HIGHLIGHT : ENTITY_HIGHLIGHT;
            drawEntityBox(context, entity, color, pulse);
            return;
        }
        HitResult hit = PointerClientHandle.currentHitResult();
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            if (!client.level.getBlockState(pos).isAir()) {
                TaskBlockOverlayRenderer.renderBlockOverlay(context, pos, BLOCK_HIGHLIGHT, 0.85F, false, 0.0F);
            }
        }
    }

    private static void renderGunRay(WorldRenderContext context, Minecraft client, LocalPlayer player) {
        if (!player.getMainHandItem().is(TMMItemTags.GUNS))
            return;

        float partialTick = client.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 view = player.getViewVector(partialTick).normalize();
        Vec3 rayEnd = eye.add(view.scale(GUN_RAY_RANGE));

        BlockHitResult blockHit = client.level.clip(new ClipContext(
                eye, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            rayEnd = blockHit.getLocation();
        }
        // 实体命中优先（指针目标实体在射线长度内时，射线止于实体）
        Entity entity = PointerClientHandle.currentTargetEntity();
        if (entity != null && entity != player) {
            Vec3 entityCenter = entity.getBoundingBox().getCenter();
            double along = entityCenter.subtract(eye).dot(view);
            if (along > 0 && along < eye.distanceTo(rayEnd)) {
                rayEnd = eye.add(view.scale(along));
            }
        }

        // 射线从视线略下方发出，模拟枪口位置
        Vec3 side = view.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 rayStart = eye.add(view.scale(0.6D)).add(side.scale(0.12D)).add(0, -0.18D, 0);

        Vec3 cameraPos = context.camera().getPosition();
        VertexConsumer vertexConsumer = context.consumers()
                .getBuffer(TaskBlockOverlayRenderer.ALWAYS_VISIBLE_THICK_LINES);
        PoseStack matrices = context.matrixStack();

        float r = GUN_RAY_COLOR.getRed() / 255f;
        float g = GUN_RAY_COLOR.getGreen() / 255f;
        float b = GUN_RAY_COLOR.getBlue() / 255f;

        matrices.pushPose();
        matrices.translate(rayStart.x - cameraPos.x, rayStart.y - cameraPos.y, rayStart.z - cameraPos.z);
        PoseStack.Pose pose = matrices.last();
        Vec3 delta = rayEnd.subtract(rayStart);
        line(pose, vertexConsumer, Vec3.ZERO, delta, r, g, b, 0.8F);

        // 终点十字标记
        Vec3 up = Math.abs(view.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 cross1 = view.cross(up).normalize().scale(RAY_END_MARK);
        Vec3 cross2 = view.cross(cross1).normalize().scale(RAY_END_MARK);
        line(pose, vertexConsumer, delta.subtract(cross1), delta.add(cross1), r, g, b, 1.0F);
        line(pose, vertexConsumer, delta.subtract(cross2), delta.add(cross2), r, g, b, 1.0F);
        matrices.popPose();
    }

    private static void drawEntityBox(WorldRenderContext context, Entity entity, Color color, float alpha) {
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 pos = entity.getPosition(partialTick);
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(Vec3.ZERO).inflate(0.08D);
        Vec3 cameraPos = context.camera().getPosition();

        VertexConsumer vertexConsumer = context.consumers()
                .getBuffer(TaskBlockOverlayRenderer.ALWAYS_VISIBLE_THICK_LINES);
        PoseStack matrices = context.matrixStack();
        matrices.pushPose();
        matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
        LevelRenderer.renderLineBox(matrices, vertexConsumer, box,
                color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha);
        matrices.popPose();
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vertexConsumer,
            Vec3 from, Vec3 to, float r, float g, float b, float alpha) {
        Vec3 normal = to.subtract(from).normalize();
        vertexConsumer.addVertex(pose, (float) from.x, (float) from.y, (float) from.z)
                .setColor(r, g, b, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        vertexConsumer.addVertex(pose, (float) to.x, (float) to.y, (float) to.z)
                .setColor(r, g, b, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

}
