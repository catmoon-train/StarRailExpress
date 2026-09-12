/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    10| * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.wifi.starrailexpress.client.mirror.MirrorReflectionManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block.scene.LoopingMirrorBlock;
import org.agmas.noellesroles.scene.VerticalLoopingMirrorLoop;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 把 A–B 体积用客户端 {@code setBlock} 叠到上方有限份；失活时还原。下方只画雾。
 */
public final class VerticalLoopingMirrorClientScene {
    private static final int BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int REHASH_INTERVAL = 20;

    private final ClientLevel level;
    private final VerticalLoopingMirrorLoop loop;
    private final int copiesUp;
    private final Map<BlockPos, BlockState> overwritten = new HashMap<>();
    private final List<Int2ObjectMap<Entity>> layerCopies = new ArrayList<>();
    private long sourceHash = Long.MIN_VALUE;
    private int rehashCountdown = 0;

    public VerticalLoopingMirrorClientScene(ClientLevel level, VerticalLoopingMirrorLoop loop) {
        this.level = level;
        this.loop = loop;
        this.copiesUp = effectiveCopies(level, loop);
        for (int i = 0; i < copiesUp; i++) {
            layerCopies.add(new Int2ObjectOpenHashMap<>());
        }
    }

    public VerticalLoopingMirrorLoop loop() {
        return loop;
    }

    public void tick() {
        if (--rehashCountdown <= 0) {
            rehashCountdown = REHASH_INTERVAL;
            long hash = computeSourceHash();
            if (hash != sourceHash) {
                rebuildBlocks();
                sourceHash = hash;
            }
        }
        updateEntities();
    }

    public void close() {
        restoreBlocks();
        for (Int2ObjectMap<Entity> copies : layerCopies) {
            discardAll(copies);
        }
    }

    public void render(WorldRenderContext context) {
        renderFog(context);
    }

    private void rebuildBlocks() {
        restoreBlocks();
        if (copiesUp <= 0) {
            return;
        }
        int period = loop.periodY();
        int written = 0;
        BlockPos.MutableBlockPos source = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        int minBuild = level.getMinBuildHeight();
        int maxBuild = level.getMaxBuildHeight();
        for (int layer = 1; layer <= copiesUp
                && written < VerticalLoopingMirrorLoop.MAX_COPY_BLOCKS; layer++) {
            int dy = layer * period;
            for (int y = loop.minY(); y <= loop.maxY()
                    && written < VerticalLoopingMirrorLoop.MAX_COPY_BLOCKS; y++) {
                int ty = y + dy;
                if (ty < minBuild || ty >= maxBuild) {
                    continue;
                }
                for (int x = loop.minX(); x <= loop.maxX()
                        && written < VerticalLoopingMirrorLoop.MAX_COPY_BLOCKS; x++) {
                    for (int z = loop.minZ(); z <= loop.maxZ()
                            && written < VerticalLoopingMirrorLoop.MAX_COPY_BLOCKS; z++) {
                        source.set(x, y, z);
                        target.set(x, ty, z);
                        if (writeCopy(source, target)) {
                            written++;
                        }
                    }
                }
            }
        }
    }

    private boolean writeCopy(BlockPos source, BlockPos target) {
        if (!level.hasChunkAt(source) || !level.hasChunkAt(target)) {
            return false;
        }
        BlockState state = level.getBlockState(source);
        if (state.getBlock() instanceof LoopingMirrorBlock) {
            return false;
        }
        BlockState current = level.getBlockState(target);
        if (current.getBlock() instanceof LoopingMirrorBlock) {
            return false;
        }
        if (state.isAir() && current.isAir()) {
            return false;
        }
        overwritten.putIfAbsent(target.immutable(), current);
        if (current != state) {
            level.setBlock(target, state, BLOCK_FLAGS, 0);
        }
        return true;
    }

    private void restoreBlocks() {
        for (Map.Entry<BlockPos, BlockState> entry : overwritten.entrySet()) {
            if (level.hasChunkAt(entry.getKey())) {
                BlockState current = level.getBlockState(entry.getKey());
                if (current.getBlock() instanceof LoopingMirrorBlock) {
                    continue;
                }
                if (current != entry.getValue()) {
                    level.setBlock(entry.getKey(), entry.getValue(), BLOCK_FLAGS, 0);
                }
            }
        }
        overwritten.clear();
    }

    private long computeSourceHash() {
        long hash = 1125899906842597L;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = loop.minY(); y <= loop.maxY(); y++) {
            for (int x = loop.minX(); x <= loop.maxX(); x++) {
                for (int z = loop.minZ(); z <= loop.maxZ(); z++) {
                    cursor.set(x, y, z);
                    int id = level.hasChunkAt(cursor)
                            ? System.identityHashCode(level.getBlockState(cursor))
                            : 0;
                    hash = hash * 31L + id;
                }
            }
        }
        return hash;
    }

    private void renderFog(WorldRenderContext context) {
        if (context.consumers() == null || context.matrixStack() == null) {
            return;
        }
        AABB fog = loop.fogBox();
        if (context.frustum() != null && !context.frustum().isVisible(fog)) {
            return;
        }
        PoseStack poseStack = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = context.consumers().getBuffer(RenderType.translucent());
        int layers = 4;
        double step = VerticalLoopingMirrorLoop.FOG_DEPTH / (double) layers;
        float minX = (float) fog.minX;
        float maxX = (float) fog.maxX;
        float minZ = (float) fog.minZ;
        float maxZ = (float) fog.maxZ;
        float[] fogColor = RenderSystem.getShaderFogColor();
        float r = fogColor[0];
        float g = fogColor[1];
        float b = fogColor[2];
        for (int i = 0; i < layers; i++) {
            float y1 = (float) (loop.minY() - i * step);
            float y0 = (float) (loop.minY() - (i + 1) * step);
            float alpha = 0.18F + i * 0.16F;
            addFogBox(consumer, matrix, minX, y0, minZ, maxX, y1, maxZ, r, g, b, alpha);
        }
        poseStack.popPose();
    }

    private static void addFogBox(VertexConsumer consumer, Matrix4f matrix,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float r, float g, float b, float a) {
        addFogQuad(consumer, matrix, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a);
        addFogQuad(consumer, matrix, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, r, g, b, Math.min(1.0F, a + 0.12F));
        addFogQuad(consumer, matrix, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
        addFogQuad(consumer, matrix, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, a);
        addFogQuad(consumer, matrix, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, r, g, b, a);
        addFogQuad(consumer, matrix, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
    }

    private static void addFogQuad(VertexConsumer consumer, Matrix4f matrix,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float r, float g, float b, float a) {
        addFogVertex(consumer, matrix, x1, y1, z1, r, g, b, a);
        addFogVertex(consumer, matrix, x2, y2, z2, r, g, b, a);
        addFogVertex(consumer, matrix, x3, y3, z3, r, g, b, a);
        addFogVertex(consumer, matrix, x4, y4, z4, r, g, b, a);
    }

    private static void addFogVertex(VertexConsumer consumer, Matrix4f matrix,
            float x, float y, float z, float r, float g, float b, float a) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, a)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private void updateEntities() {
        if (copiesUp <= 0) {
            return;
        }
        List<Entity> sources = level.getEntities((Entity) null, loop.cellBox(),
                entity -> entity != null && entity.getId() >= 0 && MirrorReflectionManager.canReflect(entity));
        if (sources.size() > VerticalLoopingMirrorLoop.MAX_ENTITY_SOURCES) {
            Vec3 focus = Minecraft.getInstance().player == null
                    ? loop.cellBox().getCenter()
                    : Minecraft.getInstance().player.position();
            sources.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(focus)));
            sources = sources.subList(0, VerticalLoopingMirrorLoop.MAX_ENTITY_SOURCES);
        }
        IntSet seen = new IntOpenHashSet(sources.size());
        for (Entity src : sources) {
            seen.add(src.getId());
        }
        int period = loop.periodY();
        for (int layer = 1; layer <= copiesUp; layer++) {
            Int2ObjectMap<Entity> copies = layerCopies.get(layer - 1);
            for (Entity src : sources) {
                Entity copy = copies.get(src.getId());
                if (copy == null) {
                    copy = MirrorReflectionManager.createCopy(level, src);
                    if (copy == null) {
                        continue;
                    }
                    copies.put(src.getId(), copy);
                    level.addEntity(copy);
                }
                applyTransform(src, copy, layer * period);
            }
            Iterator<Int2ObjectMap.Entry<Entity>> it = copies.int2ObjectEntrySet().iterator();
            while (it.hasNext()) {
                Int2ObjectMap.Entry<Entity> entry = it.next();
                if (!seen.contains(entry.getIntKey())) {
                    MirrorReflectionManager.discardCopy(level, entry.getValue());
                    it.remove();
                }
            }
        }
    }

    private void applyTransform(Entity source, Entity copy, int yOffset) {
        Vec3 position = source.position().add(0.0D, yOffset, 0.0D);
        Vec3 previous = new Vec3(source.xOld, source.yOld, source.zOld).add(0.0D, yOffset, 0.0D);
        copy.setPos(position.x, position.y, position.z);
        copy.xOld = previous.x;
        copy.yOld = previous.y;
        copy.zOld = previous.z;
        copy.xo = previous.x;
        copy.yo = previous.y;
        copy.zo = previous.z;
        copy.setDeltaMovement(Vec3.ZERO);
        copy.setYRot(source.getYRot());
        copy.yRotO = source.yRotO;
        copy.setXRot(source.getXRot());
        copy.xRotO = source.xRotO;
        copy.tickCount = source.tickCount;
        copy.setInvisible(source.isInvisible() || source.isSpectator());

        if (source instanceof LivingEntity fromLiving && copy instanceof LivingEntity toLiving) {
            toLiving.yBodyRot = fromLiving.yBodyRot;
            toLiving.yBodyRotO = fromLiving.yBodyRotO;
            toLiving.yHeadRot = fromLiving.yHeadRot;
            toLiving.yHeadRotO = fromLiving.yHeadRotO;
            toLiving.walkAnimation.update(fromLiving.walkAnimation.position() - toLiving.walkAnimation.position(), 1.0F);
            toLiving.walkAnimation.setSpeed(fromLiving.walkAnimation.speed());
            toLiving.setPose(fromLiving.getPose());
            toLiving.swinging = fromLiving.swinging;
            toLiving.swingingArm = fromLiving.swingingArm;
            toLiving.attackAnim = fromLiving.attackAnim;
            toLiving.oAttackAnim = fromLiving.oAttackAnim;
            toLiving.hurtTime = fromLiving.hurtTime;
            toLiving.hurtDuration = fromLiving.hurtDuration;
            toLiving.deathTime = fromLiving.deathTime;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack wanted = fromLiving.getItemBySlot(slot);
                if (!ItemStack.matches(toLiving.getItemBySlot(slot), wanted)) {
                    toLiving.setItemSlot(slot, wanted.copy());
                }
            }
        }
    }

    private void discardAll(Int2ObjectMap<Entity> copies) {
        for (Entity copy : copies.values()) {
            MirrorReflectionManager.discardCopy(level, copy);
        }
        copies.clear();
    }

    private static int effectiveCopies(ClientLevel level, VerticalLoopingMirrorLoop loop) {
        int period = loop.periodY();
        if (period <= 0) {
            return 0;
        }
        int maxByHeight = (level.getMaxBuildHeight() - 1 - loop.maxY()) / period;
        return Math.max(0, Math.min(loop.copiesUp(), maxByHeight));
    }
}
