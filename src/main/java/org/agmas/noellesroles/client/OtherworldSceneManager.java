package org.agmas.noellesroles.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 里世界场景变化管理器（客户端侧）
 *
 * 比 StupidExpressClient 的 WEAVING 更高级：
 * - 渐进式扩散替换，从玩家位置逐圈向外
 * - 使用多种暗色方块（灵魂沙、黑石、深板岩等）
 * - 支持完全还原（记录原始方块状态）
 * - 周期性脉动：已替换方块会随时间变化
 */
public class OtherworldSceneManager {
    public static final OtherworldSceneManager INSTANCE = new OtherworldSceneManager();

    /** 已替换方块的原始状态缓存 */
    private final Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();

    /** 当前替换半径 */
    private int currentRadius = 0;

    /** 最大替换半径 */
    private static final int MAX_RADIUS = 80;

    /** 每次tick扩展的层数 */
    private static final int EXPAND_PER_TICK = 4;

    /** 开场快速扩散持续（tick）- 1秒 */
    private static final int RAPID_EXPAND_DURATION = 20;

    /** 开场快速扩散倍率 */
    private static final int RAPID_EXPAND_MULTIPLIER = 4;

    /** 扩展间隔（tick） */
    private static final int EXPAND_INTERVAL = 2;

    /** 替换概率 */
    private static final float REPLACE_CHANCE = 0.75f;

    /** 脉动间隔（tick） */
    private static final int PULSE_INTERVAL = 30;

    /** 是否正在活动 */
    private boolean active = false;

    /** 计时器 */
    private int tickCounter = 0;

    /** 脉动计时器 */
    private int pulseTimer = 0;

    /** 阶段化扩散环偏移（开场后继续一圈圈展开） */
    private int ringOffset = 0;

    /** 上一次的玩家位置 */
    private BlockPos lastPlayerPos = null;

    /** 是否正在还原中 */
    private boolean restoring = false;

    /** 还原队列 */
    private final List<Map.Entry<BlockPos, BlockState>> restoreQueue = new ArrayList<>();
    private int restoreIndex = 0;

    private final Random random = new Random();

    /** 里世界替换方块集合（暗色系） */
    private static final BlockState[] OTHERWORLD_BLOCKS = {
        // Blocks.SOUL_SAND.defaultBlockState(),//不完整方块会卡人
        Blocks.SOUL_SOIL.defaultBlockState(),
        Blocks.BLACKSTONE.defaultBlockState(),
        Blocks.GILDED_BLACKSTONE.defaultBlockState(),
        Blocks.DEEPSLATE.defaultBlockState(),
        Blocks.CRYING_OBSIDIAN.defaultBlockState(),
        Blocks.SCULK.defaultBlockState(),
        Blocks.BASALT.defaultBlockState(),
        Blocks.POLISHED_BLACKSTONE.defaultBlockState(),
        Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
        Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
        Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
        Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
        Blocks.NETHER_BRICKS.defaultBlockState(),
        Blocks.RED_NETHER_BRICKS.defaultBlockState(),
        Blocks.TUFF.defaultBlockState(),
    };

    /** 稀有替换方块（低概率出现，发光/氛围方块） */
    private static final BlockState[] RARE_OTHERWORLD_BLOCKS = {
        Blocks.SCULK_CATALYST.defaultBlockState(),
        // Blocks.SCULK_SENSOR.defaultBlockState(), //这个会把人卡住
        // Blocks.SCULK_SHRIEKER.defaultBlockState(),不完整方块会卡人
        // Blocks.SOUL_LANTERN.defaultBlockState(),不完整方块会卡人
        Blocks.SHROOMLIGHT.defaultBlockState(),
        Blocks.JACK_O_LANTERN.defaultBlockState(),
        Blocks.REDSTONE_BLOCK.defaultBlockState(),
        Blocks.MAGMA_BLOCK.defaultBlockState(),
    };

    /**
     * 激活里世界场景变化
     */
    public void activate() {
        if (active) return;
        active = true;
        restoring = false;
        currentRadius = 0;
        ringOffset = 0;
        tickCounter = 0;
        pulseTimer = 0;
        restoreQueue.clear();
        restoreIndex = 0;
    }

    /**
     * 停用里世界场景变化并开始还原
     */
    public void deactivate() {
        if (!active) return;
        active = false;
        startRestoration();
    }

    /**
     * 每tick调用
     */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

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

        // 前1秒：快速扩散（半径*4）
        int expandStep = EXPAND_PER_TICK;
        if (tickCounter <= RAPID_EXPAND_DURATION) {
            expandStep *= RAPID_EXPAND_MULTIPLIER;
        }

        // 开场后：围绕玩家逐圈展开（ringOffset 让圈层有明显层级感）
        if (tickCounter % EXPAND_INTERVAL == 0 && currentRadius < MAX_RADIUS) {
            int fromRadius = Math.max(0, currentRadius - ringOffset);
            int toRadius = Math.min(MAX_RADIUS, currentRadius + expandStep - ringOffset);
            expandToRadius(mc.level, playerPos, fromRadius, toRadius);
            currentRadius += expandStep;

            if (tickCounter > RAPID_EXPAND_DURATION) {
                ringOffset = Math.min(24, ringOffset + 1);
            }
        }

        // 脉动效果：已替换的方块周期性变化
        if (pulseTimer >= PULSE_INTERVAL) {
            pulseTimer = 0;
            pulseExistingBlocks(mc.level);
        }
    }

    /**
     * 扩展替换范围
     */
    private void expandToRadius(ClientLevel level, BlockPos center, int fromRadius, int toRadius) {
        for (int r = fromRadius; r < toRadius; r++) {
            for (int x = -r; x <= r; x++) {
                for (int y = -3; y <= 10; y++) {
                    for (int z = -r; z <= r; z++) {
                        // 只处理当前圈层边界上的方块
                        if (Math.abs(x) != r && Math.abs(z) != r) continue;

                        BlockPos pos = center.offset(x, y, z);

                        // 已经替换过的跳过
                        if (originalBlocks.containsKey(pos)) continue;

                        BlockState state = level.getBlockState(pos);

                        // 跳过空气和流体
                        if (state.isAir()) continue;
                        if (!state.getFluidState().isEmpty()) continue;

                        // 不替换不可见方块
                        if (!state.isSolidRender(level, pos)) continue;

                        // 概率替换
                        if (random.nextFloat() >= REPLACE_CHANCE) continue;

                        // 记录原始方块
                        originalBlocks.put(pos.immutable(), state);

                        // 随机选择替换方块
                        BlockState replacement;
                        if (random.nextFloat() < 0.12f) {
                            replacement = RARE_OTHERWORLD_BLOCKS[random.nextInt(RARE_OTHERWORLD_BLOCKS.length)];
                        } else {
                            replacement = OTHERWORLD_BLOCKS[random.nextInt(OTHERWORLD_BLOCKS.length)];
                        }

                        level.setBlock(pos, replacement, 3);

                        // 替换时释放粒子效果
                        if (random.nextFloat() < 0.3f) {
                            double px = pos.getX() + 0.5;
                            double py = pos.getY() + 0.5;
                            double pz = pos.getZ() + 0.5;
                            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, px, py, pz, 0, 0.05, 0);
                            if (random.nextFloat() < 0.2f) {
                                level.addParticle(ParticleTypes.SCULK_CHARGE_POP, px, py + 0.3, pz, 0, 0.02, 0);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 脉动效果：随机改变一些已替换的方块
     */
    private void pulseExistingBlocks(ClientLevel level) {
        List<BlockPos> positions = new ArrayList<>(originalBlocks.keySet());
        int pulseCount = Math.min(positions.size() / 3, 80);

        for (int i = 0; i < pulseCount; i++) {
            BlockPos pos = positions.get(random.nextInt(positions.size()));
            if (!level.isLoaded(pos)) continue;

            BlockState replacement;
            if (random.nextFloat() < 0.08f) {
                replacement = RARE_OTHERWORLD_BLOCKS[random.nextInt(RARE_OTHERWORLD_BLOCKS.length)];
            } else {
                replacement = OTHERWORLD_BLOCKS[random.nextInt(OTHERWORLD_BLOCKS.length)];
            }
            level.setBlock(pos, replacement, 3);

            // 脉动时的粒子效果
            if (random.nextFloat() < 0.15f) {
                double px = pos.getX() + 0.5;
                double py = pos.getY() + 1.0;
                double pz = pos.getZ() + 0.5;
                level.addParticle(ParticleTypes.SOUL, px, py, pz,
                        (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
            }
        }
    }

    /**
     * 开始还原过程（渐进式）
     */
    private void startRestoration() {
        restoring = true;
        restoreQueue.clear();
        restoreQueue.addAll(originalBlocks.entrySet());
        // 从外到内还原
        if (lastPlayerPos != null) {
            BlockPos center = lastPlayerPos;
            restoreQueue.sort((a, b) -> {
                double distA = a.getKey().distSqr(center);
                double distB = b.getKey().distSqr(center);
                return Double.compare(distB, distA); // 远的先还原
            });
        }
        restoreIndex = 0;
    }

    /**
     * 还原tick：每tick还原一批方块
     */
    private void tickRestore(Minecraft mc) {
        if (restoreIndex >= restoreQueue.size()) {
            // 还原完毕
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

        // 每tick还原 30 个方块
        int batchSize = Math.min(30, restoreQueue.size() - restoreIndex);
        for (int i = 0; i < batchSize; i++) {
            var entry = restoreQueue.get(restoreIndex + i);
            BlockPos pos = entry.getKey();
            BlockState original = entry.getValue();
            if (level.isLoaded(pos)) {
                level.setBlock(pos, original, 3);
                // 还原时释放恢复粒子
                if (random.nextFloat() < 0.15f) {
                    double px = pos.getX() + 0.5;
                    double py = pos.getY() + 0.5;
                    double pz = pos.getZ() + 0.5;
                    level.addParticle(ParticleTypes.END_ROD, px, py, pz, 0, 0.05, 0);
                }
            }
        }
        restoreIndex += batchSize;
    }

    /**
     * 强制立即还原所有方块
     */
    public void forceRestore() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (var entry : originalBlocks.entrySet()) {
            if (mc.level.isLoaded(entry.getKey())) {
                mc.level.setBlock(entry.getKey(), entry.getValue(), 3);
            }
        }
        originalBlocks.clear();
        restoreQueue.clear();
        restoring = false;
        active = false;
        currentRadius = 0;
    }

    /**
     * 清理所有状态
     */
    public void reset() {
        forceRestore();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isRestoring() {
        return restoring;
    }
}
