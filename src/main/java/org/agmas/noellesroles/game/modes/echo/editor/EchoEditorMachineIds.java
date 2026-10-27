package org.agmas.noellesroles.game.modes.echo.editor;

import org.agmas.noellesroles.game.modes.echo.content.EchoMachines;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapConfig;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把已注册的回声机器映射到编辑器点位，并生成不冲突的点位 id。
 *
 * <p>只做纯映射与 id 规则，不读世界、不信客户端传来的 kind 或坐标。
 * 重复识别同一方块中心时返回既有 id，避免同坐标堆出多条点位。
 */
public final class EchoEditorMachineIds {
    private EchoEditorMachineIds() { }

    /**
     * 12 种机器到草稿 kind 的固定映射。
     * 未知机器返回 null，调用方应给出「不是机器」反馈，而不是猜一个类型。
     */
    public static String kindOf(EchoMachines.Kind machine) {
        if (machine == null) return null;
        return switch (machine) {
            case BEACON -> "beacon_open";
            case CORE_BEACON -> "beacon_core";
            case CRYSTAL -> "crystal";
            case ALTAR -> "altar";
            case SUPPLY -> "supply";
            case BLACK_MARKET -> "black_market";
            case BLACK_NODE -> "black_node";
            case BLACK_DROPOFF -> "black_dropoff";
            case CORE_CONSOLE -> "core_control";
            case FREE_EXIT -> "free_control";
            case EVACUATION_CONSOLE -> "evacuation_control";
            case BROKER_EXIT -> "broker_exit";
        };
    }

    /** 该机器点位是否带人数档。与 {@link EchoMapDraft} 的 mark 规则一致。 */
    public static boolean hasPlayers(String kind) {
        return "beacon_open".equals(kind) || "beacon_core".equals(kind)
                || "crystal".equals(kind) || "supply".equals(kind) || "black_market".equals(kind);
    }

    /**
     * 在同 kind 已有点位里找同一方块中心。
     * 找不到返回 null；找到则应更新该 id，而不是再追加一条。
     */
    public static String existingId(List<EchoMapDraft.PointInfo> points, EchoMapConfig.Vec center) {
        if (points == null || center == null) return null;
        for (var point : points) {
            if (sameBlock(point.x(), point.y(), point.z(), center)) return point.id();
        }
        return null;
    }

    public static boolean sameBlock(double x, double y, double z, EchoMapConfig.Vec center) {
        return Math.floor(x) == Math.floor(center.x())
                && Math.floor(y) == Math.floor(center.y())
                && Math.floor(z) == Math.floor(center.z());
    }

    /**
     * 选用点位 id：手填合法 id 优先；留空则自动生成不冲突 id。
     * 手填 id 若已被另一坐标占用，返回 null，调用方应拒绝而不是覆盖别人的点。
     */
    public static String chooseId(String requested, String kind, List<EchoMapDraft.PointInfo> points, EchoMapConfig.Vec center) {
        String manual = requested == null ? "" : requested.trim().toLowerCase(Locale.ROOT);
        if (!manual.isEmpty()) {
            if (!manual.matches("[a-z0-9_-]{1,64}")) return null;
            for (var point : points) {
                if (point.id().equals(manual) && !sameBlock(point.x(), point.y(), point.z(), center)) return null;
            }
            return manual;
        }
        return nextId(prefix(kind), ids(points));
    }

    /** 自动 id 前缀，稳定且不超过 64。 */
    public static String prefix(String kind) {
        return switch (kind) {
            case "beacon_open" -> "beacon";
            case "beacon_core" -> "core_beacon";
            case "crystal" -> "crystal";
            case "altar" -> "altar";
            case "supply" -> "supply";
            case "black_market" -> "black_market";
            case "black_node" -> "black_node";
            case "black_dropoff" -> "black_dropoff";
            case "core_control" -> "core_door";
            case "free_control" -> "free_exit";
            case "evacuation_control" -> "evac";
            case "broker_exit" -> "broker_exit";
            default -> "machine";
        };
    }

    /** 从 1 起找第一个未占用的 {@code prefix_N}。 */
    public static String nextId(String prefix, List<String> taken) {
        String stem = prefix == null || prefix.isBlank() ? "machine" : prefix;
        int bound=taken==null?1:taken.size()+1;
        for (int n = 1; n <= bound; n++) {
            String suffix="_"+n;
            String candidate=stem.substring(0,Math.min(stem.length(),64-suffix.length()))+suffix;
            if (!contains(taken, candidate)) return candidate;
        }
        throw new IllegalStateException("无法分配点位id");
    }

    private static List<String> ids(List<EchoMapDraft.PointInfo> points) {
        var ids = new ArrayList<String>();
        if (points == null) return ids;
        for (var point : points) ids.add(point.id());
        return ids;
    }

    private static boolean contains(List<String> taken, String id) {
        if (taken == null) return false;
        for (String existing : taken) if (id.equals(existing)) return true;
        return false;
    }
}
