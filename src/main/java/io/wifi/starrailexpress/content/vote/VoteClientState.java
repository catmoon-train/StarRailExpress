package io.wifi.starrailexpress.content.vote;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 客户端投票状态缓存（静态全局）。
 * <p>
 * 由网络包处理器更新，由 {@link VoteScreen} 读取。
 */
@Environment(EnvType.CLIENT)
public class VoteClientState {

    public String sessionId = "";
    public String title = "";
    public List<VoteOption> options = Collections.emptyList();
    public boolean showResults = true;
    public boolean allowRevote = false;
    /** 服务端 epoch 毫秒截止时间（-1 = 无截止） */
    public long endTimeMillis = -1L;
    /** 选项 index → 当前票数 */
    public Map<Integer, Integer> voteCounts = Collections.emptyMap();
    /** 本玩家已投的选项 index（-1 = 未投） */
    public int myVote = -1;

    /** 当前活跃的投票状态；null 表示没有活跃投票 */
    public static VoteClientState current = null;

    /** 清除当前状态 */
    public static void clear() {
        current = null;
    }

    /** 更新票数（仅更新 voteCounts 和 endTimeMillis） */
    public void updateCounts(Map<Integer, Integer> counts, long endTimeMillis) {
        this.voteCounts = counts;
        this.endTimeMillis = endTimeMillis;
    }
}
