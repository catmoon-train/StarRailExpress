package io.wifi.starrailexpress.content.vote;

import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

public class VoteSession {

    private final List<VoteOption> options = new CopyOnWriteArrayList<>();
    private final Map<UUID, Integer> votes = new HashMap<>();
    private final boolean showResults;
    private int syncIntervalTicks;
    private long endTick;
    private Predicate<VoteSession> customEndPredicate;
    private boolean allowReVote;
    private boolean paused;
    private long remainingTicks;
    private Component title;
    private boolean ended = false; // ★ 是否已结束

    /** 限定投票目标玩家，null 表示全体玩家 */
    @Nullable
    private final Set<UUID> targetPlayers;

    VoteSession(Component title, List<VoteOption> options, boolean showResults, int syncIntervalTicks,
            int durationTicks, Predicate<VoteSession> customEndPredicate, boolean allowReVote,
            @Nullable Set<UUID> targetPlayers) {
        this.title = title;
        this.options.addAll(options);
        this.showResults = showResults;
        this.syncIntervalTicks = syncIntervalTicks;
        this.remainingTicks = durationTicks;
        this.customEndPredicate = customEndPredicate;
        this.allowReVote = allowReVote;
        this.paused = false;
        this.ended = false;

        this.targetPlayers = targetPlayers == null ? null : new HashSet<>(targetPlayers);
    }

    @Nullable
    public Set<UUID> getTargetPlayers() {
        return targetPlayers;
    }

    /** 该玩家是否允许参与本次投票 */
    public boolean isAllowedToVote(UUID playerId) {
        return targetPlayers == null || targetPlayers.contains(playerId);
    }

    // 投票时增加校验
    boolean castVote(UUID playerId, int optionIndex) {
        if (!(optionIndex >= 0 && optionIndex < options.size()))
            return false;
        if (ended || !isAllowedToVote(playerId))
            return false;
        if (!allowReVote && votes.containsKey(playerId))
            return false;
        votes.put(playerId, optionIndex);
        return true;
    }

    public List<VoteOption> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public boolean isShowResults() {
        return showResults;
    }

    public int getSyncIntervalTicks() {
        return syncIntervalTicks;
    }

    public Component getTitle() {
        return title;
    }

    public boolean isAllowReVote() {
        return allowReVote;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isEnded() {
        return ended;
    }

    public long getEndTick() {
        return endTick;
    }

    public Map<Integer, Integer> getIndexResults() {
        Map<Integer, Integer> tally = new LinkedHashMap<>();
        for (int i = 0; i < options.size(); i++)
            tally.put(i, 0);
        for (int choice : votes.values()) {
            if (choice >= options.size() || choice < 0)
                continue;
            tally.merge(choice, 1, Integer::sum);
        }
        return tally;
    }

    public Map<String, Integer> getResults() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (int i = 0; i < options.size(); i++)
            tally.put(options.get(i).resultId(), 0);
        for (int choice : votes.values()) {
            if (choice >= options.size() || choice < 0)
                continue;
            tally.merge(options.get(choice).resultId(), 1, Integer::sum);
        }
        return tally;
    }

    /** 获取最高票数的 resultId 和票数，若无投票则返回 null */
    @Nullable
    public Map.Entry<String, Integer> getTopResult() {
        Map<String, Integer> results = getResults();
        if (results.isEmpty())
            return null;
        return results.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);
    }

    public int getTotalVotes() {
        return votes.size();
    }

    void start(long serverTick) {
        this.endTick = serverTick + remainingTicks;
        this.paused = false;
        this.ended = false;
    }

    void pause() {
        if (paused || endTick < 0 || ended)
            return;
        remainingTicks = Math.max(0, endTick - 0); // 需要外部传入当前 tick，暂时用 getCurrentTick()
        paused = true;
    }

    void resume(int serverTick) {
        if (!paused || ended)
            return;
        endTick = serverTick + remainingTicks;
        remainingTicks = 0;
        paused = false;
    }

    void reset(int serverTick) {
        votes.clear();
        remainingTicks = 0;
        paused = false;
        ended = false;
    }

    boolean shouldEnd(long serverTick) {
        if (ended)
            return true;
        if (paused)
            return false;
        if (endTick > 0 && serverTick >= endTick)
            return true;
        if (customEndPredicate != null && customEndPredicate.test(this))
            return true;
        return false;
    }

    void markEnded() {
        this.ended = true;
    }

    // 内部使用的 getCurrentTick 委托给 VoteManager（注入）
    static int getCurrentTick() {
        return VoteManager.getServerTick();
    }
}