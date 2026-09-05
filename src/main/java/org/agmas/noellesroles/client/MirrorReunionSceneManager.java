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
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 破镜重圆：客户端可见区域按圆形从外向内逐渐坠落，效果结束再从内向外还原。
 *
 * <p>不改客户端方块状态（避免大量 setBlock / 区块重建卡顿）。可见范围用水平圆柱波前剔除
 * （任意高度），近处再补坠落动画。
 */
@Environment(EnvType.CLIENT)
public class MirrorReunionSceneManager {
    public static final MirrorReunionSceneManager INSTANCE = new MirrorReunionSceneManager();

    private static final int Y_DOWN = 96;
    private static final int Y_UP = 160;
    private static final int COLLAPSE_START_TICKS = 80;
    private static final int COLLAPSE_END_TICKS = 200;
    private static final int COLLAPSE_DURATION_TICKS = COLLAPSE_END_TICKS - COLLAPSE_START_TICKS;
    private static final int RESTORE_DURATION_TICKS = 32;
    private static final int SCAN_BUDGET = 20000;
    private static final int FALL_STARTS_PER_TICK = 320;
    private static final int RESTORE_PER_TICK = 400;
    private static final int MAX_FALLING = 220;
    private static final int MAX_RISING = 72;
    private static final float GRAVITY = 0.055f;
    private static final float CAMERA_GRAVITY = 0.038f;
    private static final float MAX_CAMERA_FALL = 56.0f;
    private static final float NEAR_ANIM_DIST = 48.0f;
    private static final float NEAR_ANIM_DIST_SQ = NEAR_ANIM_DIST * NEAR_ANIM_DIST;
    private static final int FALL_LIFE_TICKS = 22;
    private static final int MIN_CULL_RADIUS = 80;
    private static final int MAX_CULL_RADIUS = 256;
    /** 单 tick 位移超过这个距离视为传送，立刻中止场景以免旧原点剔除跟着玩家走。 */
    private static final double TRANSFER_JUMP_DIST_SQ = 32.0 * 32.0;
    /** 传送后继续给新加载区块标脏的时间，避免大厅网格带着「空气」缓存。 */
    private static final int POST_TRANSFER_REBUILD_TICKS = 60;

    private static volatile boolean hideAllowed = false;
    private static volatile LongOpenHashSet hiddenSnapshot = new LongOpenHashSet();
    private static volatile WaveCull WAVE = WaveCull.NONE;

    private final LongOpenHashSet seen = new LongOpenHashSet();
    private final LongOpenHashSet hiddenWorking = new LongOpenHashSet();
    private final LongOpenHashSet dirtySections = new LongOpenHashSet();
    private final LongOpenHashSet affectedSections = new LongOpenHashSet();
    private final LongOpenHashSet fallCells = new LongOpenHashSet();
    private final LongArrayList packedPos = new LongArrayList();
    private final List<AnimPiece> falling = new ArrayList<>();
    private final List<AnimPiece> rising = new ArrayList<>();
    private final Random random = new Random();

    private float[] horizDist = new float[0];
    private int[] order = new int[0];
    private int orderSize = 0;
    private int orderCursor = 0;
    private int restoreCursor = 0;
    private int restoreTicks = 0;
    private int sortedCount = -1;

    private boolean active = false;
    private boolean restoring = false;
    private boolean scanDone = false;
    private boolean hiddenDirty = false;
    private boolean collapseStartedSound;
    private boolean cameraFalling;
    private boolean sunkToBottom;
    private boolean controlLocked;
    private float cameraFallY;
    private float cameraFallPrevY;
    private float cameraFallVy;
    private float cameraReturnStartY;
    private int tickCounter = 0;
    private int radius = 40;
    private int scanDx;
    private int scanDz;
    private int scanDy;
    private float cullMaxRadius = 128.0f;
    private float publishedCullRadius = Float.NaN;
    private float restoreStartRadius = 0.0f;
    private BlockPos origin = BlockPos.ZERO;
    private ClientLevel boundLevel;
    private Vec3 lastTrackedPos;
    private int ignoreActivateTicks;
    private int postTransferRebuildTicks;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.tickClient(client));
        WorldRenderEvents.AFTER_ENTITIES.register(INSTANCE::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> INSTANCE.abortForTransfer());
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> INSTANCE.onChunkLoad(chunk.getPos()));
    }

    /**
     * 网格构建线程也会调用，必须无锁且只读快照。
     */
    public static boolean shouldHideBlock(int x, int y, int z) {
        if (!hideAllowed) {
            return false;
        }
        if (WAVE.hides(x, z)) {
            return true;
        }
        LongOpenHashSet set = hiddenSnapshot;
        return !set.isEmpty() && set.contains(BlockPos.asLong(x, y, z));
    }

    public boolean isActive() {
        return active;
    }

    public float getShakeIntensity(float partialTick) {
        if (restoring) {
            return 0.22f * (1.0f - restoreTicks / (float) RESTORE_DURATION_TICKS);
        }
        if (!active && !restoring && cameraFallY == 0.0f && cameraFallPrevY == 0.0f) {
            return 0.0f;
        }
        float t = tickCounter + partialTick;
        if (t < COLLAPSE_START_TICKS) {
            return 0.32f + 0.45f * (t / COLLAPSE_START_TICKS);
        }
        if (t < COLLAPSE_END_TICKS) {
            float p = (t - COLLAPSE_START_TICKS) / COLLAPSE_DURATION_TICKS;
            return 0.7f + 0.45f * p;
        }
        return 1.05f;
    }

    public float getFilterStrength() {
        if (restoring) {
            return 0.9f * (1.0f - Mth.clamp(restoreTicks / (float) RESTORE_DURATION_TICKS, 0.0f, 1.0f));
        }
        if (!active) {
            return 0.0f;
        }
        if (tickCounter < 36) {
            return 0.75f * (tickCounter / 36.0f);
        }
        if (tickCounter < COLLAPSE_END_TICKS) {
            return 0.8f + 0.2f * collapseProgress();
        }
        return 1.0f;
    }

    /**
     * 脚下方块破碎后的镜头下坠偏移（负值向下）。药水结束还原时平滑回到 0。
     */
    public float getCameraFallY(float partialTick) {
        if (!cameraFalling && !restoring && cameraFallY == 0.0f && cameraFallPrevY == 0.0f) {
            return 0.0f;
        }
        return Mth.lerp(partialTick, cameraFallPrevY, cameraFallY);
    }

    public boolean wantsBlackMonitor() {
        return sunkToBottom && active && !restoring;
    }

    public boolean blocksPlayerControl() {
        return controlLocked;
    }

    public void activate() {
        if (active) {
            return;
        }
        if (restoring) {
            forceRestore();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        origin = mc.player.blockPosition().immutable();
        boundLevel = mc.level;
        lastTrackedPos = mc.player.position();
        hideAllowed = true;
        cullMaxRadius = Mth.clamp(mc.options.getEffectiveRenderDistance() * 16 + 32, MIN_CULL_RADIUS, MAX_CULL_RADIUS);
        radius = Mth.ceil(NEAR_ANIM_DIST) + 8;
        publishedCullRadius = Float.NaN;
        restoreStartRadius = 0.0f;
        active = true;
        restoring = false;
        scanDone = false;
        collapseStartedSound = false;
        tickCounter = 0;
        scanDx = -radius;
        scanDz = -radius;
        scanDy = -Y_DOWN;
        orderCursor = 0;
        restoreCursor = 0;
        restoreTicks = 0;
        sortedCount = -1;
        seen.clear();
        hiddenWorking.clear();
        hiddenDirty = true;
        packedPos.clear();
        falling.clear();
        rising.clear();
        dirtySections.clear();
        affectedSections.clear();
        fallCells.clear();
        resetCameraFall();
        publishHidden();
    }

    public void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        startRestoration();
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (active) {
            tickActive(mc);
        } else if (restoring) {
            tickRestore(mc);
        }
        tickCamera(mc);
        tickAnimations();
        publishHidden();
        closeInventoryIfLocked(mc);
    }

    /**
     * 立刻关掉剔除（可在网络线程调用）。网格构建线程下一拍就不会再把方块编成空气。
     */
    public void clearHideState() {
        hideAllowed = false;
        WAVE = WaveCull.NONE;
        publishedCullRadius = Float.NaN;
        hiddenSnapshot = new LongOpenHashSet();
    }

    /**
     * 玩家被传送 / 回大厅 / 换世界时调用：跳过还原动画，清剔除，只重编落点附近区块。
     */
    public void abortForTransfer() {
        forceRestore(true);
    }

    public void forceRestore() {
        forceRestore(false);
    }

    private void forceRestore(boolean transfer) {
        boolean wasBusy = active || restoring || WAVE.enabled || !hiddenWorking.isEmpty();
        BlockPos oldOrigin = origin;
        clearHideState();
        hiddenWorking.clear();
        hiddenDirty = false;
        falling.clear();
        rising.clear();
        packedPos.clear();
        seen.clear();
        fallCells.clear();
        dirtySections.clear();
        if (transfer) {
            ignoreActivateTicks = POST_TRANSFER_REBUILD_TICKS;
            postTransferRebuildTicks = POST_TRANSFER_REBUILD_TICKS;
            dirtyAroundPlayer();
        } else if (wasBusy) {
            dirtyLoadedAround(oldOrigin, Math.min(cullMaxRadius, NEAR_ANIM_DIST + 32.0f));
            markAffectedDirty();
        }
        affectedSections.clear();
        clearFlags();
        boundLevel = null;
        lastTrackedPos = null;
    }

    private void tickActive(Minecraft mc) {
        tickCounter++;
        ClientLevel level = mc.level;
        tickScan(level);
        if (tickCounter == COLLAPSE_START_TICKS && !collapseStartedSound) {
            collapseStartedSound = true;
            playLocal(level, origin, 0.55f, 0.5f);
        }
        if (tickCounter >= COLLAPSE_START_TICKS) {
            publishWaveRadius(NEAR_ANIM_DIST * (1.0f - collapseProgress()));
            tickCollapse(level);
            spawnFallsOnWave(level);
        }
    }

    private void tickScan(ClientLevel level) {
        if (scanDone) {
            return;
        }
        int r = radius;
        int rSq = r * r;
        int budget = SCAN_BUDGET;
        while (budget > 0 && !scanDone) {
            int distSq = scanDx * scanDx + scanDz * scanDz;
            if (distSq > rSq) {
                nextColumn();
                budget--;
                continue;
            }
            tryTrack(level, origin.offset(scanDx, scanDy, scanDz), (float) Math.sqrt(distSq));
            budget--;
            if (++scanDy > Y_UP) {
                nextColumn();
            }
        }
    }

    private void nextColumn() {
        scanDy = -Y_DOWN;
        if (++scanDz > radius) {
            scanDz = -radius;
            if (++scanDx > radius) {
                scanDone = true;
            }
        }
    }

    private void tryTrack(ClientLevel level, BlockPos pos, float dist) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!shouldCollapse(state)) {
            return;
        }
        long packed = pos.asLong();
        if (!seen.add(packed)) {
            return;
        }
        packedPos.add(packed);
        int n = packedPos.size();
        if (horizDist.length < n) {
            float[] next = new float[Math.max(n * 2, 1024)];
            System.arraycopy(horizDist, 0, next, 0, horizDist.length);
            horizDist = next;
        }
        horizDist[n - 1] = dist;
        if (active && tickCounter >= COLLAPSE_START_TICKS && shouldAlreadyFall(n - 1)) {
            beginFall(level, packed, state);
        }
    }

    private boolean shouldAlreadyFall(int index) {
        if (orderSize == 0 || packedPos.isEmpty()) {
            return false;
        }
        float progress = collapseProgress();
        return index < packedPos.size() && horizDist[index] >= maxTrackedDist() * (1.0f - progress);
    }

    private boolean shouldCollapse(BlockState state) {
        return !state.isAir() && state.getRenderShape() != RenderShape.INVISIBLE;
    }

    private void tickCollapse(ClientLevel level) {
        ensureOrder();
        float progress = collapseProgress();
        int target = (int) (orderSize * progress);
        int started = 0;
        while (orderCursor < target && orderCursor < orderSize && started < FALL_STARTS_PER_TICK) {
            int index = order[orderCursor++];
            long packed = packedPos.getLong(index);
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.isLoaded(pos) ? level.getBlockState(pos) : null;
            if (state != null && shouldCollapse(state)) {
                beginFall(level, packed, state);
            }
            started++;
        }
    }

    private float collapseProgress() {
        int elapsed = tickCounter - COLLAPSE_START_TICKS;
        float linear = Mth.clamp(elapsed / (float) COLLAPSE_DURATION_TICKS, 0.0f, 1.0f);
        return linear * linear * (3.0f - 2.0f * linear);
    }

    private void ensureOrder() {
        int n = packedPos.size();
        if (n == sortedCount && orderSize == n) {
            return;
        }
        if (order.length < n) {
            order = new int[n];
        }
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        IntArrays.mergeSort(order, 0, n, (a, b) -> Float.compare(horizDist[b], horizDist[a]));
        orderSize = n;
        sortedCount = n;
        orderCursor = 0;
        while (orderCursor < orderSize && hiddenWorking.contains(packedPos.getLong(order[orderCursor]))) {
            orderCursor++;
        }
    }

    private float maxTrackedDist() {
        if (orderSize <= 0) {
            return radius;
        }
        return Math.max(1.0f, horizDist[order[0]]);
    }

    private void beginFall(ClientLevel level, long packed, BlockState state) {
        BlockPos pos = BlockPos.of(packed);
        if (!isNearPlayer(pos) || falling.size() >= MAX_FALLING) {
            return;
        }
        long cell = fallCell(pos);
        if (!fallCells.add(cell)) {
            return;
        }
        if (hiddenWorking.add(packed)) {
            hiddenDirty = true;
            markSection(pos);
        }
        spawnFall(pos, state, packedLight(level, pos), cell);
    }

    private void spawnFall(BlockPos pos, BlockState state, int light, long cell) {
        AnimPiece piece = new AnimPiece();
        piece.state = state;
        piece.cell = cell;
        piece.fadeOut = true;
        piece.x = piece.prevX = pos.getX() + (random.nextFloat() - 0.5f) * 0.55f;
        piece.y = piece.prevY = pos.getY();
        piece.z = piece.prevZ = pos.getZ() + (random.nextFloat() - 0.5f) * 0.55f;
        double ox = pos.getX() + 0.5 - origin.getX() - 0.5;
        double oz = pos.getZ() + 0.5 - origin.getZ() - 0.5;
        double len = Math.sqrt(ox * ox + oz * oz) + 0.001;
        float outward = 0.12f + random.nextFloat() * 0.28f;
        piece.vx = ox / len * outward + (random.nextFloat() - 0.5f) * 0.42f;
        piece.vy = 0.02f + random.nextFloat() * 0.08f;
        piece.vz = oz / len * outward + (random.nextFloat() - 0.5f) * 0.42f;
        piece.rotVelX = (random.nextFloat() - 0.5f) * 8.0f;
        piece.rotVelZ = (random.nextFloat() - 0.5f) * 8.0f;
        piece.originY = pos.getY();
        piece.light = light;
        falling.add(piece);
    }

    private boolean isNearPlayer(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        return dx * dx + dz * dz <= NEAR_ANIM_DIST_SQ;
    }

    private static long fallCell(BlockPos pos) {
        return BlockPos.asLong(pos.getX() >> 1, pos.getY() >> 1, pos.getZ() >> 1);
    }

    private void startRestoration() {
        restoring = true;
        restoreTicks = 0;
        cameraFalling = false;
        cameraReturnStartY = cameraFallY;
        cameraFallPrevY = cameraFallY;
        ensureOrder();
        restoreCursor = orderSize - 1;
        restoreStartRadius = Float.isNaN(publishedCullRadius) ? 0.0f : publishedCullRadius;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.playLocalSound(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.7f, 1.2f, false);
        }
        unhideFarImmediately();
    }

    private void unhideFarImmediately() {
        for (int i = 0; i < packedPos.size(); i++) {
            long packed = packedPos.getLong(i);
            if (!hiddenWorking.contains(packed)) {
                continue;
            }
            if (!isNearPlayer(BlockPos.of(packed))) {
                unhide(packed);
            }
        }
    }

    private void tickRestore(Minecraft mc) {
        restoreTicks++;
        float t = Mth.clamp(restoreTicks / (float) RESTORE_DURATION_TICKS, 0.0f, 1.0f);
        float ease = 1.0f - (1.0f - t) * (1.0f - t);
        publishWaveRadius(Mth.lerp(ease, restoreStartRadius, cullMaxRadius));
        ClientLevel level = mc.level;
        int started = 0;
        while (restoreCursor >= 0 && started < RESTORE_PER_TICK) {
            long packed = packedPos.getLong(order[restoreCursor--]);
            if (hiddenWorking.contains(packed)) {
                beginRise(level, packed);
            }
            started++;
        }
        if (restoreTicks >= RESTORE_DURATION_TICKS && rising.isEmpty()) {
            BlockPos restoreOrigin = origin;
            clearHideState();
            hiddenWorking.clear();
            dirtyLoadedAround(restoreOrigin, NEAR_ANIM_DIST + 32.0f);
            markAffectedDirty();
            clearFlags();
        }
    }

    private void beginRise(ClientLevel level, long packed) {
        BlockPos pos = BlockPos.of(packed);
        if (isNearPlayer(pos) && rising.size() < MAX_RISING && level.isLoaded(pos)) {
            BlockState state = level.getBlockState(pos);
            if (shouldCollapse(state)) {
                AnimPiece piece = new AnimPiece();
                piece.state = state;
                piece.trackedPacked = packed;
                piece.x = piece.prevX = pos.getX();
                piece.z = piece.prevZ = pos.getZ();
                piece.originY = pos.getY();
                piece.startY = pos.getY() - 4.5;
                piece.y = piece.prevY = piece.startY;
                piece.rotX = (random.nextFloat() - 0.5f) * 14.0f;
                piece.rotZ = (random.nextFloat() - 0.5f) * 14.0f;
                piece.light = packedLight(level, pos);
                rising.add(piece);
                return;
            }
        }
        unhide(packed);
    }

    private void unhide(long packed) {
        if (hiddenWorking.remove(packed)) {
            hiddenDirty = true;
            markSection(BlockPos.of(packed));
        }
    }

    private void tickAnimations() {
        for (int i = falling.size() - 1; i >= 0; i--) {
            AnimPiece piece = falling.get(i);
            piece.prevX = piece.x;
            piece.prevY = piece.y;
            piece.prevZ = piece.z;
            piece.prevRotX = piece.rotX;
            piece.prevRotZ = piece.rotZ;
            piece.vy -= GRAVITY;
            piece.x += piece.vx;
            piece.y += piece.vy;
            piece.z += piece.vz;
            piece.rotX += piece.rotVelX;
            piece.rotZ += piece.rotVelZ;
            piece.age++;
            if (piece.age >= FALL_LIFE_TICKS || piece.y < piece.originY - 28) {
                fallCells.remove(piece.cell);
                falling.remove(i);
            }
        }
        for (int i = rising.size() - 1; i >= 0; i--) {
            AnimPiece piece = rising.get(i);
            piece.prevX = piece.x;
            piece.prevY = piece.y;
            piece.prevZ = piece.z;
            piece.prevRotX = piece.rotX;
            piece.prevRotZ = piece.rotZ;
            piece.age++;
            float t = Mth.clamp(piece.age / 8.0f, 0.0f, 1.0f);
            float ease = 1.0f - (1.0f - t) * (1.0f - t);
            piece.y = piece.startY + (piece.originY - piece.startY) * ease;
            piece.rotX *= 0.78f;
            piece.rotZ *= 0.78f;
            if (t >= 1.0f) {
                unhide(piece.trackedPacked);
                rising.remove(i);
            }
        }
    }

    private void render(WorldRenderContext context) {
        if (falling.isEmpty() && rising.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        MultiBufferSource consumers = context.consumers();
        PoseStack pose = context.matrixStack();
        if (consumers == null || pose == null) {
            return;
        }
        float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.camera().getPosition();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        renderPieces(falling, dispatcher, pose, consumers, camera, partial);
        renderPieces(rising, dispatcher, pose, consumers, camera, partial);
    }

    private void renderPieces(List<AnimPiece> pieces, BlockRenderDispatcher dispatcher, PoseStack pose,
            MultiBufferSource consumers, Vec3 camera, float partial) {
        for (AnimPiece piece : pieces) {
            float scale = 1.0f;
            if (piece.fadeOut) {
                float life = (piece.age + partial) / FALL_LIFE_TICKS;
                if (life >= 1.0f) {
                    continue;
                }
                float fade = Mth.clamp((life - 0.28f) / 0.72f, 0.0f, 1.0f);
                scale = 1.0f - fade * fade;
                if (scale <= 0.04f) {
                    continue;
                }
            }
            double x = Mth.lerp(partial, piece.prevX, piece.x);
            double y = Mth.lerp(partial, piece.prevY, piece.y);
            double z = Mth.lerp(partial, piece.prevZ, piece.z);
            float rx = Mth.lerp(partial, piece.prevRotX, piece.rotX);
            float rz = Mth.lerp(partial, piece.prevRotZ, piece.rotZ);
            pose.pushPose();
            pose.translate(x - camera.x, y - camera.y, z - camera.z);
            pose.translate(0.5, 0.5, 0.5);
            pose.scale(scale, scale, scale);
            pose.mulPose(Axis.XP.rotationDegrees(rx));
            pose.mulPose(Axis.ZP.rotationDegrees(rz));
            pose.translate(-0.5, -0.5, -0.5);
            dispatcher.renderSingleBlock(piece.state, pose, consumers, piece.light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }

    private void markSection(BlockPos pos) {
        long section = SectionPos.asLong(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        dirtySections.add(section);
        affectedSections.add(section);
    }

    private void publishHidden() {
        if (hiddenDirty) {
            hiddenSnapshot = hiddenWorking.clone();
            hiddenDirty = false;
        }
        if (dirtySections.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null) {
            dirtySections.clear();
            return;
        }
        dirtySections.forEach((long packed) -> client.levelRenderer.setSectionDirtyWithNeighbors(
                SectionPos.x(packed), SectionPos.y(packed), SectionPos.z(packed)));
        dirtySections.clear();
    }

    private void markAffectedDirty() {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null) {
            affectedSections.clear();
            return;
        }
        affectedSections.forEach((long packed) -> client.levelRenderer.setSectionDirtyWithNeighbors(
                SectionPos.x(packed), SectionPos.y(packed), SectionPos.z(packed)));
        affectedSections.clear();
    }

    private static int packedLight(ClientLevel level, BlockPos pos) {
        return Math.max(LevelRenderer.getLightColor(level, pos), 0x00700070);
    }

    private void publishWaveRadius(float newRadius) {
        float clamped = Math.max(0.0f, newRadius);
        float prev = publishedCullRadius;
        WAVE = new WaveCull(true, origin.getX(), origin.getZ(), clamped * clamped);
        if (Float.isNaN(prev)) {
            markWaveDirty(clamped - 8.0f, cullMaxRadius + 48.0f);
        } else {
            markWaveDirty(Math.min(prev, clamped) - 24.0f, Math.max(prev, clamped) + 24.0f);
        }
        publishedCullRadius = clamped;
    }

    private void clearWave() {
        if (!WAVE.enabled && Float.isNaN(publishedCullRadius)) {
            return;
        }
        float radius = Float.isNaN(publishedCullRadius) ? NEAR_ANIM_DIST : publishedCullRadius;
        dirtyLoadedAround(origin, radius + 32.0f);
        WAVE = WaveCull.NONE;
        publishedCullRadius = Float.NaN;
    }

    private void markWaveDirty(float inner, float outer) {
        Minecraft client = Minecraft.getInstance();
        if (client.levelRenderer == null || client.level == null) {
            return;
        }
        inner = Math.max(0.0f, inner);
        outer = Math.max(inner, outer);
        int ox = origin.getX();
        int oz = origin.getZ();
        int pad = Mth.ceil(outer) + 16;
        int minSx = SectionPos.blockToSectionCoord(ox - pad);
        int maxSx = SectionPos.blockToSectionCoord(ox + pad);
        int minSz = SectionPos.blockToSectionCoord(oz - pad);
        int maxSz = SectionPos.blockToSectionCoord(oz + pad);
        int minSy = Math.max(SectionPos.blockToSectionCoord(client.level.getMinBuildHeight()),
                SectionPos.blockToSectionCoord(origin.getY() - Y_DOWN));
        int maxSy = Math.min(SectionPos.blockToSectionCoord(client.level.getMaxBuildHeight() - 1),
                SectionPos.blockToSectionCoord(origin.getY() + Y_UP));
        var chunks = client.level.getChunkSource();
        for (int sx = minSx; sx <= maxSx; sx++) {
            if (!anyChunkInColumnLoaded(chunks, sx, minSz, maxSz)) {
                continue;
            }
            int cx = SectionPos.sectionToBlockCoord(sx) + 8 - ox;
            for (int sz = minSz; sz <= maxSz; sz++) {
                if (!chunks.hasChunk(sx, sz)) {
                    continue;
                }
                int cz = SectionPos.sectionToBlockCoord(sz) + 8 - oz;
                float dist = (float) Math.sqrt(cx * (double) cx + cz * (double) cz);
                if (dist + 12.0f < inner || dist - 12.0f > outer) {
                    continue;
                }
                for (int sy = minSy; sy <= maxSy; sy++) {
                    client.levelRenderer.setSectionDirty(sx, sy, sz);
                }
            }
        }
    }

    private static boolean anyChunkInColumnLoaded(ClientChunkCache chunks, int sx, int minSz, int maxSz) {
        for (int sz = minSz; sz <= maxSz; sz++) {
            if (chunks.hasChunk(sx, sz)) {
                return true;
            }
        }
        return false;
    }

    private void spawnFallsOnWave(ClientLevel level) {
        if (falling.size() >= MAX_FALLING) {
            return;
        }
        float r = publishedCullRadius;
        if (Float.isNaN(r) || r > NEAR_ANIM_DIST + 3.0f) {
            return;
        }
        int y0 = origin.getY() - Y_DOWN;
        int y1 = origin.getY() + Y_UP;
        int samples = Mth.clamp((int) (r * 2.8f), 28, 96);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples && falling.size() < MAX_FALLING; i++) {
            double angle = (i + random.nextFloat()) * (Math.PI * 2.0 / samples);
            int x = origin.getX() + Mth.floor(Math.cos(angle) * r + (random.nextFloat() - 0.5f) * 2.2f);
            int z = origin.getZ() + Mth.floor(Math.sin(angle) * r + (random.nextFloat() - 0.5f) * 2.2f);
            for (int y = y0; y <= y1 && falling.size() < MAX_FALLING; y += 2) {
                cursor.set(x, y, z);
                if (!level.isLoaded(cursor)) {
                    continue;
                }
                BlockState state = level.getBlockState(cursor);
                if (!shouldCollapse(state)) {
                    continue;
                }
                beginFall(level, cursor.asLong(), state);
            }
        }
    }

    private void playLocal(ClientLevel level, BlockPos pos, float volume, float pitch) {
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.GLASS_BREAK,
                SoundSource.AMBIENT, volume, pitch, false);
    }

    private void tickCamera(Minecraft mc) {
        cameraFallPrevY = cameraFallY;
        if (restoring) {
            float t = Mth.clamp(restoreTicks / (float) RESTORE_DURATION_TICKS, 0.0f, 1.0f);
            float ease = 1.0f - (1.0f - t) * (1.0f - t);
            cameraFallY = Mth.lerp(ease, cameraReturnStartY, 0.0f);
            cameraFallVy = 0.0f;
            return;
        }
        if (!active || mc.player == null) {
            return;
        }
        if (!cameraFalling && isFloorCollapsed(mc.player)) {
            cameraFalling = true;
            controlLocked = true;
            cameraFallVy = -0.12f;
        }
        if (!cameraFalling) {
            return;
        }
        cameraFallVy -= CAMERA_GRAVITY;
        cameraFallY += cameraFallVy;
        if (cameraFallY < -MAX_CAMERA_FALL) {
            cameraFallY = -MAX_CAMERA_FALL;
            cameraFallVy = 0.0f;
            sunkToBottom = true;
        }
    }

    private boolean isFloorCollapsed(LocalPlayer player) {
        if (!player.onGround()) {
            return false;
        }
        BlockPos on = player.getOnPos();
        if (WAVE.hides(on.getX(), on.getZ())) {
            return true;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        AABB box = player.getBoundingBox();
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX - 1.0E-4);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ - 1.0E-4);
        int supportY = player.getOnPos().getY();
        boolean anyHidden = false;
        boolean anyVisibleSupport = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                cursor.set(x, supportY, z);
                BlockState state = level.getBlockState(cursor);
                if (state.isAir() || state.getCollisionShape(level, cursor).isEmpty()) {
                    continue;
                }
                if (shouldCollapse(state) && (hiddenWorking.contains(cursor.asLong()) || WAVE.hides(x, z))) {
                    anyHidden = true;
                } else {
                    anyVisibleSupport = true;
                }
            }
        }
        return anyHidden && !anyVisibleSupport;
    }

    private void closeInventoryIfLocked(Minecraft mc) {
        if (!controlLocked || mc.screen == null) {
            return;
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                || mc.screen instanceof io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen) {
            mc.setScreen(null);
        }
    }

    private void tickClient(Minecraft client) {
        if (postTransferRebuildTicks > 0) {
            postTransferRebuildTicks--;
            if (postTransferRebuildTicks == 50 || postTransferRebuildTicks == 20) {
                dirtyAroundPlayer();
            }
        }
        if (ignoreActivateTicks > 0) {
            ignoreActivateTicks--;
        }
        if (client.player == null || client.level == null) {
            if (active || restoring || hideAllowed) {
                abortForTransfer();
            }
            return;
        }
        if (boundLevel != null && boundLevel != client.level) {
            abortForTransfer();
        }
        Vec3 now = client.player.position();
        if ((active || restoring) && lastTrackedPos != null
                && lastTrackedPos.distanceToSqr(now) > TRANSFER_JUMP_DIST_SQ) {
            abortForTransfer();
            lastTrackedPos = now;
            return;
        }
        lastTrackedPos = now;
        boolean hasEffect = client.player.hasEffect(ModEffects.MIRROR_REUNION);
        if (hasEffect && !isActive() && ignoreActivateTicks <= 0) {
            activate();
        } else if (!hasEffect && isActive()) {
            deactivate();
        }
        if (!client.isPaused()) {
            tick();
        }
    }

    private void onChunkLoad(ChunkPos pos) {
        if (postTransferRebuildTicks > 0) {
            dirtyChunk(pos);
        }
    }

    private void dirtyAroundPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        float radius = mc.options.getEffectiveRenderDistance() * 16.0f + 16.0f;
        dirtyLoadedAround(mc.player.blockPosition(), radius);
    }

    private void dirtyLoadedAround(BlockPos center, float radius) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.levelRenderer == null) {
            return;
        }
        int viewChunks = Math.max(2, Mth.ceil(radius / 16.0f));
        int pcx = SectionPos.blockToSectionCoord(center.getX());
        int pcz = SectionPos.blockToSectionCoord(center.getZ());
        int minSy = Math.max(SectionPos.blockToSectionCoord(client.level.getMinBuildHeight()),
                SectionPos.blockToSectionCoord(center.getY() - Y_DOWN));
        int maxSy = Math.min(SectionPos.blockToSectionCoord(client.level.getMaxBuildHeight() - 1),
                SectionPos.blockToSectionCoord(center.getY() + Y_UP));
        ClientChunkCache chunks = client.level.getChunkSource();
        double radiusSq = (radius + 16.0f) * (radius + 16.0f);
        for (int sx = pcx - viewChunks; sx <= pcx + viewChunks; sx++) {
            for (int sz = pcz - viewChunks; sz <= pcz + viewChunks; sz++) {
                if (!chunks.hasChunk(sx, sz)) {
                    continue;
                }
                int cx = SectionPos.sectionToBlockCoord(sx) + 8 - center.getX();
                int cz = SectionPos.sectionToBlockCoord(sz) + 8 - center.getZ();
                if (cx * (double) cx + cz * (double) cz > radiusSq) {
                    continue;
                }
                for (int sy = minSy; sy <= maxSy; sy++) {
                    client.levelRenderer.setSectionDirty(sx, sy, sz);
                }
            }
        }
    }

    private void dirtyChunk(ChunkPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.levelRenderer == null || client.player == null) {
            return;
        }
        int minSy = Math.max(SectionPos.blockToSectionCoord(client.level.getMinBuildHeight()),
                SectionPos.blockToSectionCoord(client.player.blockPosition().getY() - Y_DOWN));
        int maxSy = Math.min(SectionPos.blockToSectionCoord(client.level.getMaxBuildHeight() - 1),
                SectionPos.blockToSectionCoord(client.player.blockPosition().getY() + Y_UP));
        for (int sy = minSy; sy <= maxSy; sy++) {
            client.levelRenderer.setSectionDirty(pos.x, sy, pos.z);
        }
    }

    private void resetCameraFall() {
        cameraFalling = false;
        sunkToBottom = false;
        controlLocked = false;
        cameraFallY = 0.0f;
        cameraFallPrevY = 0.0f;
        cameraFallVy = 0.0f;
        cameraReturnStartY = 0.0f;
    }

    private void clearFlags() {
        active = false;
        restoring = false;
        scanDone = false;
        collapseStartedSound = false;
        tickCounter = 0;
        orderSize = 0;
        orderCursor = 0;
        restoreCursor = 0;
        restoreTicks = 0;
        sortedCount = -1;
        origin = BlockPos.ZERO;
        falling.clear();
        rising.clear();
        packedPos.clear();
        seen.clear();
        dirtySections.clear();
        fallCells.clear();
        restoreStartRadius = 0.0f;
        WAVE = WaveCull.NONE;
        publishedCullRadius = Float.NaN;
        resetCameraFall();
    }

    private static final class WaveCull {
        private static final WaveCull NONE = new WaveCull(false, 0, 0, Float.POSITIVE_INFINITY);
        private final boolean enabled;
        private final int originX;
        private final int originZ;
        private final float radiusSq;

        private WaveCull(boolean enabled, int originX, int originZ, float radiusSq) {
            this.enabled = enabled;
            this.originX = originX;
            this.originZ = originZ;
            this.radiusSq = radiusSq;
        }

        private boolean hides(int x, int z) {
            if (!enabled) {
                return false;
            }
            int dx = x - originX;
            int dz = z - originZ;
            return dx * (float) dx + dz * (float) dz >= radiusSq;
        }
    }

    private static final class AnimPiece {
        private BlockState state;
        private long trackedPacked;
        private long cell;
        private boolean fadeOut;
        private double x, y, z;
        private double prevX, prevY, prevZ;
        private double vx, vy, vz;
        private double originY;
        private double startY;
        private float rotX, rotZ;
        private float prevRotX, prevRotZ;
        private float rotVelX, rotVelZ;
        private int age;
        private int light;
    }
}
