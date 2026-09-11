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

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.content.item.VerticalLoopingMirrorToolItem;
import io.wifi.starrailexpress.index.DevItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block_entity.scene.LoopingMirrorBlockEntity;
import org.agmas.noellesroles.scene.VerticalLoopingMirrorLoop;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 扫描附近的上下循环配置，叠加上方场景，并画出勾选框。
 */
public final class VerticalLoopingMirrorClient {
    private static final int SCAN_INTERVAL = 20;
    private static final double ACTIVATION_DISTANCE = 56.0D;
    private static final DustParticleOptions BOX_DUST = new DustParticleOptions(new Vector3f(0.45F, 0.75F, 1.0F), 0.8F);

    private static final Map<BlockPos, VerticalLoopingMirrorClientScene> ACTIVE = new HashMap<>();
    private static ClientLevel boundLevel;
    private static int scanCountdown;

    private VerticalLoopingMirrorClient() {
    }

    public static void register() {
        ClientTickEvents.END_WORLD_TICK.register(VerticalLoopingMirrorClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> forget());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(VerticalLoopingMirrorClient::render);
    }

    /**
     * 镜头掉进勾选体积下方时改用短距雾，避免再往下叠场景。
     *
     * @return 已接管本次雾设置
     */
    public static boolean applyBottomFog(Camera camera) {
        if (ACTIVE.isEmpty()) {
            return false;
        }
        Vec3 pos = camera.getPosition();
        for (VerticalLoopingMirrorClientScene scene : ACTIVE.values()) {
            VerticalLoopingMirrorLoop loop = scene.loop();
            AABB fog = loop.fogBox();
            if (!fog.contains(pos)) {
                continue;
            }
            double depth = loop.minY() - pos.y;
            float t = Mth.clamp((float) (depth / (double) VerticalLoopingMirrorLoop.FOG_DEPTH), 0.0F, 1.0F);
            float end = Mth.lerp(t, 8.0F, 2.0F);
            RenderSystem.setShaderFogStart(0.0F);
            RenderSystem.setShaderFogEnd(end);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            return true;
        }
        return false;
    }

    private static void tick(ClientLevel level) {
        if (boundLevel != level) {
            forget();
            boundLevel = level;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (--scanCountdown <= 0) {
            scanCountdown = SCAN_INTERVAL;
            rescan(level, player);
        }
        for (VerticalLoopingMirrorClientScene scene : ACTIVE.values()) {
            scene.tick();
        }
        if (player.isCreative()) {
            spawnCreativeParticles(level, player);
        }
    }

    private static void forget() {
        for (VerticalLoopingMirrorClientScene scene : ACTIVE.values()) {
            scene.close();
        }
        ACTIVE.clear();
        boundLevel = null;
    }

    private static void rescan(ClientLevel level, LocalPlayer player) {
        Map<BlockPos, VerticalLoopingMirrorLoop> found = new HashMap<>();
        BlockPos origin = player.blockPosition();
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        for (int cx = originChunkX - 5; cx <= originChunkX + 5; cx++) {
            for (int cz = originChunkZ - 5; cz <= originChunkZ + 5; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (entry.getValue() instanceof LoopingMirrorBlockEntity be && be.getVerticalLoop() != null) {
                        VerticalLoopingMirrorLoop loop = be.getVerticalLoop();
                        AABB box = loop.cellBox().expandTowards(0.0D, loop.copiesUp() * loop.periodY(), 0.0D);
                        if (player.position().distanceToSqr(box.getCenter()) <= ACTIVATION_DISTANCE * ACTIVATION_DISTANCE
                                || player.blockPosition().closerThan(loop.controller(), ACTIVATION_DISTANCE)) {
                            found.put(loop.controller(), loop);
                        }
                    }
                }
            }
        }

        Iterator<Map.Entry<BlockPos, VerticalLoopingMirrorClientScene>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, VerticalLoopingMirrorClientScene> entry = it.next();
            VerticalLoopingMirrorLoop next = found.get(entry.getKey());
            if (next == null || !next.equals(entry.getValue().loop())) {
                entry.getValue().close();
                it.remove();
            }
        }
        for (Map.Entry<BlockPos, VerticalLoopingMirrorLoop> entry : found.entrySet()) {
            if (!ACTIVE.containsKey(entry.getKey())) {
                ACTIVE.put(entry.getKey(), new VerticalLoopingMirrorClientScene(level, entry.getValue()));
            }
        }
    }

    private static void render(WorldRenderContext context) {
        for (VerticalLoopingMirrorClientScene scene : ACTIVE.values()) {
            scene.render(context);
        }
        renderToolOverlay(context);
    }

    private static void renderToolOverlay(WorldRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = context.world();
        if (player == null || level == null || context.consumers() == null || context.matrixStack() == null) {
            return;
        }
        boolean holdingTool = player.getMainHandItem().is(DevItems.VERTICAL_LOOPING_MIRROR_TOOL)
                || player.getOffhandItem().is(DevItems.VERTICAL_LOOPING_MIRROR_TOOL);
        if (!holdingTool && !player.isCreative()) {
            return;
        }

        PoseStack poseStack = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderType.lines());

        if (holdingTool || player.isCreative()) {
            for (VerticalLoopingMirrorClientScene scene : ACTIVE.values()) {
                VerticalLoopingMirrorLoop loop = scene.loop();
                LevelRenderer.renderLineBox(poseStack, lines, loop.cellBox(), 0.35F, 0.85F, 1.0F, 1.0F);
                LevelRenderer.renderLineBox(poseStack, lines, loop.fogBox(), 0.55F, 0.60F, 0.70F, 0.55F);
                LevelRenderer.renderLineBox(poseStack, lines, new AABB(loop.controller()), 1.0F, 1.0F, 0.25F, 1.0F);
                int period = loop.periodY();
                for (int layer = 1; layer <= loop.copiesUp(); layer++) {
                    float fade = 0.75F - (layer - 1) * 0.25F;
                    LevelRenderer.renderLineBox(poseStack, lines, loop.cellBox().move(0.0D, layer * period, 0.0D),
                            0.45F, 0.95F, 0.70F, fade);
                }
            }
        }
        if (holdingTool) {
            renderSelection(poseStack, lines, player);
        }
        poseStack.popPose();
    }

    private static void renderSelection(PoseStack poseStack, VertexConsumer lines, LocalPlayer player) {
        ItemStack stack = player.getMainHandItem().is(DevItems.VERTICAL_LOOPING_MIRROR_TOOL)
                ? player.getMainHandItem()
                : player.getOffhandItem();
        if (!stack.is(DevItems.VERTICAL_LOOPING_MIRROR_TOOL)) {
            return;
        }
        BlockPos host = VerticalLoopingMirrorToolItem.readHost(stack);
        if (host != null) {
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(host), 1.0F, 1.0F, 0.2F, 1.0F);
        }
        BlockPos cornerA = VerticalLoopingMirrorToolItem.readCornerA(stack);
        if (cornerA != null) {
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(cornerA), 1.0F, 0.55F, 0.2F, 1.0F);
            BlockPos hover = hoveredBlock(player);
            if (hover != null) {
                LevelRenderer.renderLineBox(poseStack, lines, cornerBox(cornerA, hover), 0.3F, 0.85F, 1.0F, 0.75F);
            }
        }
    }

    private static BlockPos hoveredBlock(LocalPlayer player) {
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        return null;
    }

    private static AABB cornerBox(BlockPos a, BlockPos b) {
        int minX = Math.min(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxX = Math.max(a.getX(), b.getX());
        int maxY = Math.max(a.getY(), b.getY());
        int maxZ = Math.max(a.getZ(), b.getZ());
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    private static void spawnCreativeParticles(ClientLevel level, LocalPlayer player) {
        RandomSource random = level.random;
        for (VerticalLoopingMirrorClientScene scene : ACTIVE.values()) {
            AABB box = scene.loop().cellBox();
            if (player.position().distanceToSqr(box.getCenter()) > 64.0D * 64.0D) {
                continue;
            }
            for (int i = 0; i < 2; i++) {
                double x = box.minX + random.nextDouble() * (box.maxX - box.minX);
                double y = box.minY + random.nextDouble() * (box.maxY - box.minY);
                double z = box.minZ + random.nextDouble() * (box.maxZ - box.minZ);
                level.addParticle(BOX_DUST, x, y, z, 0.0D, 0.0D, 0.0D);
                level.addParticle(ParticleTypes.END_ROD, x, box.maxY + 0.15D, z, 0.0D, 0.02D, 0.0D);
            }
            AABB fog = scene.loop().fogBox();
            double fx = fog.minX + random.nextDouble() * (fog.maxX - fog.minX);
            double fy = fog.minY + random.nextDouble() * (fog.maxY - fog.minY);
            double fz = fog.minZ + random.nextDouble() * (fog.maxZ - fog.minZ);
            level.addParticle(ParticleTypes.CLOUD, fx, fy, fz, 0.0D, 0.01D, 0.0D);
        }
    }
}
