package net.exmo.sre.record.client;

import net.exmo.sre.record.MatchRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端侧的全局战绩缓存：保存最近一次收到的战绩列表，以及已加载的完整回放记录。
 */
public final class ClientMatchRecordCache {

    private static volatile List<MatchRecord.Summary> summaries = new ArrayList<>();
    private static final Map<String, MatchRecord> LOADED = new ConcurrentHashMap<>();

    private ClientMatchRecordCache() {
    }

    public static void setSummaries(List<MatchRecord.Summary> list) {
        summaries = list == null ? new ArrayList<>() : list;
    }

    public static List<MatchRecord.Summary> getSummaries() {
        return summaries;
    }

    public static void putRecord(MatchRecord record) {
        if (record != null && record.matchId != null) {
            LOADED.put(record.matchId, record);
        }
    }

    public static MatchRecord getRecord(String matchId) {
        return matchId == null ? null : LOADED.get(matchId);
    }
}
