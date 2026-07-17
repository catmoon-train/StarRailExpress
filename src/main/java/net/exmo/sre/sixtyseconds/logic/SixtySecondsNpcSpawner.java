package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * NPC 生成（纯生成逻辑，调度在 {@link SixtySecondsNpcSystem}）。覆盖计划里的 5 条生成路径：
 * <ol>
 *   <li>创造手动放置（{@code SixtySecondsConfig.npcSpawns}）→ {@link #spawnConfigured}</li>
 *   <li>每日随机刷在搜刮区 → {@link #spawnDaily}</li>
 *   <li>每日事件（流浪商人来访 / 强盗团夜袭）→ {@link #spawnAt} / {@link #spawnAssaultBandits}</li>
 *   <li>夜袭混入强盗 → {@link #spawnAssaultBandits}</li>
 *   <li>搜刮区绑定门门口概率刷 → {@link #spawnAtDoors}</li>
 * </ol>
 */
public final class SixtySecondsNpcSpawner {
    private SixtySecondsNpcSpawner() {
    }

    /** 通用生成入口：在 pos 造一只 NPC 并装配变体/朝向/驻守/归属队。 */
    public static SixtySecondsNpcEntity spawnAt(ServerLevel level, BlockPos pos,
            SixtySecondsNpcEntity.Variant variant, float yaw, String profile, int garrisonRadius,
            int ownerTeamId) {
        SixtySecondsNpcEntity npc = ModEntities.SIXTY_SECONDS_NPC.create(level);
        if (npc == null) {
            return null;
        }
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        npc.setYHeadRot(yaw);
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        npc.applyVariant(variant);
        npc.setShopProfile(profile == null ? "default" : profile);
        npc.setOwnerTeamId(ownerTeamId);
        // 商人/军人站桩：驻守在生成点附近（强盗/旅者自由游荡）
        if (variant == SixtySecondsNpcEntity.Variant.MERCHANT
                || variant == SixtySecondsNpcEntity.Variant.SOLDIER) {
            npc.setGarrison(pos, Math.max(2, garrisonRadius));
        }
        if (variant == SixtySecondsNpcEntity.Variant.TRAVELER) {
            fillCarry(level, npc);
        }
        level.addFreshEntity(npc);
        return npc;
    }

    /**
     * 搭图预览生成（NPC 放置器 / {@code /sre:60s npc} 指令）：立在登记点当立牌，
     * 模式外不自毁、不自散、不参与局内逻辑；开局由 {@link #spawnConfigured} 清掉并按配置重生成。
     */
    public static SixtySecondsNpcEntity spawnPreview(ServerLevel level, BlockPos pos,
            SixtySecondsNpcEntity.Variant variant, float yaw, String profile, int garrisonRadius) {
        SixtySecondsNpcEntity npc = spawnAt(level, pos, variant, yaw, profile, garrisonRadius, -1);
        if (npc != null) {
            npc.setEditorPreview(true);
        }
        return npc;
    }

    /**
     * 清掉全世界的搭图预览 NPC。开局前调用（{@link #spawnConfigured}）——预览会按 config.npcSpawns
     * 重新生成为正式 NPC，不清就会和正式的重成两份。
     *
     * @return 清掉的数量
     */
    public static int clearPreviews(ServerLevel level) {
        // 先收集再删除：遍历 getAllEntities() 途中 discard 会并发修改实体存储
        List<SixtySecondsNpcEntity> previews = new ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof SixtySecondsNpcEntity npc && npc.isEditorPreview()) {
                previews.add(npc);
            }
        }
        for (SixtySecondsNpcEntity npc : previews) {
            npc.discard();
        }
        return previews.size();
    }

    /** 旅者随身物资（被偷抽一格、被杀全掉）：2~4 件常见物资。 */
    private static void fillCarry(ServerLevel level, SixtySecondsNpcEntity npc) {
        RandomSource random = level.getRandom();
        int count = 2 + random.nextInt(3);
        for (int i = 0; i < count && i < npc.getCarry().size(); i++) {
            npc.getCarry().set(i, randomCarryItem(random));
        }
    }

    private static ItemStack randomCarryItem(RandomSource random) {
        return switch (random.nextInt(5)) {
            case 0 -> new ItemStack(ModItems.SIXTY_SECONDS_CANNED_FOOD, 1);
            case 1 -> new ItemStack(ModItems.SIXTY_SECONDS_WATER_SMALL, 1);
            case 2 -> new ItemStack(ModItems.SIXTY_SECONDS_BANDAGE, 1);
            case 3 -> new ItemStack(ModItems.SIXTY_SECONDS_SCRAP, 2 + random.nextInt(3));
            default -> new ItemStack(ModItems.SIXTY_SECONDS_COIN, 1 + random.nextInt(4));
        };
    }

    // ── 路径 1：按配置的手动放置点生成（开局第一天） ─────────────────────────

    /**
     * 按 {@code config.npcSpawns} 生成：点落在住宅/避难所模板盒内 → <b>每队各克隆一份</b>
     * （模板相对偏移 + 队伍网格偏移，与 {@code SixtySecondsArena.spawnFor} 的换算一致）；
     * 否则（搜索区/野外）→ <b>只生成一份</b>（全队共用，不克隆）。
     */
    public static void spawnConfigured(ServerLevel level, SixtySecondsState.Data data) {
        // 搭图期留下的预览立牌先清掉：下面会按同一份 config 重新生成正式 NPC，不清就是两份
        int cleared = clearPreviews(level);
        if (cleared > 0) {
            org.agmas.noellesroles.Noellesroles.LOGGER.info("[60s] 清理搭图预览 NPC {} 只，按配置重新生成。", cleared);
        }
        SixtySecondsConfig config = SixtySecondsConfigStore.current(level).orElse(null);
        if (config == null || config.npcSpawns == null || config.npcSpawns.isEmpty()) {
            return;
        }
        for (SixtySecondsConfig.NpcSpawn spawn : config.npcSpawns) {
            if (spawn.pos == null) {
                continue;
            }
            BlockPos template = spawn.pos.toBlockPos();
            SixtySecondsNpcEntity.Variant variant = SixtySecondsNpcEntity.Variant.byId(spawn.variant);
            if (!isInPerTeamTemplate(config, template)) {
                // 野外/搜索区：单份，全队共用（搜索区不克隆）
                spawnAt(level, template, variant, spawn.yaw, spawn.profile, spawn.garrisonRadius, -1);
                continue;
            }
            // 住宅/避难所：每队一份。点已是模板<b>绝对</b>坐标且落在模板盒内，
            // 故换算与 SixtySecondsArena.spawnFor 一致——直接叠加该队的网格偏移即可。
            int index = 0;
            for (SixtySecondsState.TeamData team : data.teams.values()) {
                BlockPos at = template.offset(config.teamOffset(index));
                spawnAt(level, at, variant, spawn.yaw, spawn.profile, spawn.garrisonRadius, team.teamId);
                index++;
            }
        }
    }

    /** 该模板点是否落在「每队克隆」的模板盒（住宅/避难所）内——是则要按队各生成一份。 */
    private static boolean isInPerTeamTemplate(SixtySecondsConfig config, BlockPos pos) {
        return (config.residentialTemplate != null && config.residentialTemplate.toBox().isInside(pos))
                || (config.shelterTemplate != null && config.shelterTemplate.toBox().isInside(pos));
    }

    // ── 路径 2：搜刮区每日刷新 ────────────────────────────────────────────

    /** 白天刷商人/旅者，夜晚刷强盗。每个搜刮区（多队共用的去重后）各刷若干。 */
    public static void spawnDaily(ServerLevel level, SixtySecondsState.Data data, boolean night) {
        RandomSource random = level.getRandom();
        int base = SixtySecondsBalance.NPC_DAILY_PER_ZONE_BASE + data.dayNumber / 2;
        for (AABB zone : searchZones(data)) {
            int existing = level.getEntitiesOfClass(SixtySecondsNpcEntity.class, zone).size();
            int want = Math.min(base, SixtySecondsBalance.NPC_ZONE_CAP - existing);
            for (int i = 0; i < want; i++) {
                BlockPos spot = findGroundSpot(level, zone, random);
                if (spot == null) {
                    continue;
                }
                SixtySecondsNpcEntity.Variant variant = night
                        ? SixtySecondsNpcEntity.Variant.BANDIT
                        : (random.nextFloat() < SixtySecondsBalance.NPC_DAY_TRAVELER_RATIO
                                ? SixtySecondsNpcEntity.Variant.TRAVELER
                                : SixtySecondsNpcEntity.Variant.MERCHANT);
                spawnAt(level, spot, variant, random.nextFloat() * 360.0F, "default", 8, -1);
            }
        }
    }

    /** 各队搜刮区盒去重（多队常共用同一个搜索区，不去重会按队数倍刷）。 */
    private static Set<AABB> searchZones(SixtySecondsState.Data data) {
        Set<AABB> zones = new LinkedHashSet<>();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.searchZoneBox != null) {
                zones.add(team.searchZoneBox);
            }
            for (SixtySecondsState.TeamData.SearchLink link : team.searchDoors.values()) {
                if (link.box() != null) {
                    zones.add(link.box());
                }
            }
        }
        return zones;
    }

    /**
     * 在盒内随机 XZ 找一个可站立的地面点（自写，不动 {@code DefenseSystem.findSpawnSpot}——那是 private）。
     * 从盒顶向下扫第一个「实心地面 + 上方两格通透」的位置；找不到返回 null。
     */
    @Nullable
    public static BlockPos findGroundSpot(ServerLevel level, AABB box, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = (int) (box.minX + random.nextDouble() * (box.maxX - box.minX));
            int z = (int) (box.minZ + random.nextDouble() * (box.maxZ - box.minZ));
            int top = (int) box.maxY;
            int bottom = (int) box.minY;
            for (int y = top; y >= bottom; y--) {
                BlockPos ground = new BlockPos(x, y, z);
                BlockPos feet = ground.above();
                if (level.getBlockState(ground).isSolidRender(level, ground)
                        && level.getBlockState(feet).isAir()
                        && level.getBlockState(feet.above()).isAir()) {
                    return feet;
                }
            }
        }
        return null;
    }

    // ── 路径 5：搜刮区绑定门门口概率刷 ─────────────────────────────────────

    /** 每队每扇绑定门按概率在其「出门落点」旁刷一只 NPC——玩家一出门就撞见。 */
    public static void spawnAtDoors(ServerLevel level, SixtySecondsState.Data data, boolean night) {
        RandomSource random = level.getRandom();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            for (SixtySecondsState.TeamData.SearchLink link : team.searchDoors.values()) {
                if (link.spawn() == null
                        || random.nextFloat() >= SixtySecondsBalance.NPC_DOOR_SPAWN_CHANCE) {
                    continue;
                }
                // findSafeSpot 是现成的 public 工具（探索区落点找安全位）
                BlockPos spot = net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones
                        .findSafeSpot(level, link.spawn());
                SixtySecondsNpcEntity.Variant variant = night
                        ? SixtySecondsNpcEntity.Variant.BANDIT
                        : (random.nextFloat() < SixtySecondsBalance.NPC_DAY_TRAVELER_RATIO
                                ? SixtySecondsNpcEntity.Variant.TRAVELER
                                : SixtySecondsNpcEntity.Variant.MERCHANT);
                spawnAt(level, spot, variant, random.nextFloat() * 360.0F, "default", 6, -1);
            }
        }
    }

    // ── 路径 6：海盗（海上乘船随机遭遇）────────────────────────────────────

    /**
     * 海盗刷新：对每名<b>在水面附近</b>的玩家做一次判定，在其 20~44 格外的开阔水面刷 1~2 名海盗，
     * <b>每人一条船</b>。判定只看「玩家周围能不能找到像样的海面」——这样群岛海域与探索区的
     * 河/海自然都覆盖到，不用分两套系统。船靠 {@code SixtySecondsNpcEntity.tickPirateBoat} 划向玩家。
     */
    public static void spawnPirates(ServerLevel level, SixtySecondsState.Data data, boolean night) {
        RandomSource random = level.getRandom();
        double chance = SixtySecondsBalance.PIRATE_SPAWN_CHANCE
                * (night ? SixtySecondsBalance.PIRATE_NIGHT_CHANCE_MULT : 1.0);
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative()
                    || io.wifi.starrailexpress.game.GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            // 只找<b>出了门</b>的人：在家的玩家哪怕住在水边也不该被海盗堵门，那是夜袭的活。
            // 这条同时把「扬帆去海岛」的玩家收进来——出海本来就走的是探索区状态。
            if (!net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones.isInSearchZone(player)) {
                continue;
            }
            if (random.nextDouble() >= chance) {
                continue;
            }
            // 附近海盗已够多就不再刷（防海盗海）
            AABB near = player.getBoundingBox().inflate(SixtySecondsBalance.PIRATE_NEARBY_RADIUS);
            int existing = 0;
            for (SixtySecondsNpcEntity npc : level.getEntitiesOfClass(SixtySecondsNpcEntity.class, near)) {
                if (npc.getVariant() == SixtySecondsNpcEntity.Variant.PIRATE) {
                    existing++;
                }
            }
            if (existing >= SixtySecondsBalance.PIRATE_MAX_NEARBY) {
                continue;
            }
            int pack = SixtySecondsBalance.PIRATE_PACK_MIN + random.nextInt(
                    SixtySecondsBalance.PIRATE_PACK_MAX - SixtySecondsBalance.PIRATE_PACK_MIN + 1);
            pack = Math.min(pack, SixtySecondsBalance.PIRATE_MAX_NEARBY - existing);
            int spawned = 0;
            for (int i = 0; i < pack; i++) {
                BlockPos sea = findSeaSpot(level, player.blockPosition(), random);
                if (sea == null) {
                    break; // 这名玩家周围没海面，直接放弃（不是海边就不该有海盗）
                }
                if (spawnPirateOnBoat(level, sea, random) != null) {
                    spawned++;
                }
            }
            if (spawned > 0) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.noellesroles.sixty_seconds.npc.pirate_sighted")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                player.playNotifySound(net.minecraft.sounds.SoundEvents.BOAT_PADDLE_WATER,
                        net.minecraft.sounds.SoundSource.HOSTILE, 0.7F, 0.8F);
            }
        }
    }

    /** 造一条船 + 船上一名海盗。船挂 {@code PIRATE_BOAT_TAG}，海盗死/散时随之清掉。 */
    @Nullable
    public static SixtySecondsNpcEntity spawnPirateOnBoat(ServerLevel level, BlockPos waterPos,
            RandomSource random) {
        net.minecraft.world.entity.vehicle.Boat boat = new net.minecraft.world.entity.vehicle.Boat(
                net.minecraft.world.entity.EntityType.BOAT, level);
        boat.setVariant(net.minecraft.world.entity.vehicle.Boat.Type.DARK_OAK);
        boat.setPos(waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);
        boat.setYRot(random.nextFloat() * 360.0F);
        boat.addTag(SixtySecondsNpcEntity.PIRATE_BOAT_TAG);
        if (!level.addFreshEntity(boat)) {
            return null;
        }
        SixtySecondsNpcEntity pirate = spawnAt(level, waterPos, SixtySecondsNpcEntity.Variant.PIRATE,
                boat.getYRot(), "default", 8, -1);
        if (pirate == null) {
            boat.discard();
            return null;
        }
        fillCarry(level, pirate); // 海盗身上也有货：打赢了有得捞
        pirate.startRiding(boat, true);
        return pirate;
    }

    /**
     * 在玩家周围 {@code PIRATE_SPAWN_MIN_DIST}~{@code MAX_DIST} 找一处开阔水面（水面格：本格是水、
     * 上方两格空气、下方也是水=有深度，不刷在一格水洼里）。找不到返回 null——玩家不在海边就不该冒出海盗。
     * 搜索半径控制在加载区块内，不会触发同步区块加载。
     */
    @Nullable
    private static BlockPos findSeaSpot(ServerLevel level, BlockPos near, RandomSource random) {
        int min = SixtySecondsBalance.PIRATE_SPAWN_MIN_DIST;
        int max = SixtySecondsBalance.PIRATE_SPAWN_MAX_DIST;
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = min + random.nextDouble() * (max - min);
            int x = near.getX() + (int) (Math.cos(angle) * dist);
            int z = near.getZ() + (int) (Math.sin(angle) * dist);
            if (!level.hasChunkAt(new BlockPos(x, near.getY(), z))) {
                continue; // 区块没加载：跳过而不是强载，免得刷海盗把主线程拖住
            }
            for (int y = near.getY() + 8; y >= near.getY() - 16; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) {
                    continue;
                }
                boolean surface = level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.above(2)).isAir();
                boolean deep = level.getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER);
                if (surface && deep) {
                    return pos;
                }
                break; // 撞到水但不合格（浅水/被盖住）：这一列不用再往下找了
            }
        }
        return null;
    }

    // ── 路径 3/4：夜袭强盗 ───────────────────────────────────────────────

    /**
     * 生成混入夜袭的强盗：挂 {@code ASSAULT_TAG} + 队伍 tag 后，清晨消散 / 破门涌入 / 死亡掉废料 /
     * {@code discardTaggedMobs} 兜底<b>全部由 DefenseSystem 自动覆盖</b>，无需本类重复实现。
     *
     * @param mobs 夜袭追踪表，生成的强盗要登记进去才会被 tickAssault 驱动冲门
     */
    public static void spawnAssaultBandits(ServerLevel level, SixtySecondsState.TeamData team,
            BlockPos door, int count, List<java.util.UUID> mobs) {
        RandomSource random = level.getRandom();
        List<BlockPos> spots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlockPos spot = door.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
            spots.add(spot);
        }
        for (BlockPos spot : spots) {
            SixtySecondsNpcEntity npc = spawnAt(level, spot, SixtySecondsNpcEntity.Variant.BANDIT,
                    random.nextFloat() * 360.0F, "default", 8, team.teamId);
            if (npc == null) {
                continue;
            }
            npc.addTag(SixtySecondsDefenseSystem.ASSAULT_TAG);
            npc.addTag(SixtySecondsDefenseSystem.ASSAULT_TEAM_TAG_PREFIX + team.teamId);
            npc.setGlowingTag(true);
            npc.setBattleMob(true); // 战场怪：无人也不自散（离线也要冲门）
            mobs.add(npc.getUUID());
        }
    }
}
