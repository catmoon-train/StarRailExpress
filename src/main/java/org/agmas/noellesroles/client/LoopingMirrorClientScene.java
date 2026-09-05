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

import io.wifi.starrailexpress.client.mirror.MirrorReflectionManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.block.scene.LoopingMirrorBlock;
import org.agmas.noellesroles.scene.LoopingMirrorLoop;
import org.agmas.noellesroles.scene.LoopingMirrorPlane;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 把其中一个平面内侧的场景平移复制到另一平面后方（不镜像翻转），并维护实体副本。
 */
public final class LoopingMirrorClientScene {
    private static final int BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int REHASH_INTERVAL = 20;

    private final ClientLevel level;
    private final LoopingMirrorLoop loop;
    private final Map<BlockPos, BlockState> overwritten = new HashMap<>();
    private final Int2ObjectMap<Entity> copiesA = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Entity> copiesB = new Int2ObjectOpenHashMap<>();
    private long sourceHash = Long.MIN_VALUE;
    private int rehashCountdown = 0;

    public LoopingMirrorClientScene(ClientLevel level, LoopingMirrorLoop loop) {
        this.level = level;
        this.loop = loop;
    }

    public LoopingMirrorLoop loop() {
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
        updateEntities(loop.planeA(), loop.planeB(), copiesA);
        updateEntities(loop.planeB(), loop.planeA(), copiesB);
    }

    public void close() {
        restoreBlocks();
        discardAll(copiesA);
        discardAll(copiesB);
    }

    private void discardAll(Int2ObjectMap<Entity> copies) {
        for (Entity copy : copies.values()) {
            MirrorReflectionManager.discardCopy(level, copy);
        }
        copies.clear();
    }

    private void rebuildBlocks() {
        restoreBlocks();
        copyBehind(loop.planeB(), loop.planeA());
        copyBehind(loop.planeA(), loop.planeB());
    }

    private void copyBehind(LoopingMirrorPlane source, LoopingMirrorPlane dest) {
        int depth = loop.copyDepth();
        for (int k = 1; k <= depth; k++) {
            for (int u = dest.uMin(); u <= dest.uMax(); u++) {
                for (int v = dest.vMin(); v <= dest.vMax(); v++) {
                    BlockPos target = dest.behind(k, u, v);
                    if (!level.hasChunkAt(target)) {
                        continue;
                    }
                    BlockState current = level.getBlockState(target);
                    if (current.getBlock() instanceof LoopingMirrorBlock) {
                        continue;
                    }
                    Vec3 targetCenter = Vec3.atCenterOf(target);
                    if (source.n(targetCenter) < 0.0D && source.containsUV(targetCenter, 0.0D)) {
                        continue;
                    }
                    int su = source.mapU(u, dest);
                    int sv = source.mapV(v, dest);
                    BlockPos srcPos = source.interior(k, su, sv);
                    if (!level.hasChunkAt(srcPos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(srcPos);
                    if (state.getBlock() instanceof LoopingMirrorBlock) {
                        continue;
                    }
                    overwritten.putIfAbsent(target, current);
                    if (current != state) {
                        level.setBlock(target, state, BLOCK_FLAGS, 0);
                    }
                }
            }
        }
    }

    private void restoreBlocks() {
        for (Map.Entry<BlockPos, BlockState> entry : overwritten.entrySet()) {
            if (level.hasChunkAt(entry.getKey())) {
                level.setBlock(entry.getKey(), entry.getValue(), BLOCK_FLAGS, 0);
            }
        }
        overwritten.clear();
    }

    private long computeSourceHash() {
        long hash = 1125899906842597L;
        hash = hashSide(hash, loop.planeA());
        hash = hashSide(hash, loop.planeB());
        return hash;
    }

    private long hashSide(long hash, LoopingMirrorPlane plane) {
        int depth = loop.copyDepth();
        for (int k = 1; k <= depth; k++) {
            for (int u = plane.uMin(); u <= plane.uMax(); u++) {
                for (int v = plane.vMin(); v <= plane.vMax(); v++) {
                    BlockPos source = plane.interior(k, u, v);
                    int id = level.hasChunkAt(source)
                            ? System.identityHashCode(level.getBlockState(source))
                            : 0;
                    hash = hash * 31L + id;
                }
            }
        }
        return hash;
    }

    private void updateEntities(LoopingMirrorPlane source, LoopingMirrorPlane dest, Int2ObjectMap<Entity> copies) {
        List<Entity> sources = level.getEntities((Entity) null, source.interiorBox(loop.copyDepth()),
                entity -> entity != null && entity.getId() >= 0 && MirrorReflectionManager.canReflect(entity)
                        && source.n(entity.position()) < 0.0D);
        IntSet seen = new IntOpenHashSet(sources.size());
        for (Entity src : sources) {
            seen.add(src.getId());
            Entity copy = copies.get(src.getId());
            if (copy == null) {
                copy = MirrorReflectionManager.createCopy(level, src);
                if (copy == null) {
                    continue;
                }
                copies.put(src.getId(), copy);
                level.addEntity(copy);
            }
            applyTransform(src, copy, source, dest);
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

    private void applyTransform(Entity source, Entity copy, LoopingMirrorPlane from, LoopingMirrorPlane to) {
        Vec3 position = loop.mapPoint(from, to, source.position());
        Vec3 previous = loop.mapPoint(from, to, new Vec3(source.xOld, source.yOld, source.zOld));
        copy.setPos(position.x, position.y, position.z);
        copy.xOld = previous.x;
        copy.yOld = previous.y;
        copy.zOld = previous.z;
        copy.xo = previous.x;
        copy.yo = previous.y;
        copy.zo = previous.z;
        copy.setDeltaMovement(Vec3.ZERO);

        Vec3 look = loop.mapVec(from, to, source.getLookAngle());
        copy.setYRot(loop.yawOf(look));
        copy.yRotO = loop.yawOf(look);
        copy.setXRot(loop.pitchOf(look));
        copy.xRotO = source.xRotO;
        copy.tickCount = source.tickCount;
        copy.setInvisible(source.isInvisible() || source.isSpectator());

        if (source instanceof LivingEntity fromLiving && copy instanceof LivingEntity toLiving) {
            toLiving.yBodyRot = copy.getYRot();
            toLiving.yBodyRotO = copy.getYRot();
            toLiving.yHeadRot = copy.getYRot();
            toLiving.yHeadRotO = copy.getYRot();
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
}
