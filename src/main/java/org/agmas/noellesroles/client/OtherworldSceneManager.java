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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 里世界的局部客户端方块换肤、符纹与薄雾；永不修改服务端世界。
 * 方块只在 tick 内按预算处理；固定 12 个符纹槽位经距离与视锥裁剪后提交顶点。
 */
public class OtherworldSceneManager {
    public static final OtherworldSceneManager INSTANCE = new OtherworldSceneManager();

    static {
        SackGhostBlockChanges.registerLifecycle();
    }

    private static final int MAX_GLYPHS = 12;
    private static final int RELOCATE_INTERVAL = 8;
    private static final double MAX_DISTANCE_SQUARED = 24.0D * 24.0D;
    private static final RenderType FOG = RenderType.entityTranslucent(
            ResourceLocation.withDefaultNamespace("textures/misc/shadow.png"));
    /** 每个符纹最多 10 条细线和 1 张低透明度雾片。 */
    private static final float[][] STROKES = {
            {-0.04F, -0.60F, 0.06F, 0.55F},
            {0.06F, 0.55F, -0.20F, 0.30F},
            {-0.20F, 0.30F, 0.02F, 0.16F},
            {0.02F, 0.16F, 0.24F, 0.34F},
            {0.24F, 0.34F, 0.06F, 0.55F},
            {0.01F, 0.07F, -0.22F, -0.12F},
            {-0.22F, -0.12F, -0.02F, -0.30F},
            {-0.02F, -0.30F, 0.22F, -0.10F},
            {0.22F, -0.10F, 0.01F, 0.07F},
            {-0.05F, -0.49F, 0.20F, -0.38F}
    };

    private final Glyph[] glyphs = new Glyph[MAX_GLYPHS];
    private ClientLevel level;
    private boolean active;
    private int ticks;
    private int nextGlyph;
    private int lastPlayerTick = Integer.MIN_VALUE;

    public OtherworldSceneManager() {
        for (int i = 0; i < glyphs.length; i++) glyphs[i] = new Glyph();
    }

    public void activate() {
        Minecraft mc = Minecraft.getInstance();
        if (active && level == mc.level) return;
        reset();
        if (mc.level == null || mc.player == null) return;
        level = mc.level;
        active = true;
        SackGhostBlockChanges.INSTANCE.activate(level);
        for (int i = 0; i < glyphs.length; i++) relocate(mc, i);
    }

    /** 普通退出分批还原方块；独立清理 tick 不受暂停和时停影响。 */
    public void deactivate() {
        SackGhostBlockChanges.INSTANCE.deactivate();
        clearGlyphs();
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (level != mc.level || mc.level == null || mc.player == null
                || !OtherworldShader.isAnyOtherworldActive()) {
            deactivate();
            return;
        }
        if (!active || !SackGhostClient.clockRunning() || lastPlayerTick == mc.player.tickCount) return;
        // 兼容旧初始化代码仍调用 tick 的情况，同一玩家 tick 只推进一次。
        lastPlayerTick = mc.player.tickCount;
        ticks++;
        SackGhostBlockChanges.INSTANCE.tick(mc);
        for (Glyph glyph : glyphs) glyph.age++;
        if (ticks % RELOCATE_INTERVAL == 0) {
            relocate(mc, nextGlyph);
            nextGlyph = (nextGlyph + 1) % glyphs.length;
        }
    }

    /** 只在重定位时创建一个裁剪盒，渲染帧内不创建位置、集合或包围盒。 */
    private void relocate(Minecraft mc, int index) {
        Glyph glyph = glyphs[index];
        double angle = index * (Math.PI * 2.0D / MAX_GLYPHS) + ticks * 0.006D;
        double radius = 7.0D + (index * 7 % 10);
        glyph.x = mc.player.getX() + Math.cos(angle) * radius;
        glyph.y = mc.player.getY() + 0.65D + (index % 3) * 0.65D;
        glyph.z = mc.player.getZ() + Math.sin(angle) * radius;
        glyph.age = 0;
        glyph.bounds = new AABB(glyph.x - 1.5D, glyph.y - 1.5D, glyph.z - 1.5D,
                glyph.x + 1.5D, glyph.y + 1.5D, glyph.z + 1.5D);
    }

    /** 由 SackGhostRealmFx.register 静态接入 AFTER_ENTITIES；保留原版深度测试。 */
    public void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || level != mc.level || !SackGhostRealmFx.hasVisuals()
                || context.matrixStack() == null || context.consumers() == null) return;
        // 装饰性几何同样尊重粒子设置；最少时完全关闭，警示 HUD 不受影响。
        int limit = switch (mc.options.particles().get()) {
            case ALL -> MAX_GLYPHS;
            case DECREASED -> MAX_GLYPHS / 2;
            case MINIMAL -> 0;
        };
        if (limit == 0) return;
        float partial = SackGhostClient.visualPartial(context.tickCounter().getGameTimeDeltaPartialTick(false));
        float pressure = SackGhostRealmFx.pressure(partial);
        float strength = SackGhostRealmFx.strength(partial);
        Vec3 camera = context.camera().getPosition();
        PoseStack poses = context.matrixStack();
        VertexConsumer mist = null;
        for (int i = 0; i < limit; i++) {
            Glyph glyph = glyphs[i];
            glyph.fade = 0.0F;
            double dx = glyph.x - camera.x;
            double dy = glyph.y - camera.y;
            double dz = glyph.z - camera.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < 16.0D || distanceSquared > MAX_DISTANCE_SQUARED
                    || glyph.bounds == null || context.frustum() == null
                    || !context.frustum().isVisible(glyph.bounds)) continue;
            float lifetime = Mth.clamp((glyph.age + partial) / 16.0F, 0.0F, 1.0F)
                    * Mth.clamp((MAX_GLYPHS * RELOCATE_INTERVAL - glyph.age - partial) / 16.0F, 0.0F, 1.0F);
            float fade = lifetime * strength * Mth.clamp((24.0F - (float) Math.sqrt(distanceSquared)) / 6.0F, 0.0F, 1.0F);
            if (fade <= 0.01F) continue;
            glyph.fade = fade;
            if (mist == null) mist = context.consumers().getBuffer(FOG);
            poses.pushPose();
            poses.translate(dx, dy + Math.sin((ticks + partial) * 0.025D + i) * 0.10D, dz);
            poses.mulPose(context.camera().rotation());
            var pose = poses.last();
            int fogAlpha = (int) (fade * (10.0F + pressure * 7.0F));
            fogVertex(mist, pose, -0.75F, -0.9F, 0.0F, 1.0F, fogAlpha);
            fogVertex(mist, pose, 0.75F, -0.9F, 1.0F, 1.0F, fogAlpha);
            fogVertex(mist, pose, 0.75F, 0.9F, 1.0F, 0.0F, fogAlpha);
            fogVertex(mist, pose, -0.75F, 0.9F, 0.0F, 0.0F, fogAlpha);
            poses.popPose();
        }
        if (mist == null) return;
        // 先完成所有雾片，再请求线段缓冲；兼容不提供固定缓冲的渲染实现。
        VertexConsumer lines = context.consumers().getBuffer(RenderType.lines());
        for (int i = 0; i < limit; i++) {
            Glyph glyph = glyphs[i];
            if (glyph.fade <= 0.01F) continue;
            poses.pushPose();
            poses.translate(glyph.x - camera.x,
                    glyph.y - camera.y + Math.sin((ticks + partial) * 0.025D + i) * 0.10D, glyph.z - camera.z);
            poses.mulPose(context.camera().rotation());
            var pose = poses.last();
            int alpha = (int) (glyph.fade * (48.0F + pressure * 30.0F));
            for (float[] stroke : STROKES) {
                float nx = stroke[2] - stroke[0];
                float ny = stroke[3] - stroke[1];
                float length = Mth.sqrt(nx * nx + ny * ny);
                lines.addVertex(pose, stroke[0], stroke[1], 0.015F).setColor(150, 24, 32, alpha)
                        .setNormal(pose, nx / length, ny / length, 0.0F);
                lines.addVertex(pose, stroke[2], stroke[3], 0.015F).setColor(150, 24, 32, alpha)
                        .setNormal(pose, nx / length, ny / length, 0.0F);
            }
            poses.popPose();
        }
    }

    private static void fogVertex(VertexConsumer out, PoseStack.Pose pose, float x, float y,
            float u, float v, int alpha) {
        out.addVertex(pose, x, y, 0.0F).setColor(48, 8, 15, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0.0F, 0.0F, 1.0F);
    }

    /** 生命周期强制清理：只还原当前世界中仍归本地替换持有的方块。 */
    public void forceRestore() {
        reset();
    }

    public void reset() {
        SackGhostBlockChanges.INSTANCE.forceRestore();
        clearGlyphs();
    }

    private void clearGlyphs() {
        active = false;
        level = null;
        ticks = 0;
        nextGlyph = 0;
        lastPlayerTick = Integer.MIN_VALUE;
        for (Glyph glyph : glyphs) {
            glyph.bounds = null;
            glyph.age = 0;
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isRestoring() {
        return SackGhostBlockChanges.INSTANCE.isRestoring();
    }

    private static final class Glyph {
        private double x;
        private double y;
        private double z;
        private int age;
        private float fade;
        private AABB bounds;
    }
}
