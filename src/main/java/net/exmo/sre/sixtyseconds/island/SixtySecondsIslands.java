package net.exmo.sre.sixtyseconds.island;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsPveSystem;
import net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartS2CPacket;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.exmo.sre.subtitle.SubtitleCommand;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 60s 海岛远征总控（指令开关，独立于对局；对局中提供玩法钩子）：
 * <ul>
 *   <li><b>生成/还原</b>：{@code /sre:60s island start|stop} 异步建造/回滚整片群岛
 *       （{@link SixtySecondsIslandGenerator}）；元数据落盘 {@code sixty_seconds_islands.json}，重启不丢。</li>
 *   <li><b>登岛提示</b>：踏上新岛 → SubTitle 报幕岛名+危险等级，4 级以上红色特别警报 + 音效；
 *       首次登岛为全队解锁该岛并刷一小队守岛怪。</li>
 *   <li><b>海图</b>：{@link SixtySecondsSeaChartS2CPacket} 把岛屿元数据（含解锁迷雾）同步给客户端
 *       SeaChartScreen；点击已解锁岛屿=扬帆（{@link #sail}）。</li>
 *   <li><b>情报解锁</b>：探索踏足 / 收音机侦听（{@link #tryRadioIntel}，每队每日一次）/
 *       每日事件（{@code SixtySecondsDailyEvents} 的 island_* 事件）三条途径点亮海图。</li>
 *   <li><b>等级联动</b>：{@code SixtySecondsAreaLevels.levelAt} 优先返回岛屿等级——物资箱稀有度
 *       与 PVE 游荡怪强度自动随岛等级缩放。</li>
 * </ul>
 */
public final class SixtySecondsIslands {

    public static final String LANG = SixtySecondsIsland.LANG;
    public static final String FILE_NAME = "sixty_seconds_islands.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 扬帆冷却（tick）。 */
    private static final int SAIL_COOLDOWN_TICKS = 20 * 10;
    /** 收音机侦听到岛屿情报的概率（每队每日一次机会）。 */
    private static final double RADIO_INTEL_CHANCE = 0.45;

    private static final Map<ServerLevel, Data> STATES = new WeakHashMap<>();

    private SixtySecondsIslands() {
    }

    /** 落盘部分（Gson）。 */
    public static class SaveData {
        public boolean enabled = false;
        public List<SixtySecondsIsland> islands = new ArrayList<>();
    }

    /** 每世界运行态。 */
    public static final class Data {
        public final SaveData save = new SaveData();
        public boolean building = false;
        /** teamId → 已解锁岛 id（海图可见）。 */
        public final Map<Integer, Set<Integer>> teamUnlocked = new HashMap<>();
        /** teamId → 已亲自踏足的岛 id。 */
        public final Map<Integer, Set<Integer>> teamVisited = new HashMap<>();
        /** 玩家 → 当前所在岛 id（登岛沿检测）。 */
        public final Map<UUID, Integer> lastIsland = new HashMap<>();
        /** teamId → 已用收音机侦听情报的游戏日。 */
        public final Map<Integer, Integer> radioIntelDay = new HashMap<>();
        /** 本局已刷过守岛怪的岛 id。 */
        public final Set<Integer> guardSpawned = new HashSet<>();
        /** 玩家 → 扬帆冷却截止 tick。 */
        public final Map<UUID, Long> sailCooldown = new HashMap<>();
        /** 生成快照（还原用，仅本进程内存）。 */
        public final LinkedHashMap<BlockPos, SixtySecondsIslandGenerator.Snapshot> snapshots =
                new LinkedHashMap<>();
        private boolean loaded = false;
    }

    public static Data get(ServerLevel level) {
        Data data = STATES.computeIfAbsent(level, ignored -> new Data());
        if (!data.loaded) {
            data.loaded = true;
            load(level, data);
        }
        return data;
    }

    public static boolean enabled(ServerLevel level) {
        return get(level).save.enabled;
    }

    /** 坐标所在岛的危险等级；不在任何岛单元格内返回 0（{@code SixtySecondsAreaLevels} 反查用）。 */
    public static int levelAt(ServerLevel level, BlockPos pos) {
        Data data = STATES.get(level);
        if (data == null || !data.save.enabled) {
            return 0;
        }
        for (SixtySecondsIsland island : data.save.islands) {
            if (island.inCell(pos)) {
                return island.level;
            }
        }
        return 0;
    }

    /** 全群岛外包盒（扬帆后的活动限制盒；含所有单元格）。 */
    public static AABB regionBox(Data data) {
        AABB union = null;
        for (SixtySecondsIsland island : data.save.islands) {
            AABB cell = island.cellBox();
            union = union == null ? cell : union.minmax(cell);
        }
        return union;
    }

    // ── 注册（init 一次）：收音机侦听钩子 ─────────────────────────────────────

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                    && player.getItemInHand(hand).getItem()
                            instanceof org.agmas.noellesroles.content.item.RadioItem) {
                tryRadioIntel(serverLevel, serverPlayer);
            }
            return InteractionResultHolder.pass(ItemStack.EMPTY);
        });
    }

    // ── 生成 / 还原 ──────────────────────────────────────────────────────

    /**
     * 生成群岛（异步）；已启用或正在建造时返回 false。完成后向全员分发海图。
     * {@code baseRadius} ≤0 用默认基准半径（{@link SixtySecondsIslandGenerator#DEFAULT_BASE_RADIUS}）。
     */
    public static boolean start(ServerLevel level, int count, int centerX, int centerZ, int seaY,
            int baseRadius) {
        Data data = get(level);
        if (data.building || data.save.enabled) {
            return false;
        }
        data.building = true;
        RandomSource rng = RandomSource.create(level.random.nextLong());
        List<SixtySecondsIsland> islands = SixtySecondsIslandGenerator.plan(rng, count, centerX, centerZ, seaY,
                baseRadius);
        data.save.islands.clear();
        data.save.islands.addAll(islands);
        data.snapshots.clear();
        Noellesroles.LOGGER.info("[60s] 开始生成海岛群：{} 座，中心 ({}, {})，海平面 y={}，基准半径 {}。",
                islands.size(), centerX, centerZ, seaY,
                baseRadius > 0 ? baseRadius : SixtySecondsIslandGenerator.DEFAULT_BASE_RADIUS);
        SixtySecondsIslandGenerator.queueBuild(level, islands, data.snapshots, () -> {
            data.building = false;
            data.save.enabled = true;
            save(level, data);
            // 全部海岛生成完毕 → 生成海图并分发（带打开提示）
            Component hint = Component.translatable(LANG + "generated", islands.size())
                    .withStyle(ChatFormatting.GOLD)
                    .append(" ")
                    .append(Component.translatable(LANG + "open_chart")
                            .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                            "/sre:60s island map"))));
            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(hint, false);
                player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.2F);
            }
            syncChartAll(level);
        });
        return true;
    }

    /** 关闭海岛模式并回滚地形（快照仅存于本进程；重启后 stop 只清登记不动方块）。 */
    public static boolean stop(ServerLevel level) {
        Data data = get(level);
        if (data.building || !data.save.enabled) {
            return false;
        }
        data.save.enabled = false;
        // 把还在岛上的玩家送回（有搜索区记录的走回家流程）
        for (ServerPlayer player : level.players()) {
            Integer islandId = data.lastIsland.remove(player.getUUID());
            if (islandId != null && SixtySecondsSearchZones.isInSearchZone(player)) {
                SixtySecondsSearchZones.forceReturn(player);
            }
        }
        List<SixtySecondsIsland> islands = new ArrayList<>(data.save.islands);
        boolean canRestore = !data.snapshots.isEmpty() || !islands.isEmpty();
        if (canRestore) {
            data.building = true;
            SixtySecondsIslandGenerator.queueRestore(level, islands, data.snapshots, () -> {
                data.building = false;
                data.save.islands.clear();
                clearRuntime(data);
                save(level, data);
                syncChartAll(level);
            });
        } else {
            data.save.islands.clear();
            clearRuntime(data);
            save(level, data);
            syncChartAll(level);
        }
        return true;
    }

    private static void clearRuntime(Data data) {
        data.teamUnlocked.clear();
        data.teamVisited.clear();
        data.lastIsland.clear();
        data.radioIntelDay.clear();
        data.guardSpawned.clear();
        data.sailCooldown.clear();
    }

    /** 开局钩子（{@code SixtySecondsManager.begin}）：清跨局解锁态，为每队默认解锁全部 1 级港湾岛。 */
    public static void onGameStart(ServerLevel level) {
        Data data = get(level);
        data.teamUnlocked.clear();
        data.teamVisited.clear();
        data.lastIsland.clear();
        data.radioIntelDay.clear();
        data.guardSpawned.clear();
        data.sailCooldown.clear();
        if (!data.save.enabled) {
            return;
        }
        SixtySecondsState.Data state = SixtySecondsState.get(level);
        for (Integer teamId : state.teams.keySet()) {
            Set<Integer> unlocked = data.teamUnlocked.computeIfAbsent(teamId, ignored -> new HashSet<>());
            for (SixtySecondsIsland island : data.save.islands) {
                if (island.level <= 1) {
                    unlocked.add(island.id);
                }
            }
        }
    }

    // ── tick：登岛沿检测（Manager DAY 相位每 tick 调，内部 10 tick 一次）──────────

    public static void tick(ServerLevel level) {
        Data data = STATES.get(level);
        if (data == null || !data.save.enabled || data.save.islands.isEmpty()
                || level.getGameTime() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            BlockPos pos = player.blockPosition();
            SixtySecondsIsland current = null;
            for (SixtySecondsIsland island : data.save.islands) {
                if (island.isOnIsland(pos)) {
                    current = island;
                    break;
                }
            }
            Integer lastId = data.lastIsland.get(player.getUUID());
            if (current == null) {
                data.lastIsland.remove(player.getUUID());
                continue;
            }
            if (lastId != null && lastId == current.id) {
                continue;
            }
            data.lastIsland.put(player.getUUID(), current.id);
            onLanded(level, data, player, current);
        }
    }

    /** 登岛：SubTitle 报幕 + 高危警报 + 全队解锁 + 首登刷守岛怪 + 区域地图切到本岛。 */
    private static void onLanded(ServerLevel level, Data data, ServerPlayer player, SixtySecondsIsland island) {
        int lv = island.level;
        ChatFormatting nameColor = lv >= 4 ? ChatFormatting.RED : lv >= 3 ? ChatFormatting.GOLD
                : ChatFormatting.AQUA;
        Component main = island.name().copy().withStyle(nameColor, ChatFormatting.BOLD);
        Component sub = Component.translatable(LANG + "level", lv)
                .withStyle(lv >= 4 ? ChatFormatting.DARK_RED : lv >= 3 ? ChatFormatting.YELLOW
                        : ChatFormatting.GREEN);
        SubtitleCommand.sendToPlayerTop(player, main, sub, 90, false);
        if (lv >= 4) {
            // 高危岛特别提醒：追加红色警报字幕 + 阴森音效
            SubtitleCommand.sendToPlayerTop(player,
                    Component.translatable(LANG + "enter_danger").withStyle(ChatFormatting.DARK_RED,
                            ChatFormatting.BOLD),
                    Component.translatable(LANG + "enter_danger_sub").withStyle(ChatFormatting.RED),
                    70, false);
            player.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.8F, 0.85F);
        } else {
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 0.8F);
        }
        // 区域地图切到本岛单元格
        net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.ensureZone(
                player, island.cellBox(), island.dockPos(), false);
        // 全队解锁 + 踏足登记
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        if (teamId >= 0) {
            data.teamVisited.computeIfAbsent(teamId, ignored -> new HashSet<>()).add(island.id);
            unlockForTeam(level, teamId, island,
                    Component.translatable(LANG + "unlocked_by_visit", island.name(), island.level));
        }
        // 首登守岛怪：一小队，等级越高越强（每岛每局一次；后续增援靠 PVE 游荡怪）
        if (SixtySecondsMod.isActive(level) && data.guardSpawned.add(island.id)
                && SixtySecondsPveSystem.pveEnabled(level)) {
            RandomSource rng = level.random;
            int pack = 1 + island.level + rng.nextInt(2);
            for (int i = 0; i < pack; i++) {
                BlockPos spot = SixtySecondsIslandGenerator.randomGround(level, island, rng, 0.1, 0.7);
                if (spot != null) {
                    SixtySecondsPveSystem.createMonster(level, spot,
                            SixtySecondsIslandGenerator.rollVariant(rng, island.level),
                            1.0 + 0.15 * (island.level - 1), 1.0);
                }
            }
        }
    }

    // ── 解锁（消息系统的统一出口）───────────────────────────────────────────

    /** 为一支队伍解锁一座岛：首次解锁时向全队播报 {@code message} 并重发海图。 */
    public static boolean unlockForTeam(ServerLevel level, int teamId, SixtySecondsIsland island,
            Component message) {
        Data data = get(level);
        Set<Integer> unlocked = data.teamUnlocked.computeIfAbsent(teamId, ignored -> new HashSet<>());
        if (!unlocked.add(island.id)) {
            return false;
        }
        SixtySecondsState.TeamData team = SixtySecondsState.get(level).teams.get(teamId);
        if (team != null) {
            for (UUID uuid : team.members) {
                ServerPlayer member = level.getServer().getPlayerList().getPlayer(uuid);
                if (member != null) {
                    member.displayClientMessage(message.copy().withStyle(ChatFormatting.GOLD), false);
                    member.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS,
                            0.8F, 1.4F);
                    syncChart(member, false);
                }
            }
        }
        return true;
    }

    /**
     * 情报解锁一座随机未解锁岛（收音机/每日事件共用）。返回解锁的岛，全部已解锁返回 null。
     * 低等级岛更容易被打听到（权重 = 6 - level）。
     */
    public static SixtySecondsIsland unlockRandomLocked(ServerLevel level, int teamId, String reasonKey) {
        Data data = get(level);
        if (!data.save.enabled) {
            return null;
        }
        Set<Integer> unlocked = data.teamUnlocked.computeIfAbsent(teamId, ignored -> new HashSet<>());
        List<SixtySecondsIsland> locked = new ArrayList<>();
        int totalWeight = 0;
        for (SixtySecondsIsland island : data.save.islands) {
            if (!unlocked.contains(island.id)) {
                locked.add(island);
                totalWeight += 6 - island.level;
            }
        }
        if (locked.isEmpty()) {
            return null;
        }
        int roll = level.random.nextInt(Math.max(1, totalWeight));
        SixtySecondsIsland picked = locked.get(locked.size() - 1);
        for (SixtySecondsIsland island : locked) {
            roll -= 6 - island.level;
            if (roll < 0) {
                picked = island;
                break;
            }
        }
        unlockForTeam(level, teamId, picked,
                Component.translatable(reasonKey, picked.name(), picked.level));
        return picked;
    }

    /** 每日事件效果入口：为该队解锁一座岛；没有可解锁的返回 false（事件文案自行兜底）。 */
    public static boolean intelEvent(ServerLevel level, SixtySecondsState.TeamData team) {
        return get(level).save.enabled
                && unlockRandomLocked(level, team.teamId, LANG + "unlocked_by_intel") != null;
    }

    /** 收音机侦听：海岛开启的对局中，每队每日一次机会，成功则解锁一座岛。 */
    private static void tryRadioIntel(ServerLevel level, ServerPlayer player) {
        Data data = STATES.get(level);
        if (data == null || !data.save.enabled || !SixtySecondsMod.isActive(level)) {
            return;
        }
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        if (teamId < 0) {
            return;
        }
        int day = SixtySecondsState.get(level).dayNumber;
        if (day <= 0) {
            return;
        }
        Integer lastDay = data.radioIntelDay.get(teamId);
        if (lastDay != null && lastDay >= day) {
            return; // 今天已侦听过，静默（对讲机原功能不受影响）
        }
        data.radioIntelDay.put(teamId, day);
        if (level.random.nextDouble() < RADIO_INTEL_CHANCE) {
            SixtySecondsIsland unlocked = unlockRandomLocked(level, teamId, LANG + "unlocked_by_radio");
            if (unlocked != null) {
                return;
            }
        }
        player.displayClientMessage(
                Component.translatable(LANG + "radio_noise").withStyle(ChatFormatting.GRAY), false);
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 0.6F);
    }

    // ── 扬帆 / 返航 ──────────────────────────────────────────────────────

    /**
     * 扬帆前往指定岛（海图点击/命令）：要求海岛开启、对局进行中、玩家已出门探索、目标岛已解锁。
     * 落点=该岛登岛滩头（安全校正），并把活动限制盒切换为整片群岛（可乘船在岛间穿行）。
     */
    public static void sail(ServerPlayer player, int islandId) {
        ServerLevel level = player.serverLevel();
        Data data = get(level);
        if (!data.save.enabled || data.building) {
            player.displayClientMessage(Component.translatable(LANG + "sail_unavailable")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        SixtySecondsIsland island = byId(data, islandId);
        if (island == null) {
            return;
        }
        boolean creative = player.isCreative();
        if (!creative) {
            if (!SixtySecondsMod.isActive(level) || !SixtySecondsSearchZones.isInSearchZone(player)) {
                player.displayClientMessage(Component.translatable(LANG + "sail_not_in_zone")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.downed) {
                return;
            }
            int teamId = stats.teamId;
            Set<Integer> unlocked = data.teamUnlocked.get(teamId);
            if (teamId < 0 || unlocked == null || !unlocked.contains(island.id)) {
                player.displayClientMessage(Component.translatable(LANG + "sail_locked")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            long now = level.getGameTime();
            Long cooldownEnd = data.sailCooldown.get(player.getUUID());
            if (cooldownEnd != null && now < cooldownEnd) {
                player.displayClientMessage(Component.translatable(LANG + "sail_cooldown",
                        (int) Math.ceil((cooldownEnd - now) / 20.0)).withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            data.sailCooldown.put(player.getUUID(), now + SAIL_COOLDOWN_TICKS);
        }
        BlockPos safe = SixtySecondsSearchZones.findSafeSpot(level, island.dockPos());
        player.teleportTo(level, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        // 活动限制盒切到整片群岛：允许乘船/游泳在岛间穿行；「返回住所」仍走原搜索区流程
        AABB region = regionBox(data);
        if (!creative && region != null) {
            SixtySecondsSearchZones.updateConfine(player, region, safe);
        }
        level.playSound(null, safe, SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 0.8F, 1.0F);
        player.displayClientMessage(Component.translatable(LANG + "sail_success", island.name())
                .withStyle(ChatFormatting.AQUA), true);
    }

    public static SixtySecondsIsland byId(Data data, int islandId) {
        for (SixtySecondsIsland island : data.save.islands) {
            if (island.id == islandId) {
                return island;
            }
        }
        return null;
    }

    // ── 海图同步 ─────────────────────────────────────────────────────────

    /** 给一名玩家同步海图（openScreen=true 时客户端直接打开界面）。 */
    public static void syncChart(ServerPlayer player, boolean openScreen) {
        SixtySecondsSeaChartS2CPacket.send(player, openScreen);
    }

    public static void syncChartAll(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            syncChart(player, false);
        }
    }

    // ── 落盘 ────────────────────────────────────────────────────────────

    private static Path path(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    private static void load(ServerLevel level, Data data) {
        Path file = path(level);
        if (!Files.exists(file)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            SaveData loaded = GSON.fromJson(reader, SaveData.class);
            if (loaded != null) {
                data.save.enabled = loaded.enabled;
                data.save.islands = loaded.islands != null ? loaded.islands : new ArrayList<>();
            }
        } catch (IOException | RuntimeException e) {
            Noellesroles.LOGGER.warn("[60s] 读取 {} 失败：{}", FILE_NAME, e.toString());
        }
    }

    public static void save(ServerLevel level, Data data) {
        Path file = path(level);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data.save, writer);
            }
        } catch (IOException e) {
            Noellesroles.LOGGER.warn("[60s] 写入 {} 失败：{}", FILE_NAME, e.toString());
        }
    }
}
