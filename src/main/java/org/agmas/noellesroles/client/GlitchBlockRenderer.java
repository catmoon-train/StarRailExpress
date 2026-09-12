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

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.joml.Matrix4f;

import java.awt.Color;

/**
 * 故障：附近方块叠一层随机色，绕过 Sodium 网格缓存。
 */
@Environment(EnvType.CLIENT)
public final class GlitchBlockRenderer {
    private static final int RADIUS = 8;
    private static final float INSET = 0.002f;
    private static final ByteBufferBuilder BYTES = new ByteBufferBuilder(262144);

    private GlitchBlockRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(GlitchBlockRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || context.matrixStack() == null
                || !client.player.hasEffect(ModEffects.GLITCH)) {
            return;
        }
        Level level = client.level;
        BlockPos origin = client.player.blockPosition();
        long bucket = level.getGameTime() / 8L;
        Vec3 camera = context.camera().getPosition();
        PoseStack pose = context.matrixStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();

        BufferBuilder builder = new BufferBuilder(BYTES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int faces = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.getCollisionShape(level, cursor).isEmpty()) {
                        continue;
                    }
                    int color = colorFor(cursor, bucket);
                    faces += emitVisibleFaces(builder, matrix, level, cursor, color);
                }
            }
        }
        MeshData mesh = faces > 0 ? builder.build() : null;
        if (mesh != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferUploader.drawWithShader(mesh);
            mesh.close();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
        BYTES.clear();
        pose.popPose();
    }

    private static int emitVisibleFaces(BufferBuilder builder, Matrix4f matrix, Level level,
            BlockPos pos, int color) {
        float x1 = pos.getX() - INSET;
        float y1 = pos.getY() - INSET;
        float z1 = pos.getZ() - INSET;
        float x2 = pos.getX() + 1.0f + INSET;
        float y2 = pos.getY() + 1.0f + INSET;
        float z2 = pos.getZ() + 1.0f + INSET;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = 0.72f;
        int faces = 0;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighbor);
            if (!neighborState.getCollisionShape(level, neighbor).isEmpty()) {
                continue;
            }
            switch (direction) {
                case DOWN -> quad(builder, matrix, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
                case UP -> quad(builder, matrix, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, r, g, b, a);
                case NORTH -> quad(builder, matrix, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, r, g, b, a);
                case SOUTH -> quad(builder, matrix, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
                case WEST -> quad(builder, matrix, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
                case EAST -> quad(builder, matrix, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, r, g, b, a);
            }
            faces++;
        }
        return faces;
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float x3, float y3, float z3, float x4, float y4, float z4,
            float r, float g, float b, float a) {
        builder.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        builder.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
        builder.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a);
    }

    private static int colorFor(BlockPos pos, long bucket) {
        long hash = pos.asLong() ^ (bucket * 0x9E3779B97F4A7C15L);
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        float hue = (hash & 0xFFFFL) / 65535.0f;
        return Color.HSBtoRGB(Mth.positiveModulo(hue, 1.0f), 0.88f, 1.0f) & 0xFFFFFF;
    }
}
