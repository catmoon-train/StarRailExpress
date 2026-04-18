package org.agmas.noellesroles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 黑白狂暴前奏场景管理器（客户端侧）
 *
 * 药水生效时，大范围把客户端视野附近的实体方块替换为黑白混凝土，
 * 并在结束后渐进式还原。
 */
public class MonokumaSceneManager {
    public static final MonokumaSceneManager INSTANCE = new MonokumaSceneManager();

    private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
    private final List<Map.Entry<BlockPos, BlockState>> restoreQueue = new ArrayList<>();
    private final Random random = new Random();

    private static final BlockState BLACK_BLOCK = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState WHITE_BLOCK = Blocks.WHITE_CONCRETE.defaultBlockState();

    private static final int MAX_RADIUS = 96;
    private static final int EXPAND_PER_TICK = 5;
    private static final int EXPAND_INTERVAL = 2;
    private static final int START_REPLACE_BUDGET = 260;
    private static final int NORMAL_REPLACE_BUDGET = 520;
    private static final int FILL_RADIUS = 18;
    private static final int FILL_BUDGET = 420;
    private static final int PULSE_INTERVAL = 18;

    private boolean active = false;
    private boolean restoring = false;
    private int tickCounter = 0;
    private int pulseTimer = 0;
    private int currentRadius = 0;
    private int ringOffset = 0;
    private int restoreIndex = 0;
    private BlockPos lastPlayerPos = null;
    private Vec3 splitOrigin = Vec3.ZERO;
    private Vec3 splitRight = new Vec3(1.0, 0.0, 0.0);

    public void activate() {
        if (active) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            Vec3 eyePos = mc.player.position();
            Vec3 look = mc.player.getLookAngle();
            Vec3 horizontalLook = new Vec3(look.x, 0.0, look.z);
            if (horizontalLook.lengthSqr() < 1.0E-4) {
                horizontalLook = new Vec3(0.0, 0.0, 1.0);
            } else {
                horizontalLook = horizontalLook.normalize();
            }

            // 用玩家右手方向作为切面法线：相对于玩家视角形成稳定的左右黑白两半。
            this.splitRight = new Vec3(horizontalLook.z, 0.0, -horizontalLook.x).normalize();
            this.splitOrigin = new Vec3(
                    Math.floor(eyePos.x) + 0.5,
                    Math.floor(eyePos.y),
                    Math.floor(eyePos.z) + 0.5);
        }

        active = true;
        restoring = false;
        tickCounter = 0;
        pulseTimer = 0;
        currentRadius = 0;
        ringOffset = 0;
        restoreQueue.clear();
        restoreIndex = 0;
    }

    public void deactivate() {
        if (!active) {
            return;
        }
        active = false;
        startRestoration();
    }

    public boolean isActive() {
        return active;
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
    }

    private void tickActive(Minecraft mc) {
        tickCounter++;
        pulseTimer++;

        BlockPos playerPos = mc.player.blockPosition();
        lastPlayerPos = playerPos;

        int replaceBudget = tickCounter <= 40 ? START_REPLACE_BUDGET : NORMAL_REPLACE_BUDGET;
        if (tickCounter % EXPAND_INTERVAL == 0 && currentRadius < MAX_RADIUS) {
            int fromRadius = Math.max(0, currentRadius - ringOffset);
            int toRadius = Math.min(MAX_RADIUS, currentRadius + EXPAND_PER_TICK - ringOffset);
            expandToRadius(mc.level, playerPos, fromRadius, toRadius, replaceBudget);
            currentRadius += EXPAND_PER_TICK;

            if (tickCounter > 20) {
                ringOffset = Math.min(28, ringOffset + 1);
            }
        }

        // 每 tick 对玩家周围做一次实时补刷，补上扩散预算没有覆盖到的完整方块。
        fillNearbyArea(mc.level, playerPos, FILL_RADIUS, FILL_BUDGET);

        if (pulseTimer >= PULSE_INTERVAL) {
            pulseTimer = 0;
            pulseExistingBlocks(mc.level);
        }
    }

    private void expandToRadius(ClientLevel level, BlockPos center, int fromRadius, int toRadius, int replaceBudget) {
        if (replaceBudget <= 0) {
            return;
        }
        int replaced = 0;

        for (int r = fromRadius; r < toRadius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int y = -8; y <= 20; y++) {
                    for (int z = -r; z <= r; z++) {
                        if (replaced >= replaceBudget) {
                            return;
                        }
                        if (Math.abs(x) != r && Math.abs(z) != r) {
                            continue;
                        }

                        BlockPos pos = center.offset(x, y, z);
                        if (originalBlocks.containsKey(pos)) {
                            continue;
                        }

                        BlockState state = level.getBlockState(pos);
                        if (!shouldReplace(level, pos, state)) {
                            continue;
                        }

                        originalBlocks.put(pos.immutable(), state);
                        BlockState replacement = chooseReplacement(pos);
                        level.setBlock(pos, replacement, 3);
                        replaced++;

                        if (random.nextFloat() < 0.18f) {
                            double px = pos.getX() + 0.5;
                            double py = pos.getY() + 0.6;
                            double pz = pos.getZ() + 0.5;
                            level.addParticle(ParticleTypes.ASH, px, py, pz, 0.0, 0.02, 0.0);
                            if (random.nextFloat() < 0.35f) {
                                level.addParticle(ParticleTypes.END_ROD, px, py + 0.2, pz, 0.0, 0.03, 0.0);
                            }
                        }
                    }
                }
            }
        }
    }

    private void fillNearbyArea(ClientLevel level, BlockPos center, int radius, int replaceBudget) {
        int replaced = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -8; y <= 20; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (replaced >= replaceBudget) {
                        return;
                    }

                    BlockPos pos = center.offset(x, y, z);
                    if (originalBlocks.containsKey(pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (!shouldReplace(level, pos, state)) {
                        continue;
                    }

                    originalBlocks.put(pos.immutable(), state);
                    level.setBlock(pos, chooseReplacement(pos), 3);
                    replaced++;
                }
            }
        }
    }

    private boolean shouldReplace(ClientLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        // 只替换完整碰撞方块，避免楼梯、台阶、栏杆等不完整方块被替换。
        if (!state.isCollisionShapeFullBlock(level, pos)) {
            return false;
        }
        return true;
    }

    private BlockState chooseReplacement(BlockPos pos) {
        Vec3 current = new Vec3(pos.getX() + 0.5, splitOrigin.y, pos.getZ() + 0.5);
        double side = current.subtract(splitOrigin).dot(splitRight);

        // 保留一条很窄的分界带，避免边界抖动导致花屏。
        if (Math.abs(side) < 1.0) {
            return WHITE_BLOCK;
        }
        return side < 0.0 ? BLACK_BLOCK : WHITE_BLOCK;
    }

    private void pulseExistingBlocks(ClientLevel level) {
        if (originalBlocks.isEmpty()) {
            return;
        }
        List<BlockPos> positions = new ArrayList<>(originalBlocks.keySet());
        int pulseCount = Math.min(positions.size() / 4, 140);

        for (int i = 0; i < pulseCount; i++) {
            BlockPos pos = positions.get(random.nextInt(positions.size()));
            if (!level.isLoaded(pos)) {
                continue;
            }
            level.setBlock(pos, chooseReplacement(pos), 3);
            if (random.nextFloat() < 0.1f) {
                double px = pos.getX() + 0.5;
                double py = pos.getY() + 1.0;
                double pz = pos.getZ() + 0.5;
                level.addParticle(ParticleTypes.CLOUD, px, py, pz, 0.0, 0.03, 0.0);
            }
        }
    }

    private void startRestoration() {
        restoring = true;
        restoreQueue.clear();
        restoreQueue.addAll(originalBlocks.entrySet());
        if (lastPlayerPos != null) {
            BlockPos center = lastPlayerPos;
            restoreQueue.sort((a, b) -> Double.compare(b.getKey().distSqr(center), a.getKey().distSqr(center)));
        }
        restoreIndex = 0;
    }

    private void tickRestore(Minecraft mc) {
        if (restoreIndex >= restoreQueue.size()) {
            restoring = false;
            originalBlocks.clear();
            restoreQueue.clear();
            currentRadius = 0;
            return;
        }

        ClientLevel level = mc.level;
        if (level == null) {
            restoring = false;
            originalBlocks.clear();
            return;
        }

        int batchSize = Math.min(36, restoreQueue.size() - restoreIndex);
        for (int i = 0; i < batchSize; i++) {
            var entry = restoreQueue.get(restoreIndex + i);
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            if (level.isLoaded(pos)) {
                level.setBlock(pos, original, 3);
                if (random.nextFloat() < 0.12f) {
                    level.addParticle(ParticleTypes.END_ROD,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            0.0, 0.03, 0.0);
                }
            }
        }
        restoreIndex += batchSize;
    }

    public void forceRestore() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            for (var entry : originalBlocks.entrySet()) {
                if (mc.level.isLoaded(entry.getKey())) {
                    mc.level.setBlock(entry.getKey(), entry.getValue(), 3);
                }
            }
        }
        originalBlocks.clear();
        restoreQueue.clear();
        restoring = false;
        active = false;
        currentRadius = 0;
        restoreIndex = 0;
        splitOrigin = Vec3.ZERO;
        splitRight = new Vec3(1.0, 0.0, 0.0);
    }

    public void reset() {
        forceRestore();
    }
}
