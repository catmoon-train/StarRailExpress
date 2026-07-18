package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.entity.OceanSeaMonsterEntity;
import net.exmo.sre.sixtyseconds.entity.OceanSharkEntity;
import net.exmo.sre.sixtyseconds.init.ModOceanEntities;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.MobSpawnType;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;

/**
 * 海洋生物自然刷新系统：海上（非岸边）对搜索区玩家定期判定，刷出鲨鱼和海怪。
 *
 * <h3>难度按天数比例递增</h3>
 * <p>基础概率 × dayRatio = 当前概率。dayRatio = currentDay / totalDays。
 * 前四天有额外压降（dayRatio × 0.3），保证前几天几乎不刷强怪。
 * 例如 7 天局：d1=0.043, d2=0.086, d3=0.129, d4=0.171, d5=0.714, d6=0.857, d7=1.0
 *
 * <h3>利维坦不自然刷新</h3>
 * 只能通过 {@code /sre:ocean spawn monster leviathan} 指令召唤。
 *
 * <h3>海怪出场特效</h3>
 * <ul>
 *   <li>全服金色播报</li>
 *   <li>附近玩家获得 {@code VISION_FOG} 浓雾效果（8s）</li>
 *   <li>深海守卫号角音效</li>
 *   <li>海浪粒子爆发</li>
 * </ul>
 */
public final class OceanCreatureSpawner {

    public static final int CHECK_INTERVAL = 20 * 14;

    private static final int MAX_NEARBY_SHARKS = 5;
    private static final int MAX_NEARBY_MONSTERS = 2;
    private static final double NEARBY_RADIUS = 64.0;
    private static final double MONSTER_FOG_RADIUS = 32.0; // 海怪浓雾作用半径

    private static final int SPAWN_MIN_DIST = 14;
    private static final int SPAWN_MAX_DIST = 36;

    private OceanCreatureSpawner() {}

    /**
     * 对所有在线存活探索中玩家做海洋生物刷新判定。
     * 刷新概率 = 基础概率 × dayRatio（前四天额外 ×0.3）。
     */
    public static void tick(ServerLevel level) {
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElse(null);
        if (config == null || !config.oceanCreaturesEnabled) return;

        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data == null || data.dayNumber <= 0) return;

        // ── 天数比例：dayRatio = currentDay / totalDays ─────────────
        double dayRatio = (double) data.dayNumber / Math.max(1, config.totalDays);
        boolean night = level.isNight();

        // 前四天额外压降 ×0.3（保证前几天几乎不刷强怪）
        double earlyDayMult = data.dayNumber <= 4 ? 0.3 : 1.0;
        // 怪物刷新频率+40%：鲨鱼/海怪基础概率 ×1.4
        double sharkBase = (night ? 0.28 : 0.105) * dayRatio * earlyDayMult;
        double monsterBase = (night ? 0.042 : 0.007) * dayRatio * earlyDayMult;

        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative()
                    || !io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            if (!net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones.isInSearchZone(player)) {
                continue;
            }
            if (!isNearOpenWater(level, player.blockPosition())) {
                continue;
            }

            int nearbySharks = countNearby(level, player, OceanSharkEntity.class, NEARBY_RADIUS);
            int nearbyMonsters = countNearby(level, player, OceanSeaMonsterEntity.class, NEARBY_RADIUS);

            // ── 鲨鱼刷新 ──────────────────────────────────────────
            if (nearbySharks < MAX_NEARBY_SHARKS && random.nextDouble() < sharkBase) {
                BlockPos spot = findWaterSpot(level, player.blockPosition(),
                        SPAWN_MIN_DIST, SPAWN_MAX_DIST, random);
                if (spot != null) {
                    spawnShark(level, spot, random, dayRatio);
                }
            }

            // ── 海怪刷新 ──────────────────────────────────────────
            if (nearbyMonsters < MAX_NEARBY_MONSTERS && random.nextDouble() < monsterBase) {
                BlockPos spot = findWaterSpot(level, player.blockPosition(),
                        SPAWN_MIN_DIST + 8, SPAWN_MAX_DIST + 12, random);
                if (spot != null) {
                    OceanSeaMonsterEntity monster = spawnSeaMonster(level, spot, random, dayRatio);
                    if (monster != null) {
                        announceSeaMonster(level, monster, player);
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  鲨鱼生成
    // ══════════════════════════════════════════════════════════════

    @Nullable
    public static OceanSharkEntity spawnShark(ServerLevel level, BlockPos waterPos,
            RandomSource random, double dayRatio) {
        OceanSharkEntity shark = ModOceanEntities.OCEAN_SHARK.create(level);
        if (shark == null) return null;
        shark.moveTo(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5,
                random.nextFloat() * 360.0F, 0.0F);

        // 变体概率随天数提升：前期只有小鲨，后期有大白鲨
        OceanSharkEntity.Variant variant;
        float r = random.nextFloat();
        if (r < 0.04 * dayRatio) {
            variant = OceanSharkEntity.Variant.MEGALODON; // 极低概率，随天数上升
        } else if (r < 0.07 + 0.12 * dayRatio) {
            variant = OceanSharkEntity.Variant.GREAT_WHITE;
        } else if (r < 0.18 + 0.20 * dayRatio) {
            variant = OceanSharkEntity.Variant.HAMMERHEAD;
        } else if (r < 0.40 + 0.20 * dayRatio) {
            variant = OceanSharkEntity.Variant.TIGER_SHARK;
        } else {
            variant = OceanSharkEntity.Variant.REEF_SHARK;
        }
        shark.applyVariant(variant);
        shark.finalizeSpawn(level, level.getCurrentDifficultyAt(waterPos),
                MobSpawnType.NATURAL, null);
        level.addFreshEntity(shark);
        return shark;
    }

    // ══════════════════════════════════════════════════════════════
    //  海怪生成（利维坦不自然刷新）
    // ══════════════════════════════════════════════════════════════

    @Nullable
    public static OceanSeaMonsterEntity spawnSeaMonster(ServerLevel level, BlockPos waterPos,
            RandomSource random, double dayRatio) {
        OceanSeaMonsterEntity monster = ModOceanEntities.OCEAN_SEA_MONSTER.create(level);
        if (monster == null) return null;
        monster.moveTo(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5,
                random.nextFloat() * 360.0F, 0.0F);

        // 利维坦不自然刷新 —— 只由指令生成
        OceanSeaMonsterEntity.Variant variant;
        float r = random.nextFloat();
        if (r < 0.20 + 0.15 * dayRatio) {
            variant = OceanSeaMonsterEntity.Variant.SERPENT;
        } else {
            variant = OceanSeaMonsterEntity.Variant.KRAKEN;
        }
        monster.applyVariant(variant);
        monster.finalizeSpawn(level, level.getCurrentDifficultyAt(waterPos),
                MobSpawnType.NATURAL, null);
        level.addFreshEntity(monster);
        return monster;
    }

    // ══════════════════════════════════════════════════════════════
    //  海怪出场特效：浓雾 + 音乐 + 全图播报 + 文字提醒
    // ══════════════════════════════════════════════════════════════

    private static void announceSeaMonster(ServerLevel level, OceanSeaMonsterEntity monster,
            ServerPlayer triggeringPlayer) {
        String variantKey = monster.getVariant().nameKey();

        // ① 全服金色播报
        Component globalMsg = Component.translatable(
                "message.noellesroles.ocean.monster_spawned",
                Component.translatable(variantKey))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        level.getServer().getPlayerList().broadcastSystemMessage(globalMsg, false);

        // ② 触发玩家个人文字警告（醒目红色）
        triggeringPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ocean.monster_sighted")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        triggeringPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.ocean.monster_warning")
                        .withStyle(ChatFormatting.DARK_RED), false);

        // ③ 浓雾：给附近所有玩家套 VISION_FOG（level=0 = 2格视野，8秒）
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator() && p.distanceToSqr(monster) < MONSTER_FOG_RADIUS * MONSTER_FOG_RADIUS) {
                // VISION_FOG amplifier 0 → 雾距 2 格（最强），时长 8 秒
                p.addEffect(new MobEffectInstance(ModEffects.VISION_FOG, 20 * 8, 0,
                        false, true, true));
                p.displayClientMessage(
                        Component.translatable("message.noellesroles.ocean.fog_warning")
                                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            }
        }

        // ④ 吓人音效：对触发玩家播深海守卫警告
        triggeringPlayer.playNotifySound(SoundEvents.ELDER_GUARDIAN_AMBIENT,
                SoundSource.HOSTILE, 1.0F, 0.6F);
        // 第二声音效延迟（深沉的号角）
        level.getServer().tell(new net.minecraft.server.TickTask(20, () -> {
            if (monster.isAlive()) {
                for (ServerPlayer p : level.players()) {
                    if (!p.isSpectator() && p.distanceToSqr(monster) < 48.0 * 48.0) {
                        p.playNotifySound(SoundEvents.WARDEN_NEARBY_CLOSE,
                                SoundSource.HOSTILE, 0.4F, 0.3F);
                    }
                }
            }
        }));

        // ⑤ 海浪粒子爆发（在怪物位置）
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE_COLUMN_UP,
                monster.getX(), monster.getY() + 2, monster.getZ(),
                40, 3.0, 1.5, 3.0, 0.05);
    }

    // ══════════════════════════════════════════════════════════════
    //  水域检测工具
    // ══════════════════════════════════════════════════════════════

    private static boolean isNearOpenWater(ServerLevel level, BlockPos center) {
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                BlockPos check = center.offset(dx * 8, 0, dz * 8);
                if (!level.hasChunkAt(check)) continue;
                BlockPos surface = findWaterSurface(level, check);
                if (surface != null) return true;
            }
        }
        return false;
    }

    @Nullable
    public static BlockPos findWaterSpot(ServerLevel level, BlockPos near,
            int minDist, int maxDist, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = minDist + random.nextDouble() * (maxDist - minDist);
            int x = near.getX() + (int) (Math.cos(angle) * dist);
            int z = near.getZ() + (int) (Math.sin(angle) * dist);
            if (!level.hasChunkAt(new BlockPos(x, near.getY(), z))) continue;
            BlockPos surface = findWaterSurface(level, new BlockPos(x, near.getY(), z));
            if (surface != null) return surface;
        }
        return null;
    }

    @Nullable
    private static BlockPos findWaterSurface(ServerLevel level, BlockPos column) {
        int startY = column.getY() + 6;
        int endY = column.getY() - 8;
        for (int y = startY; y >= endY; y--) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            if (!level.getFluidState(pos).is(FluidTags.WATER)) continue;
            boolean surface = level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.above(2)).isAir();
            boolean deep = level.getFluidState(pos.below()).is(FluidTags.WATER)
                    && level.getFluidState(pos.below(2)).is(FluidTags.WATER)
                    && level.getFluidState(pos.below(3)).is(FluidTags.WATER);
            if (surface && deep) return pos;
            break;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.entity.Entity> int countNearby(
            ServerLevel level, ServerPlayer player, Class<T> clazz, double radius) {
        int count = 0;
        for (T e : level.getEntitiesOfClass(clazz, player.getBoundingBox().inflate(radius))) {
            count++;
        }
        return count;
    }
}
