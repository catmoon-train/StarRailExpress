package org.agmas.noellesroles.game.modes.echo.runtime;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.network.PacketTracker;
import net.exmo.sre.camera.AdvancedCameraNode;
import net.exmo.sre.camera.AdvancedCameraPayload;
import net.exmo.sre.camera.AdvancedCameraSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 回响托运站的开局运镜：先俯瞰全图，再分别展示截流者（追捕方）与托运者（逃脱方）的出生区，
 * 随后带过核心门与撤离区等设施，最后回到玩家自己的眼睛位置交还视角。
 *
 * <p>与既有开场不同，这里由模式自己发一条 {@link AdvancedCameraPayload} 轨道，因此默认的
 * 「从远处俯视玩家」镜头必须被跳过（见 {@code SREEventRegister}）。解说文字走
 * {@link EchoIntroPayload}，两者时间轴共用本类常量；客户端只按 tick 播放，不参与任何判定。
 *
 * <p>总时长必须留在 {@code EchoRules.SAFE_TICKS} 内，并给专长选择窗口留出余量。
 */
public final class EchoIntroService {
    /** 俯瞰全图。 */
    private static final int OVERVIEW_APPROACH = 15, OVERVIEW_HOLD = 15;
    /** 阵营出生区：远景推近。 */
    private static final int FACTION_APPROACH = 25, FACTION_HOLD = 20;
    /** 核心门。 */
    private static final int GATE_APPROACH = 20, GATE_HOLD = 15;
    /** 撤离区。 */
    private static final int EVAC_APPROACH = 20, EVAC_HOLD = 10;
    /** 回到玩家视角。 */
    private static final int RETURN_APPROACH = 35;

    private static final int CUE_OVERVIEW = 0;
    private static final int CUE_INTERCEPTOR = CUE_OVERVIEW + OVERVIEW_APPROACH + OVERVIEW_HOLD;
    private static final int CUE_CARRIER = CUE_INTERCEPTOR + FACTION_APPROACH + FACTION_HOLD;
    private static final int CUE_GATE = CUE_CARRIER + FACTION_APPROACH + FACTION_HOLD;
    private static final int CUE_EVAC = CUE_GATE + GATE_APPROACH + GATE_HOLD;
    private static final int CUE_SPEC = CUE_EVAC + EVAC_APPROACH + EVAC_HOLD;

    /** 整条轨道总时长（tick）；客户端用它结束运镜文字。 */
    public static final int TOTAL_TICKS = CUE_SPEC + RETURN_APPROACH;

    private EchoIntroService() { }

    /** 向全部在线参与者发送运镜与解说；无会话或地图时静默跳过。 */
    public static void send(ServerLevel level, EchoMatch match) {
        if (match == null) return;
        var map = match.map();
        if (map == null) return;
        var areas = AreasWorldComponent.KEY.get(level);
        List<EchoIntroPayload.Cue> cues = cues();
        for (ServerPlayer player : match.onlinePlayers()) {
            AdvancedCameraSequence sequence = build(map, areas, player);
            if (sequence.isEmpty()) continue;
            PacketTracker.sendToClient(player, AdvancedCameraPayload.play(sequence.toJson()));
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                    new EchoIntroPayload(match.id(), TOTAL_TICKS, cues));
        }
    }

    private static List<EchoIntroPayload.Cue> cues() {
        return List.of(
                new EchoIntroPayload.Cue("echo.intro.cue.map.title", "echo.intro.cue.map.body",
                        CUE_OVERVIEW, CUE_INTERCEPTOR),
                new EchoIntroPayload.Cue("echo.intro.cue.interceptor.title", "echo.intro.cue.interceptor.body",
                        CUE_INTERCEPTOR, CUE_CARRIER),
                new EchoIntroPayload.Cue("echo.intro.cue.carrier.title", "echo.intro.cue.carrier.body",
                        CUE_CARRIER, CUE_GATE),
                new EchoIntroPayload.Cue("echo.intro.cue.facility.title", "echo.intro.cue.facility.body",
                        CUE_GATE, CUE_EVAC),
                new EchoIntroPayload.Cue("echo.intro.cue.evacuation.title", "echo.intro.cue.evacuation.body",
                        CUE_EVAC, CUE_SPEC),
                new EchoIntroPayload.Cue("echo.intro.cue.specialization.title", "echo.intro.cue.specialization.body",
                        CUE_SPEC, TOTAL_TICKS));
    }

    /**
     * 单人轨道：终点是本人的眼睛位置与朝向，交还视角后不会把玩家甩到别处。
     * 缺失的点位会被跳过，因此不完整的地图也不会卡住开局。
     */
    public static AdvancedCameraSequence build(EchoMapConfig map, AreasWorldComponent areas, ServerPlayer player) {
        List<AdvancedCameraNode> nodes = new ArrayList<>();
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 overview = overviewFocus(areas);
        dolly(nodes, overview, flatUnit(1, 1), 90.0D, 46.0D, 26.0D,
                OVERVIEW_APPROACH, OVERVIEW_HOLD, 72.0F);
        // 阵营出生区：相机站在这一队的正前方回望，展示到的是他们的正面。
        dolly(nodes, centroid(map.interceptorSpawns()), forwardOf(map.interceptorSpawns()),
                12.0D, 5.0D, 7.0D, FACTION_APPROACH, FACTION_HOLD, 62.0F);
        dolly(nodes, centroid(map.carrierSpawns()), forwardOf(map.carrierSpawns()),
                12.0D, 5.0D, 7.0D, FACTION_APPROACH, FACTION_HOLD, 62.0F);
        dolly(nodes, gateFocus(map), flatUnit(-1, -1), 14.0D, 6.0D, 6.0D, GATE_APPROACH, GATE_HOLD, 64.0F);
        dolly(nodes, evacFocus(map), flatUnit(1, -1), 16.0D, 7.0D, 6.0D, EVAC_APPROACH, EVAC_HOLD, 66.0F);
        // 终点：直接落在玩家眼睛上，沿用玩家自己的角度，避免注视自身造成角度退化。
        nodes.add(new AdvancedCameraNode(RETURN_APPROACH, 0, eye, null, player.getYRot(), player.getXRot(), 0F));
        return new AdvancedCameraSequence(nodes, true, true, false);
    }

    /**
     * 两点推近：远景 → 近景，都注视同一个焦点。
     *
     * @param dir 焦点指向相机的水平单位方向（相机站哪一侧）
     */
    private static void dolly(List<AdvancedCameraNode> nodes, Vec3 focus, Vec3 dir, double far, double near,
                              double high, int approach, int hold, float fov) {
        if (focus == null) return;
        Vec3 camera = focus.add(dir.x * far, high, dir.z * far);
        nodes.add(new AdvancedCameraNode(approach, hold, camera, focus, null, null, fov));
        Vec3 close = focus.add(dir.x * near, high * 0.35D, dir.z * near);
        nodes.add(new AdvancedCameraNode(0, 0, close, focus, null, null, fov));
    }

    /** 水平单位向量；退化时退到 +Z，避免除零。 */
    private static Vec3 flatUnit(double x, double z) {
        double length = Math.sqrt(x * x + z * z);
        return length < 1.0E-4D ? new Vec3(0, 0, 1) : new Vec3(x / length, 0, z / length);
    }

    private static Vec3 overviewFocus(AreasWorldComponent areas) {
        var box = areas.getPlayArea();
        if (box == null) return Vec3.ZERO;
        double y = Math.min(box.maxY, box.minY + 24.0D);
        return new Vec3((box.minX + box.maxX) / 2.0D, y, (box.minZ + box.maxZ) / 2.0D);
    }

    private static Vec3 gateFocus(EchoMapConfig map) {
        var doors = map.coreGate().doors();
        if (!doors.isEmpty()) {
            var control = doors.getFirst().controlPos();
            return new Vec3(control.x(), control.y(), control.z());
        }
        var core = map.coreArea();
        return new Vec3((core.minX() + core.maxX()) / 2.0D, (core.minY() + core.maxY()) / 2.0D,
                (core.minZ() + core.maxZ()) / 2.0D);
    }

    private static Vec3 evacFocus(EchoMapConfig map) {
        var control = map.evacuation().controlPos();
        return new Vec3(control.x(), control.y(), control.z());
    }

    /** 出生点质心（抬高一点，避免贴地）。 */
    private static Vec3 centroid(List<EchoMapConfig.Spawn> spawns) {
        if (spawns.isEmpty()) return null;
        double x = 0, y = 0, z = 0;
        for (var spawn : spawns) { x += spawn.x(); y += spawn.y(); z += spawn.z(); }
        int count = spawns.size();
        return new Vec3(x / count, y / count + 1.0D, z / count);
    }

    /** 一组出生点的平均朝向在水平面上的正前方单位向量。 */
    private static Vec3 forwardOf(List<EchoMapConfig.Spawn> spawns) {
        if (spawns.isEmpty()) return flatUnit(0, 1);
        double sum = 0;
        for (var spawn : spawns) sum += spawn.yaw();
        double yawRad = Math.toRadians(sum / spawns.size());
        return flatUnit(-Math.sin(yawRad), Math.cos(yawRad));
    }

    /** 供日志与调试命令使用的一句摘要。 */
    public static String describe(EchoMapConfig map) {
        return String.format(Locale.ROOT, "%s ticks=%d carriers=%d interceptors=%d", map.mapId(), TOTAL_TICKS,
                map.carrierSpawns().size(), map.interceptorSpawns().size());
    }
}
