package org.agmas.noellesroles.game.modes.echo.editor;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachines;
import org.agmas.noellesroles.game.modes.echo.map.*;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoPreflight;
import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 回响地图编辑物品的服务端会话。
 *
 * <p>与 {@code /sre:echo map ...} 命令走同一套底层（{@link EchoMapEditState} / {@link EchoMapDraft} /
 * {@link EchoMapRepository} / {@link EchoPreflight}），但入口是网络包，因此这里自己维护一份按玩家隔离的会话表：
 * 仅 OP2、仅非对局、每人一份、全服上限 16、闲置 30 分钟或切图/换平移即失效。命令会话与物品会话互不影响，
 * 不会出现两处同时改同一份草稿。
 */
public final class EchoEditorService {
    private static final int MAX_SESSIONS = 16;

    /** 一个玩家的编辑会话；kind 是界面当前选中的点位类型（逐条列表用），随选择变化。 */
    private static final class Session {
        private final ServerLevel level;
        private final EchoMapEditState state;
        private String kind;
        private Session(ServerLevel level, EchoMapEditState state) { this.level = level; this.state = state; }
    }

    /** 一条回给客户端的本地化消息：[0] 是键，其余是参数。 */
    private record Msg(String key, List<String> args) {
        static Msg of(String key, String... args) { return new Msg(key, List.of(args)); }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> HEAVY = new HashMap<>();
    private static long tick;

    private EchoEditorService() { }

    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(EchoEditorActionPayload.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
    }

    // ==================== 入口 ====================

    public static void handle(ServerPlayer player, EchoEditorActionPayload payload) {
        if (!player.hasPermissions(2)) {
            reply(player, EchoEditorStatePayload.empty("echo.editor.msg.no_permission"));
            return;
        }
        try {
            Msg message = switch (payload.op()) {
                case NEW -> begin(player, payload.a(), true);
                case EDIT -> begin(player, payload.a(), false);
                case CANCEL -> cancel(player);
                case MARK -> mark(player, payload.a(), payload.b(), payload.c());
                case IDENTIFY -> identify(player, payload.a(), payload.b());
                case CORNER_A -> corner(player, true);
                case CORNER_B -> corner(player, false);
                case CORNER_INPUT -> cornerInput(player, payload.a(), payload.b());
                case POINTS -> points(player, payload.a());
                case POINT_META -> pointMeta(player, payload.a(), payload.b(), payload.c());
                case VOLUME -> volume(player, payload.a(), payload.b());
                case REMOVE -> remove(player, payload.a(), payload.b());
                case LIST -> Msg.of("echo.editor.msg.state");
                case VALIDATE -> validate(player);
                case PREVIEW -> preview(player);
                case SAVE -> save(player, "");
                case SAVE_CONFIRM -> save(player, payload.a());
                case GATE_ON -> gate(player, true);
                case GATE_OFF -> gate(player, false);
                case RELOAD -> Msg.of("echo.editor.msg.reload");
            };
            reply(player, snapshot(player, message));
        } catch (IOException | IllegalArgumentException failure) {
            reply(player, snapshot(player, Msg.of("echo.editor.msg.failed", String.valueOf(failure.getMessage()))));
        }
    }

    public static void tick(MinecraftServer server) {
        tick++;
        if (tick % 20 != 0) return;
        SESSIONS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !player.hasPermissions(2) || player.serverLevel() != session.level) return true;
            try {
                idle(session.level);
                return !session.state.valid(context(session.level, session.state.context().mapId()), tick);
            } catch (IOException | IllegalArgumentException invalid) {
                return true;
            }
        });
        HEAVY.entrySet().removeIf(entry -> tick - entry.getValue() > 1200);
    }

    public static void disconnected(UUID player) { SESSIONS.remove(player); HEAVY.remove(player); }

    public static void unload(ServerLevel level) {
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().level == level);
    }

    public static void stop() { SESSIONS.clear(); HEAVY.clear(); tick = 0; }

    // ==================== 操作 ====================

    private static Msg begin(ServerPlayer player, String requested, boolean fresh) throws IOException {
        ServerLevel level = player.serverLevel();
        idle(level);
        var areas = AreasWorldComponent.KEY.get(level);
        boolean hasAreas = areas.mapName != null && !areas.mapName.isBlank();
        // 留空时按「switchmap 绑定 → Areas 地图名 → 自动命名」取名；工具自包含，不要求先加载 Areas 地图。
        String mapId = requested == null || requested.isBlank() ? suggestedMapId(level) : requested.trim();
        EchoMapRepository.requireMapId(mapId);
        if (SESSIONS.containsKey(player.getUUID())) throw new IOException("已有草稿，请先保存或取消；不会自动覆盖内存编辑");
        if (SESSIONS.size() >= MAX_SESSIONS) throw new IOException("全服编辑会话已达上限");
        var draft = fresh ? EchoMapDraft.empty(mapId) : new EchoMapDraft(EchoMapRepository.load(world(level), mapId));
        SESSIONS.put(player.getUUID(), new Session(level, new EchoMapEditState(context(level, mapId), draft, tick)));
        // 没加载 Areas 地图也能建档：偏移记 0、坐标按世界原样记录，但要明确告知风险
        if (!hasAreas) return Msg.of(fresh ? "echo.editor.msg.new_no_areas" : "echo.editor.msg.edit_no_areas", mapId);
        return Msg.of(fresh ? "echo.editor.msg.new" : "echo.editor.msg.edit", mapId, areas.mapName);
    }

    /** 新建时留空用的建议名：switchmap 绑定 → Areas 地图名 → 自动命名（echo_map、echo_map_2……避开已存在文件）。 */
    private static String suggestedMapId(ServerLevel level) throws IOException {
        try {
            String bound = EchoActiveMap.effective(world(level), null);
            if (bound != null && !bound.isBlank()) return bound;
        } catch (IOException ignored) {
            // 绑定文件损坏不影响新建：继续按 Areas 同名 / 自动命名走
        }
        var areas = AreasWorldComponent.KEY.get(level);
        if (areas.mapName != null && !areas.mapName.isBlank()) return areas.mapName;
        for (int n = 1; n <= 99; n++) {
            String candidate = n == 1 ? "echo_map" : "echo_map_" + n;
            if (!fileExists(level, candidate)) return candidate;
        }
        throw new IOException("自动命名失败：请手动填写地图ID");
    }

    private static Msg cancel(ServerPlayer player) throws IOException {
        edit(player);
        SESSIONS.remove(player.getUUID());
        return Msg.of("echo.editor.msg.cancelled");
    }

    private static Msg mark(ServerPlayer player, String kind, String id, String minPlayers) throws IOException {
        EchoMapEditState state = edit(player);
        int threshold = 6;
        if (minPlayers != null && !minPlayers.isBlank()) {
            try { threshold = Integer.parseInt(minPlayers.trim()); }
            catch (NumberFormatException invalid) { throw new IOException("minPlayers必须是6–32的整数"); }
        }
        EchoMapConfig.Vec point;
        if (kind.endsWith("_spawn") || Set.of("spectator", "free_safe", "evacuation_safe", "drop_anchor").contains(kind)) {
            point = new EchoMapConfig.Vec(player.getX(), player.getY(), player.getZ());
        } else {
            BlockPos pos = org.agmas.noellesroles.game.modes.echo.content.EchoMachineInteractions.root(player.serverLevel(),ray(player));
            point = new EchoMapConfig.Vec(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
        state.draft().mark(kind, id, state.template(point), player.getYRot(), player.getXRot(), threshold);
        return Msg.of("echo.editor.msg.changed", kind, id, Long.toString(state.draft().revision()));
    }
    /**
     * 识别准星上的回声机器并写入对应点位。
     *
     * <p>kind 与坐标只来自服务端重新射线，不读客户端参数。界面打开时客户端 hitResult 可能冻结，
     * 因此这里始终取玩家当前视线。同坐标已有点位则更新，不追加重复项。不会自动保存。
     */
    private static Msg identify(ServerPlayer player, String requestedId, String minPlayers) throws IOException {
        EchoMapEditState state = edit(player);
        BlockPos pos = org.agmas.noellesroles.game.modes.echo.content.EchoMachineInteractions.root(player.serverLevel(),ray(player));
        var machine = EchoMachines.kind(player.serverLevel().getBlockState(pos));
        if (machine == null) {
            return Msg.of("echo.editor.machine.not_machine", pos.toShortString());
        }
        String kind = EchoEditorMachineIds.kindOf(machine);
        if (kind == null || !EchoMapDraft.KINDS.contains(kind)) {
            return Msg.of("echo.editor.machine.not_machine", pos.toShortString());
        }
        int threshold = 6;
        if (EchoEditorMachineIds.hasPlayers(kind) && minPlayers != null && !minPlayers.isBlank()) {
            try { threshold = Integer.parseInt(minPlayers.trim()); }
            catch (NumberFormatException invalid) { throw new IOException("minPlayers必须是6–32的整数"); }
        }
        var center = new EchoMapConfig.Vec(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        var template = state.template(center);
        var existing = state.draft().points(kind);
        String same = EchoEditorMachineIds.existingId(existing, template);
        String id = same != null ? same : EchoEditorMachineIds.chooseId(requestedId, kind, existing, template);
        if (id == null) throw new IOException("点位id无效，或已被另一坐标占用");
        state.draft().mark(kind, id, template, player.getYRot(), player.getXRot(), threshold);
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) session.kind = kind;
        String where = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        return Msg.of(same == null ? "echo.editor.machine.marked" : "echo.editor.machine.updated",
                kind, id, where);
    }

    private static Msg corner(ServerPlayer player, boolean first) throws IOException {
        EchoMapEditState state = edit(player);
        BlockPos pos = ray(player);
        state.corner(first, new EchoMapConfig.Vec(pos.getX(), pos.getY(), pos.getZ()));
        return Msg.of("echo.editor.msg.corner", first ? "a" : "b", pos.toShortString());
    }

    /**
     * 输入框手动设入角点：{@code which} 为 a/b，{@code coords} 为 "x y z"（世界坐标，整数方块）。
     *
     * <p>与「取自准星」走同一条落库路径（{@link EchoMapEditState#corner} 内部做世界→模板换算），
     * 所以输入框里回显的坐标与准星取来的是同一套，改完再应用不会出现二次平移。
     */
    private static Msg cornerInput(ServerPlayer player, String which, String coords) throws IOException {
        EchoMapEditState state = edit(player);
        boolean first = !"b".equalsIgnoreCase(which == null ? "" : which.trim());
        EchoMapConfig.Vec point = parseVec(coords);
        state.corner(first, point);
        return Msg.of("echo.editor.msg.corner_input", first ? "a" : "b", formatVec(point));
    }

    /** 解析 "x y z"（空格/逗号分隔）；角点必须是整数方块，与 EditorLayout 的整数 AABB 要求一致。 */
    private static EchoMapConfig.Vec parseVec(String coords) throws IOException {
        if (coords == null || coords.isBlank()) throw new IOException("坐标不能为空，格式：x y z");
        String[] parts = coords.trim().split("[\\s,]+");
        if (parts.length != 3) throw new IOException("坐标需要三个数（x y z）");
        try {
            double x = Double.parseDouble(parts[0]), y = Double.parseDouble(parts[1]), z = Double.parseDouble(parts[2]);
            if (x != Math.rint(x) || y != Math.rint(y) || z != Math.rint(z))
                throw new IOException("角点坐标必须是整数方块坐标");
            return new EchoMapConfig.Vec(x, y, z);
        } catch (NumberFormatException invalid) {
            throw new IOException("坐标必须是数字，格式：x y z");
        }
    }

    private static String formatVec(EchoMapConfig.Vec p) {
        return (long) p.x() + " " + (long) p.y() + " " + (long) p.z();
    }

    private static Msg volume(ServerPlayer player, String kind, String id) throws IOException {
        EchoMapEditState state = edit(player);
        state.draft().volume(kind, id, state.selection());
        return Msg.of("echo.editor.msg.changed", kind, id, Long.toString(state.draft().revision()));
    }

    private static Msg remove(ServerPlayer player, String field, String id) throws IOException {
        EchoMapEditState state = edit(player);
        state.draft().remove(field, id);
        return Msg.of("echo.editor.msg.changed", field, id, Long.toString(state.draft().revision()));
    }

    /** 列出某类型全部点位（id / 人数档 / 坐标）：记下选中类型，之后每次快照都带该类型的逐条列表。 */
    private static Msg points(ServerPlayer player, String kind) throws IOException {
        edit(player);
        if (kind == null || !EchoMapDraft.KINDS.contains(kind)) throw new IOException("未知点位类型: " + kind);
        Session session = SESSIONS.get(player.getUUID());
        session.kind = kind;
        return Msg.of("echo.editor.msg.points", kind,
                Integer.toString(session.state.draft().points(kind).size()));
    }

    /** 就地改点位：{@code spec} 形如 "newId|minPlayers|x y z"（世界坐标，允许小数），任一部分留空表示不改。 */
    private static Msg pointMeta(ServerPlayer player, String kind, String oldId, String spec) throws IOException {
        EchoMapEditState state = edit(player);
        String newId = null;
        Integer players = null;
        String coords = null;
        if (spec != null && !spec.isBlank()) {
            String[] parts = spec.split("\\|", -1);
            if (parts.length > 0 && !parts[0].isBlank()) newId = parts[0].trim();
            if (parts.length > 1 && !parts[1].isBlank()) {
                try { players = Integer.parseInt(parts[1].trim()); }
                catch (NumberFormatException invalid) { throw new IOException("人数档必须是6–32的整数"); }
            }
            if (parts.length > 2 && !parts[2].isBlank()) coords = parts[2].trim();
        }
        // 输入框里编辑的是世界坐标：与「取自准星」走同一条模板换算，不会二次平移
        EchoMapConfig.Vec move = coords == null ? null : state.template(parsePoint(coords));
        String target = newId == null ? oldId : newId;
        state.draft().meta(kind, oldId, target, players, move);
        return Msg.of("echo.editor.msg.changed", kind, target, Long.toString(state.draft().revision()));
    }

    /** 解析点位坐标 "x y z"（空格/逗号分隔，允许小数：出生点是精确落点）。 */
    private static EchoMapConfig.Vec parsePoint(String coords) throws IOException {
        if (coords == null || coords.isBlank()) throw new IOException("坐标不能为空，格式：x y z");
        String[] parts = coords.trim().split("[\\s,]+");
        if (parts.length != 3) throw new IOException("坐标需要三个数（x y z）");
        try {
            return new EchoMapConfig.Vec(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        } catch (NumberFormatException invalid) {
            throw new IOException("坐标必须是数字，格式：x y z");
        }
    }

    private static Msg validate(ServerPlayer player) throws IOException {
        EchoMapEditState state = edit(player);
        heavy(player);
        List<String> issues = validateWorld(player.serverLevel(), state.draft().snapshot());
        if (issues.isEmpty()) return Msg.of("echo.editor.msg.valid");
        return Msg.of("echo.editor.msg.invalid", Integer.toString(issues.size()), issues.getFirst());
    }

    private static Msg preview(ServerPlayer player) throws IOException {
        EchoMapEditState state = edit(player);
        heavy(player);
        // 平移用会话自己的偏移（没加载 Areas 地图时为 0），与草稿记录坐标用的是同一套
        var map = state.draft().snapshot().translated(state.context().offset());
        var payload = EchoMapPreviewPayload.from(player.serverLevel().dimension().location(), map);
        ServerPlayNetworking.send(player, payload);
        return Msg.of("echo.editor.msg.preview", Integer.toString(payload.entries().size()));
    }

    private static Msg save(ServerPlayer player, String nonce) throws IOException {
        EchoMapEditState state = edit(player);
        heavy(player);
        String mapId = state.context().mapId();
        var map = state.draft().exported(mapId);
        List<String> issues = validateWorld(player.serverLevel(), map);
        if (!issues.isEmpty()) return Msg.of("echo.editor.msg.invalid", Integer.toString(issues.size()), issues.getFirst());
        Path root = world(player.serverLevel());
        String digest = EchoMapRepository.digest(root, mapId);
        if (nonce != null && !nonce.isBlank()) {
            digest = state.consume(UUID.fromString(nonce.trim()), mapId, digest, tick);
        } else if (!digest.isEmpty()) {
            // 已有同名文件：先发一次性令牌，由界面上的「确认覆盖」再提交一次。
            var token = state.confirm(mapId, digest, tick);
            return Msg.of("echo.editor.msg.save_confirm", token.nonce().toString());
        }
        EchoMapRepository.save(root, map, digest);
        state.saved();
        return Msg.of("echo.editor.msg.saved", mapId);
    }

    /** 用 AreasWorldComponent.gameModes 声明/取消声明本模式，并写回地图配置。 */
    private static Msg gate(ServerPlayer player, boolean declared) throws IOException {
        ServerLevel level = player.serverLevel();
        idle(level);
        var areas = AreasWorldComponent.KEY.get(level);
        if (areas.mapName == null || areas.mapName.isBlank()) throw new IOException("当前没有加载任何Areas地图");
        boolean changed = EchoMapGate.setSupported(areas, declared);
        if (changed) areas.sync();
        boolean saved = io.wifi.starrailexpress.game.MapManager.saveCurrentMap(level, areas.mapName, true);
        if (!saved) throw new IOException("地图配置写回失败：" + areas.mapName);
        return Msg.of(declared ? "echo.editor.msg.gate_on" : "echo.editor.msg.gate_off", areas.mapName);
    }

    // ==================== 快照 ====================

    private static EchoEditorStatePayload snapshot(ServerPlayer player, Msg message) {
        ServerLevel level = player.serverLevel();
        var areas = AreasWorldComponent.KEY.get(level);
        String mapName = areas.mapName == null ? "" : areas.mapName;
        boolean declared = EchoMapGate.supported(areas);
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return new EchoEditorStatePayload(false, "", mapName, 0L, declared, false, false,
                    false, false, 0, 0, 0, 0, 0, 0, List.of(), null, List.of(), message.key(), message.args());
        }
        EchoMapEditState state = session.state;
        String mapId = state.context().mapId();
        boolean fileExists = fileExists(level, mapId);
        boolean bound = isActiveMap(level, mapId);
        // 角点按世界坐标回显：输入框里编辑的就是世界坐标，应用时再走同一条模板换算
        var offset = state.context().offset();
        var ca = state.cornerA();
        var cb = state.cornerB();
        double ax = ca == null ? 0 : ca.x() + offset.x(), ay = ca == null ? 0 : ca.y() + offset.y(), az = ca == null ? 0 : ca.z() + offset.z();
        double bx = cb == null ? 0 : cb.x() + offset.x(), by = cb == null ? 0 : cb.y() + offset.y(), bz = cb == null ? 0 : cb.z() + offset.z();
        // 选中点位类型的逐条列表（直接读草稿 JSON，不整份解析；失败就退回没有列表）
        EchoEditorStatePayload.Section detail = null;
        if (session.kind != null) {
            try {
                var entries = new ArrayList<EchoEditorStatePayload.Entry>();
                var ids = new ArrayList<String>();
                for (var point : state.draft().points(session.kind)) {
                    if (entries.size() >= EchoEditorStatePayload.MAX_ENTRIES) break;
                    // 回显世界坐标（模板+偏移）：输入框里编辑的就是世界坐标，应用时再走同一条换算
                    entries.add(new EchoEditorStatePayload.Entry(point.id(), point.players(),
                            point.x() + offset.x(), point.y() + offset.y(), point.z() + offset.z()));
                    if (ids.size() < EchoEditorStatePayload.MAX_IDS) ids.add(point.id());
                }
                detail = new EchoEditorStatePayload.Section(session.kind, entries.size(), ids, entries);
            } catch (RuntimeException ignored) {
                detail = null;
            }
        }
        try {
            var map = state.draft().snapshot();
            List<EchoEditorStatePayload.Section> sections = List.of(
                    section("carrierSpawns", map.carrierSpawns().size(), spawnIds(map.carrierSpawns().size())),
                    section("interceptorSpawns", map.interceptorSpawns().size(), spawnIds(map.interceptorSpawns().size())),
                    section("brokerSpawns", map.brokerSpawns().size(), spawnIds(map.brokerSpawns().size())),
                    section("beacons", map.beacons().size(), map.beacons().stream().map(EchoMapConfig.Beacon::id).toList()),
                    section("crystalNodes", map.crystalNodes().size(), map.crystalNodes().stream().map(EchoMapConfig.Node::id).toList()),
                    section("altars", map.altars().size(), map.altars().stream().map(EchoMapConfig.Point::id).toList()),
                    section("supplyCrates", map.supplyCrates().size(), map.supplyCrates().stream().map(EchoMapConfig.Crate::id).toList()),
                    section("blackCrystalNodes", map.blackCrystalNodes().size(), map.blackCrystalNodes().stream().map(EchoMapConfig.Point::id).toList()),
                    section("blackDropoffs", map.blackDropoffs().size(), map.blackDropoffs().stream().map(EchoMapConfig.Point::id).toList()),
                    section("doors", map.coreGate().doors().size(), map.coreGate().doors().stream().map(EchoMapConfig.Door::id).toList()));
            return new EchoEditorStatePayload(true, mapId, mapName, state.draft().revision(),
                    declared, fileExists, bound, ca != null, cb != null, ax, ay, az, bx, by, bz,
                    sections, detail, List.of(), message.key(), message.args());
        } catch (IOException | RuntimeException failure) {
            return new EchoEditorStatePayload(true, mapId, mapName, state.draft().revision(),
                    declared, fileExists, bound, ca != null, cb != null, ax, ay, az, bx, by, bz,
                    List.of(), detail, List.of(String.valueOf(failure.getMessage())), message.key(), message.args());
        }
    }

    /** 侧文件是否已存在（保存时新建 / 覆盖的提示用）。 */
    private static boolean fileExists(ServerLevel level, String mapId) {
        if (mapId == null || mapId.isBlank()) return false;
        try {
            // getWorldPath / Files.isRegularFile 均不抛受检异常；只防御非法路径导致的运行期异常
            return Files.isRegularFile(world(level).resolve("echo_freight/maps").resolve(mapId + ".json"));
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /** 该配置是否是当前生效地图（switchmap 绑定或 Areas 同名）；取不到一律算未生效，不溢出封包处理。 */
    private static boolean isActiveMap(ServerLevel level, String mapId) {
        try {
            return mapId.equals(effectiveMapId(level));
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static EchoEditorStatePayload.Section section(String field, int count, List<String> ids) {
        return new EchoEditorStatePayload.Section(field, count,
                ids.stream().limit(EchoEditorStatePayload.MAX_IDS).toList(), List.of());
    }

    private static List<String> spawnIds(int count) {
        List<String> ids = new ArrayList<>();
        for (int n = 0; n < Math.min(count, EchoEditorStatePayload.MAX_IDS); n++) ids.add(Integer.toString(n));
        return ids;
    }

    private static void reply(ServerPlayer player, EchoEditorStatePayload state) {
        ServerPlayNetworking.send(player, state);
    }

    // ==================== 公共检查 ====================

    private static void idle(ServerLevel level) throws IOException {
        if (SREGameWorldComponent.KEY.get(level).getGameStatus() != SREGameWorldComponent.GameStatus.INACTIVE)
            throw new IOException("仅允许在非对局、非准备阶段使用地图工具");
    }

    private static Path world(ServerLevel level) { return level.getServer().getWorldPath(LevelResource.ROOT); }

    private static EchoMapEditState.Context context(ServerLevel level, String mapId) throws IOException {
        try {
            EchoMapRepository.requireMapId(mapId);
        } catch (IOException invalid) {
            throw new IOException("非法的回声地图ID: " + mapId);
        }
        // 偏移取自当前 Areas 地图的模板/粘贴区；没加载地图时记 0（坐标按世界原样记录，界面上会提示）
        var areas = AreasWorldComponent.KEY.get(level);
        var offset = areas.mapName == null || areas.mapName.isBlank()
                ? new EchoMapConfig.Vec(0, 0, 0)
                : EchoPreflight.offset(areas);
        return new EchoMapEditState.Context(level.dimension().location().toString(), mapId, offset);
    }

    /** 当前生效的回声地图：switchmap 绑定优先，没有绑定按 Areas 地图名。 */
    private static String effectiveMapId(ServerLevel level) throws IOException {
        requireAreasMap(level);
        String mapId = EchoActiveMap.effective(world(level), AreasWorldComponent.KEY.get(level).mapName);
        try {
            EchoMapRepository.requireMapId(mapId);
        } catch (IOException invalid) {
            throw new IOException("当前回声地图名不是合法mapId: " + mapId);
        }
        return mapId;
    }

    private static void requireAreasMap(ServerLevel level) throws IOException {
        var areas = AreasWorldComponent.KEY.get(level);
        if (areas.mapName == null || areas.mapName.isBlank())
            throw new IOException("当前未加载Areas地图：请在地图ID框里填写地图ID，或先用 /tmm:switchmap load <地图> 加载实战地图");
    }

    private static EchoMapEditState edit(ServerPlayer player) throws IOException {
        idle(player.serverLevel());
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) throw new IOException("没有编辑会话；先在界面里新建或读取草稿");
        // 会话维度/偏移用草稿自己的 mapId 比对：切换绑定不应误杀在途编辑。
        if (session.level != player.serverLevel() || !session.state.valid(context(player.serverLevel(), session.state.context().mapId()), tick)) {
            SESSIONS.remove(player.getUUID());
            throw new IOException("编辑会话因切图、平移变化或超时失效");
        }
        session.state.touch(tick);
        return session.state;
    }

    private static void heavy(ServerPlayer player) throws IOException {
        Long previous = HEAVY.get(player.getUUID());
        if (previous != null && tick - previous < 20) throw new IOException("请间隔1秒再执行地图检查/预览/保存");
        HEAVY.put(player.getUUID(), tick);
    }

    private static BlockPos ray(ServerPlayer player) throws IOException {
        var hit = player.pick(16, 0, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK
                || !player.serverLevel().hasChunkAt(block.getBlockPos()))
            throw new IOException("请瞄准16格内已加载的方块");
        return block.getBlockPos();
    }

    /**
     * 6 人至地图出生点容量（最多 32 人）的逐档校验；返回前若干条问题。
     *
     * <p>已加载 Areas 地图时跑完整预检（结构 + 世界安全 + 地图设置）；没加载时只跑纯结构校验——
     * 世界安全检查留到开局预检做，这样不加载地图也能在工具里把配置建完、存完。
     */
    private static List<String> validateWorld(ServerLevel level, EchoMapConfig map) {
        var areas = AreasWorldComponent.KEY.get(level);
        boolean hasAreas = areas.mapName != null && !areas.mapName.isBlank();
        List<String> issues = new ArrayList<>();
        for (int count = EchoRosterPolicy.MIN_PLAYERS; count <= EchoMapValidator.validationPlayerLimit(map) && issues.isEmpty(); count++) {
            if (hasAreas) {
                var check = EchoPreflight.check(level, count, false, map);
                if (!check.valid()) check.errors().stream().limit(8).map(Component::getString).forEach(issues::add);
            } else {
                var structural = EchoMapValidator.validate(map, count);
                structural.stream().limit(8).forEach(issues::add);
            }
        }
        return issues;
    }
}
