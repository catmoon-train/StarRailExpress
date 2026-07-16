package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.content.block_entity.SupplyBoxBlockEntity;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * 空投系统：探索区避难所返回门周围 200~300 格随机投放，3x3x3 结构含 4 个物资箱，
 * 落地后 60s 冒烟标记。由 {@code END_WORLD_TICK} 全局推进。
 */
public final class SixtySecondsAirdrop {
    private static final double FALL_SPEED = 0.5;
    private static final int DROP_HEIGHT = 40;
    private static final int DISTANCE_MIN = 200;
    private static final int DISTANCE_MAX = 300;
    private static final int HEIGHT_MATCH_TOLERANCE = 8;
    private static final int SMOKE_TICKS = 20 * 60; // 60 秒冒烟

    private static final List<Drop> DROPS = new ArrayList<>();
    /** 已落地的空投结构：中心坐标 → 剩余冒烟 tick。 */
    private static final Map<BlockPos, SmokeData> SMOKE = new HashMap<>();

    private SixtySecondsAirdrop() {}

    private static final class Drop {
        ResourceKey<Level> dimension;
        double x, y, z;
        int targetY;
    }

    private static final class SmokeData {
        int remaining;
        final BlockPos[] crateBoxes; // 四个物资箱位置，游戏结束时清除

        SmokeData(int remaining, BlockPos[] crateBoxes) {
            this.remaining = remaining;
            this.crateBoxes = crateBoxes;
        }
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SixtySecondsAirdrop::tick);
        // 信号枪：延迟空投推进（挂同一 tick，避免多注册一个回调点）
        ServerTickEvents.END_WORLD_TICK.register(
                net.exmo.sre.sixtyseconds.content.item.SixtySecondsFlareGunItem::tick);
    }

    // ── 投放 ────────────────────────────────────────────────────────────

    /** 指令投放（指定坐标）。 */
    public static boolean drop(ServerLevel level, int x, int z) {
        Integer ground = findGroundY(level, x, z);
        if (ground == null) return false;
        spawnDrop(level, x, z, ground);
        return true;
    }

    /**
     * 随机投放：选一个队伍的探索区返回门，在周围 300~699 格、高度匹配门高、
     * 不在房顶的随机位置投放。
     */
    public static boolean dropRandom(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        // 收集有返回门（探索区入口）的队伍
        List<SixtySecondsState.TeamData> candidates = new ArrayList<>();
        for (SixtySecondsState.TeamData t : data.teams.values()) {
            if (t.returnDoorPos != null) candidates.add(t);
        }
        if (candidates.isEmpty()) return false;

        for (int attempt = 0; attempt < 32; attempt++) {
            SixtySecondsState.TeamData team = candidates.get(level.random.nextInt(candidates.size()));
            BlockPos door = team.returnDoorPos;
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = DISTANCE_MIN + level.random.nextDouble() * (DISTANCE_MAX - DISTANCE_MIN);
            int x = door.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = door.getZ() + (int) Math.round(Math.sin(angle) * dist);

            // 高度匹配门高（±8 格内），避免山上/地下
            int top = Math.min(level.getMaxBuildHeight() - 1, door.getY() + HEIGHT_MATCH_TOLERANCE);
            int bottom = Math.max(level.getMinBuildHeight(), door.getY() - HEIGHT_MATCH_TOLERANCE);
            Integer ground = scanGround(level, x, z, top, bottom);
            if (ground == null) continue;

            // 防房顶：落点上方至少 5 格无实心方块（= 不是被房子盖住的窄巷/屋顶边缘）
            if (!hasClearSky(level, x, ground, z, 5)) continue;

            // 不在任何队伍住宅/避难所盒内
            boolean insideHome = false;
            for (SixtySecondsState.TeamData t : data.teams.values()) {
                if (inFootprint(t.shelterBox, x, z) || inFootprint(t.residentialBox, x, z)) {
                    insideHome = true;
                    break;
                }
            }
            if (insideHome) continue;

            spawnDrop(level, x, z, ground);
            return true;
        }
        return false;
    }

    // ── 下落动画 + 落地 ──────────────────────────────────────────────────

    private static void spawnDrop(ServerLevel level, int x, int z, int groundY) {
        Drop drop = new Drop();
        drop.dimension = level.dimension();
        drop.x = x + 0.5;
        drop.z = z + 0.5;
        drop.targetY = groundY;
        drop.y = groundY + DROP_HEIGHT;
        DROPS.add(drop);
        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.airdrop_incoming", x, z)
                .withStyle(ChatFormatting.GOLD));
        for (ServerPlayer p : level.players()) {
            p.playNotifySound(SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 0.8F, 0.6F);
        }
    }

    /** 落地：放置 3x3x3 结构（中心 1 + 四角 4 物资箱 × 2 层高），启 60s 冒烟。 */
    private static void land(ServerLevel level, BlockPos center) {
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        // ── 结构：3x3 底座 → 中心 1（空投残骸）+ N/S/E/W 4 角物资箱 × 2 层 ──
        // Layer 1（y=center）
        BlockPos[] crates = new BlockPos[4];
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int i = 0; i < 4; i++) {
            BlockPos boxPos = center.offset(offsets[i][0], 0, offsets[i][2]);
            level.setBlock(boxPos, org.agmas.noellesroles.init.ModBlocks
                    .SIXTY_SECONDS_SUPPLY_BOX.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(boxPos)
                    instanceof net.exmo.sre.sixtyseconds.content.block_entity.SupplyBoxBlockEntity be) {
                be.category = "airdrop";
                be.setChanged();
            }
            crates[i] = boxPos.immutable();
        }
        // 中心块：残骸
        level.setBlock(center, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(),
                Block.UPDATE_ALL);

        // Layer 2（y=center+1）：上层 4 角物资箱
        for (int i = 0; i < 4; i++) {
            BlockPos upPos = center.offset(offsets[i][0], 1, offsets[i][2]);
            level.setBlock(upPos, org.agmas.noellesroles.init.ModBlocks
                    .SIXTY_SECONDS_SUPPLY_BOX.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(upPos)
                    instanceof net.exmo.sre.sixtyseconds.content.block_entity.SupplyBoxBlockEntity be) {
                be.category = "airdrop";
                be.setChanged();
            }
        }

        // 落地特效
        level.sendParticles(ParticleTypes.EXPLOSION, center.getX() + 0.5, center.getY() + 0.5,
                center.getZ() + 0.5, 10, 0.8, 0.5, 0.8, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.getX() + 0.5, center.getY() + 1.5,
                center.getZ() + 0.5, 20, 0.5, 0.8, 0.5, 0.02);
        level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 0.8F, 1.2F);

        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.airdrop_landed",
                center.getX(), center.getY(), center.getZ()).withStyle(ChatFormatting.GREEN));

        SMOKE.put(center.immutable(), new SmokeData(SMOKE_TICKS, crates));
    }

    // ── 冒烟 tick ────────────────────────────────────────────────────────

    private static void tickSmoke(ServerLevel level) {
        for (Iterator<Map.Entry<BlockPos, SmokeData>> it = SMOKE.entrySet().iterator(); it.hasNext();) {
            Map.Entry<BlockPos, SmokeData> entry = it.next();
            BlockPos pos = entry.getKey();
            SmokeData data = entry.getValue();
            if (!level.dimension().location().equals(level.dimension().location()) ||
                    !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                // 跨维度/区块不在范围内，跳过（保留数据）
                continue;
            }
            // 冒烟：中央柱状 + 底座扩散
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.getX() + 0.5, pos.getY() + 2.5, pos.getZ() + 0.5,
                    3, 0.15, 0.9, 0.15, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5,
                    2, 0.5, 0.4, 0.5, 0.01);
            data.remaining--;
            if (data.remaining <= 0) {
                // 冒烟结束：可以在这里额外效果（如结构自毁），目前仅停止冒烟
                it.remove();
            }
        }
    }

    // ── 主 tick ──────────────────────────────────────────────────────────

    private static void tick(ServerLevel level) {
        // 下落中的空投
        for (Iterator<Drop> it = DROPS.iterator(); it.hasNext();) {
            Drop drop = it.next();
            if (!drop.dimension.equals(level.dimension())) continue;
            // 下落粒子
            level.sendParticles(ParticleTypes.CLOUD, drop.x, drop.y + 1.5, drop.z, 4, 0.4, 0.2, 0.4, 0.01);
            level.sendParticles(ParticleTypes.FIREWORK, drop.x, drop.y, drop.z, 2, 0.15, 0.3, 0.15, 0.02);
            if (level.getGameTime() % 20 == 0) {
                level.playSound(null, drop.x, drop.y, drop.z,
                        SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, 0.5F, 1.3F);
            }
            drop.y -= FALL_SPEED;
            if (drop.y > drop.targetY) continue;
            // 落地
            land(level, BlockPos.containing(drop.x, drop.targetY, drop.z));
            it.remove();
        }
        // 冒烟中
        tickSmoke(level);
    }

    // ── 工具方法 ─────────────────────────────────────────────────────────

    private static Integer findGroundY(ServerLevel level, int x, int z) {
        int top = level.getMaxBuildHeight() - 1;
        int bottom = level.getMinBuildHeight();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (SixtySecondsState.TeamData t : data.teams.values()) {
            if (t.searchZoneBox != null) {
                top = Math.min(top, (int) t.searchZoneBox.maxY);
                bottom = Math.max(bottom, (int) t.searchZoneBox.minY - 1);
                break;
            }
        }
        return scanGround(level, x, z, top, bottom);
    }

    private static Integer scanGround(ServerLevel level, int x, int z, int top, int bottom) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y > bottom; y--) {
            if (!level.getBlockState(pos.set(x, y, z)).isAir()
                    && level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
                return y + 1;
            }
        }
        return null;
    }

    /** 落点上方 len 格内是否都是非实心（= 开阔天空，非屋内/窄巷）。 */
    private static boolean hasClearSky(ServerLevel level, int x, int y, int z, int len) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < len; dy++) {
            BlockState s = level.getBlockState(pos.set(x, y + dy, z));
            if (!s.getCollisionShape(level, pos).isEmpty()) return false;
        }
        return true;
    }

    private static boolean inFootprint(AABB box, int x, int z) {
        return box != null && x + 0.5 >= box.minX && x + 0.5 <= box.maxX
                && z + 0.5 >= box.minZ && z + 0.5 <= box.maxZ;
    }

    private static void broadcast(ServerLevel level, Component msg) {
        for (ServerPlayer p : level.players()) p.displayClientMessage(msg, false);
    }

    /** 游戏结束时清除所有空投遗留结构和下落中的空投。 */
    public static void reset(ServerLevel level) {
        DROPS.clear();
        net.exmo.sre.sixtyseconds.content.item.SixtySecondsFlareGunItem.reset();
        for (Map.Entry<BlockPos, SmokeData> e : new HashMap<>(SMOKE).entrySet()) {
            BlockPos center = e.getKey();
            level.setBlock(center, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL);
            int[][] offs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] off : offs) {
                for (int dy = 0; dy < 2; dy++) {
                    level.setBlock(center.offset(off[0], dy, off[2]),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        SMOKE.clear();
    }

    /** 无参数重置（兼容旧调用——仅清掉下落队列）。 */
    public static void reset() {
        DROPS.clear();
    }
}
