package net.exmo.sre.record;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 全局战绩 / 回放记录的数据模型。
 *
 * <p>使用普通字段类（而非 record）以保证 Gson 在任意版本下都能稳定地按字段反射序列化/反序列化。
 * 该模型既用于数据库 {@code payload_json}（完整事件时间线）的持久化，
 * 也用于通过网络 payload 同步到客户端供 GUI 渲染。</p>
 *
 * <p>文本字段统一存储为「序列化后的 {@code Component} JSON 字符串」，
 * 服务端用 {@code Component.Serializer.toJson(component, registryAccess)} 写入，
 * 客户端用 {@code Component.Serializer.fromJson(json, registryAccess)} 还原，保留颜色与样式。</p>
 */
public final class MatchRecord {

    static final Gson GSON = new Gson();
    private static final Type SUMMARY_LIST_TYPE = new TypeToken<List<Summary>>() {
    }.getType();

    /** 唯一战绩 ID（UUID 字符串）。 */
    public String matchId;
    /** 战斗结束时的 epoch 毫秒时间戳。 */
    public long createdAt;
    /** 获胜队伍标识（可能为 null）。 */
    public String winningTeam;
    /** 获胜标题，序列化的 Component JSON（可能为 null）。 */
    public String winningTitleJson;
    /** 本局玩家数量。 */
    public int playerCount;
    /** 玩家名单（含角色）。 */
    public List<MatchPlayer> players = new ArrayList<>();
    /** 完整事件时间线。 */
    public List<MatchEvent> events = new ArrayList<>();

    /** 单个玩家在战绩中的快照。 */
    public static final class MatchPlayer {
        public String uuid;
        public String name;
        public String roleId;
        public boolean alive = true;
    }

    /** 单条时间线事件。 */
    public static final class MatchEvent {
        /** {@code ReplayEventTypes.EventType} 的枚举名。 */
        public String type;
        /** 相对游戏开始的毫秒偏移。 */
        public long relativeTimestamp;
        /** 行为发起者名字（可能为 null）。 */
        public String actorName;
        /** 事件展示文本，序列化的 Component JSON。 */
        public String textJson;
        /** 是否在普通展示中隐藏。 */
        public boolean hidden;
    }

    /** 列表视图所需的精简摘要（不含完整事件，便于战绩列表分页展示）。 */
    public static final class Summary {
        public String matchId;
        public long createdAt;
        public String winningTeam;
        public String winningTitleJson;
        public int playerCount;
        public List<MatchPlayer> players = new ArrayList<>();
    }

    public Summary toSummary() {
        Summary summary = new Summary();
        summary.matchId = matchId;
        summary.createdAt = createdAt;
        summary.winningTeam = winningTeam;
        summary.winningTitleJson = winningTitleJson;
        summary.playerCount = playerCount;
        summary.players = players == null ? new ArrayList<>() : new ArrayList<>(players);
        return summary;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public String toSummaryJson() {
        return GSON.toJson(toSummary());
    }

    public static MatchRecord fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return GSON.fromJson(json, MatchRecord.class);
    }

    public static Summary summaryFromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return GSON.fromJson(json, Summary.class);
    }

    public static String summaryListToJson(List<Summary> summaries) {
        return GSON.toJson(summaries == null ? new ArrayList<Summary>() : summaries);
    }

    public static List<Summary> summaryListFromJson(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        List<Summary> parsed = GSON.fromJson(json, SUMMARY_LIST_TYPE);
        return parsed == null ? new ArrayList<>() : parsed;
    }
}
