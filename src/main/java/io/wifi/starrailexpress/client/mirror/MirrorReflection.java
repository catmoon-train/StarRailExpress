package io.wifi.starrailexpress.client.mirror;

import io.wifi.starrailexpress.content.block.MirrorBlock;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 一面镜子的反射实例：把镜面前方的房间几何镜像写进镜面后方的空腔，并为每个源实体维护一个镜像副本。
 *
 * <p>因为写进去的是真实的世界方块和真实的实体，它们走的是同一个渲染 pass，
 * 所以光影（Iris）与 Sodium 都不需要任何适配。
 */
public final class MirrorReflection {

    /** 不触发邻居更新、不触发形状更新——我们只是在改客户端的可视副本。 */
    private static final int BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final int REHASH_INTERVAL = 20;

    public record Surface(MirrorPlane plane, int uMin, int uMax, int vMin, int vMax, int depth, int margin) {
    }

    private final ClientLevel level;
    private final MirrorPlane plane;
    private final int depth;
    private final int uMin;
    private final int uMax;
    private final int vMin;
    private final int vMax;

    /** 被我们覆盖过的格子 -> 覆盖前的原状态，用于失活时还原。 */
    private final Map<BlockPos, BlockState> overwritten = new HashMap<>();
    /** 源实体 networkId -> 镜像副本。 */
    private final Int2ObjectMap<Entity> copies = new Int2ObjectOpenHashMap<>();

    private long sourceHash = Long.MIN_VALUE;
    private int rehashCountdown = 0;

    public MirrorReflection(ClientLevel level, Surface surface) {
        this.level = level;
        this.plane = surface.plane();
        this.depth = surface.depth();
        this.uMin = surface.uMin() - surface.margin();
        this.uMax = surface.uMax() + surface.margin();
        this.vMin = surface.vMin() - surface.margin();
        this.vMax = surface.vMax() + surface.margin();
    }

    public ClientLevel level() {
        return this.level;
    }

    public void tick() {
        if (--this.rehashCountdown <= 0) {
            this.rehashCountdown = REHASH_INTERVAL;
            long hash = computeSourceHash();
            if (hash != this.sourceHash) {
                rebuildBlocks();
                this.sourceHash = hash;
            }
        }
        updateEntities();
    }

    public void close() {
        restoreBlocks();
        for (Entity copy : this.copies.values()) {
            MirrorReflectionManager.discardCopy(this.level, copy);
        }
        this.copies.clear();
    }

    // ---------------------------------------------------------------- 方块

    private void rebuildBlocks() {
        restoreBlocks();

        // k 必须从 1 起：k = 0 层的像落在镜子自身所在的墙层上，写下去会把镜子和它周围的墙抹掉。
        for (int k = 1; k < this.depth; k++) {
            int sourceLayer = this.plane.sourceCoord(k);
            for (int u = this.uMin; u <= this.uMax; u++) {
                for (int v = this.vMin; v <= this.vMax; v++) {
                    BlockPos source = at(sourceLayer, u, v);
                    if (!this.level.hasChunkAt(source)) {
                        continue;
                    }
                    BlockState state = this.level.getBlockState(source);
                    if (state.getBlock() instanceof MirrorBlock) {
                        continue; // 不复制别的镜子，避免镜中镜递归
                    }
                    BlockPos target = this.plane.reflect(source);
                    if (!this.level.hasChunkAt(target)) {
                        continue;
                    }
                    BlockState current = this.level.getBlockState(target);
                    if (current.getBlock() instanceof MirrorBlock) {
                        continue; // 绝不覆盖镜子本身，否则会连同它的 BlockEntity 一起消失
                    }
                    this.overwritten.putIfAbsent(target, current);
                    BlockState mirrored = state.mirror(this.plane.blockMirror());
                    if (current != mirrored) {
                        this.level.setBlock(target, mirrored, BLOCK_FLAGS, 0);
                    }
                }
            }
        }
    }

    private void restoreBlocks() {
        for (Map.Entry<BlockPos, BlockState> entry : this.overwritten.entrySet()) {
            if (this.level.hasChunkAt(entry.getKey())) {
                this.level.setBlock(entry.getKey(), entry.getValue(), BLOCK_FLAGS, 0);
            }
        }
        this.overwritten.clear();
    }

    /**
     * BlockState 实例在注册表里是唯一的，identityHashCode 在单次运行内稳定，
     * 足以廉价地判断"源房间有没有变过"。
     */
    private long computeSourceHash() {
        long hash = 1125899906842597L;
        for (int k = 1; k < this.depth; k++) {
            int sourceLayer = this.plane.sourceCoord(k);
            for (int u = this.uMin; u <= this.uMax; u++) {
                for (int v = this.vMin; v <= this.vMax; v++) {
                    BlockPos source = at(sourceLayer, u, v);
                    int id = this.level.hasChunkAt(source)
                            ? System.identityHashCode(this.level.getBlockState(source))
                            : 0;
                    hash = hash * 31L + id;
                }
            }
        }
        return hash;
    }

    // ---------------------------------------------------------------- 实体

    private void updateEntities() {
        List<Entity> sources = this.level.getEntities((Entity) null, sourceBox(), MirrorReflectionManager::canReflect);
        IntSet seen = new IntOpenHashSet(sources.size());

        for (Entity source : sources) {
            if (!this.plane.inFront(source.position())) {
                continue;
            }
            seen.add(source.getId());

            Entity copy = this.copies.get(source.getId());
            if (copy == null) {
                copy = MirrorReflectionManager.createCopy(this.level, source);
                if (copy == null) {
                    continue;
                }
                this.copies.put(source.getId(), copy);
                this.level.addEntity(copy);
            }
            applyTransform(source, copy);
        }

        Iterator<Int2ObjectMap.Entry<Entity>> it = this.copies.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            Int2ObjectMap.Entry<Entity> entry = it.next();
            if (!seen.contains(entry.getIntKey())) {
                MirrorReflectionManager.discardCopy(this.level, entry.getValue());
                it.remove();
            }
        }
    }

    private void applyTransform(Entity source, Entity copy) {
        Vec3 position = this.plane.reflect(source.position());
        Vec3 previous = this.plane.reflect(new Vec3(source.xOld, source.yOld, source.zOld));

        copy.setPos(position.x, position.y, position.z);
        // 渲染插值读的是 xOld/yOld/zOld；xo/yo/zo 供 getPosition(partialTick) 用。两套都要跟上。
        copy.xOld = previous.x;
        copy.yOld = previous.y;
        copy.zOld = previous.z;
        copy.xo = previous.x;
        copy.yo = previous.y;
        copy.zo = previous.z;
        copy.setDeltaMovement(Vec3.ZERO);

        copy.setYRot(this.plane.reflectYaw(source.getYRot()));
        copy.yRotO = this.plane.reflectYaw(source.yRotO);
        copy.setXRot(source.getXRot());
        copy.xRotO = source.xRotO;
        copy.tickCount = source.tickCount;
        copy.setInvisible(source.isInvisible());

        if (source instanceof LivingEntity from && copy instanceof LivingEntity to) {
            to.yBodyRot = this.plane.reflectYaw(from.yBodyRot);
            to.yBodyRotO = this.plane.reflectYaw(from.yBodyRotO);
            to.yHeadRot = this.plane.reflectYaw(from.yHeadRot);
            to.yHeadRotO = this.plane.reflectYaw(from.yHeadRotO);

            // WalkAnimationState 没有 position 的 setter。update(delta, 1.0) 会把 position 推到目标值，
            // 之后再校正 speed；否则倒影的迈腿相位会和本体错开。
            to.walkAnimation.update(from.walkAnimation.position() - to.walkAnimation.position(), 1.0F);
            to.walkAnimation.setSpeed(from.walkAnimation.speed());

            to.setPose(from.getPose());
            to.swinging = from.swinging;
            to.swingingArm = from.swingingArm;
            to.attackAnim = from.attackAnim;
            to.oAttackAnim = from.oAttackAnim;
            to.hurtTime = from.hurtTime;
            to.hurtDuration = from.hurtDuration;
            to.deathTime = from.deathTime;

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack wanted = from.getItemBySlot(slot);
                if (!ItemStack.matches(to.getItemBySlot(slot), wanted)) {
                    to.setItemSlot(slot, wanted.copy());
                }
            }
        }
    }

    /** 镜面正前方、深度 depth 的整块房间体积。实体是连续量，所以从平面本身算起（不跳 k = 0）。 */
    private AABB sourceBox() {
        double lo = this.plane.positive() ? this.plane.boundary() : this.plane.boundary() - this.depth;
        double hi = this.plane.positive() ? this.plane.boundary() + this.depth : this.plane.boundary();
        return this.plane.axis() == Direction.Axis.X
                ? new AABB(lo, this.uMin, this.vMin, hi, this.uMax + 1.0D, this.vMax + 1.0D)
                : new AABB(this.uMin, this.vMin, lo, this.uMax + 1.0D, this.vMax + 1.0D, hi);
    }

    /** 平面内坐标 (u, v) 与层坐标 a 组装成世界坐标：X 面用 (Y, Z)，Z 面用 (X, Y)。 */
    private BlockPos at(int a, int u, int v) {
        return this.plane.axis() == Direction.Axis.X ? new BlockPos(a, u, v) : new BlockPos(u, v, a);
    }
}
