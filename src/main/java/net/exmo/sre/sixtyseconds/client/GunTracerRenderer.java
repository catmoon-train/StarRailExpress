package net.exmo.sre.sixtyseconds.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.exmo.sre.sixtyseconds.network.GunTracerS2CPacket;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * 枪械射击轨迹渲染：收到 {@link GunTracerS2CPacket} 时按射手当前枪口位置定格一条轨迹线，
 * {@link #LIFE_TICKS} 内渐隐消失。挂在 WorldRenderEvents.AFTER_TRANSLUCENT。
 * 轨迹线走<b>常规深度测试</b>（{@link #THICK_LINES}），被墙体遮挡即不可见（不透视）。
 */
public final class GunTracerRenderer {
    private static final int LIFE_TICKS = 8;
    private static final List<Tracer> TRACERS = new ArrayList<>();

    /**
     * 弹道线渲染层：与 {@code TaskBlockOverlayRenderer.ALWAYS_VISIBLE_THICK_LINES} 不同——
     * 用 {@link RenderStateShard#LEQUAL_DEPTH_TEST} 参与深度测试、且输出到主渲染目标（默认 MAIN_TARGET），
     * 故被墙体/地形遮挡的部分会被剔除，实现「穿墙不可见」。仅写颜色不写深度。
     */
    private static final RenderType THICK_LINES = RenderType.create("sixty_seconds_gun_tracer",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(3.0))) // 线宽3.0
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private record Tracer(Vec3 from, Vec3 to, long expireGameTime) {
    }

    private GunTracerRenderer() {
    }

    /** 客户端收包：以射手实体当前位置推算枪口，定格轨迹起点。 */
    public static void onPacket(GunTracerS2CPacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        Entity shooter = client.level.getEntity(packet.shooterId());
        if (shooter == null) {
            return;
        }
        Vec3 eye = shooter.getEyePosition();
        Vec3 to = new Vec3(packet.toX(), packet.toY(), packet.toZ());
        Vec3 view = to.subtract(eye);
        if (view.lengthSqr() < 0.01) {
            return;
        }
        view = view.normalize();
        // 枪口位置：视线前 0.6、右 0.12、下 0.18（与 PointerGuidanceRenderer 的枪口模拟一致）
        Vec3 side = view.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 from = eye.add(view.scale(0.6D)).add(side.scale(0.12D)).add(0, -0.18D, 0);
        TRACERS.add(new Tracer(from, to, client.level.getGameTime() + LIFE_TICKS));
    }

    public static void render(WorldRenderContext context) {
        if (TRACERS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            TRACERS.clear();
            return;
        }
        long now = client.level.getGameTime();
        Vec3 cameraPos = context.camera().getPosition();
        VertexConsumer vertexConsumer = context.consumers().getBuffer(THICK_LINES);
        PoseStack matrices = context.matrixStack();
        for (Iterator<Tracer> it = TRACERS.iterator(); it.hasNext();) {
            Tracer tracer = it.next();
            long left = tracer.expireGameTime() - now;
            if (left <= 0) {
                it.remove();
                continue;
            }
            float alpha = 0.15F + 0.55F * left / LIFE_TICKS;
            matrices.pushPose();
            matrices.translate(tracer.from().x - cameraPos.x, tracer.from().y - cameraPos.y,
                    tracer.from().z - cameraPos.z);
            PoseStack.Pose pose = matrices.last();
            Vec3 delta = tracer.to().subtract(tracer.from());
            line(pose, vertexConsumer, delta, 1.0F, 0.85F, 0.4F, alpha);
            matrices.popPose();
        }
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vertexConsumer, Vec3 delta,
            float r, float g, float b, float alpha) {
        Vec3 normal = delta.normalize();
        vertexConsumer.addVertex(pose, 0F, 0F, 0F)
                .setColor(r, g, b, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        vertexConsumer.addVertex(pose, (float) delta.x, (float) delta.y, (float) delta.z)
                .setColor(r, g, b, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
