package io.wifi.starrailexpress.content.vote;

import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

public class VoteSession {

    private final List<VoteOption> options = new ArrayList<>();
    // 改动：每个玩家映射到其选择的选项索引集合
    private final Map<UUID, Set<Integer>> votes = new HashMap<>();
    private final boolean showResults;
    private int syncIntervalTicks;
    private long endTick;
    private Predicate<VoteSession> customEndPredicate;
    private boolean allowReVote;
    private boolean paused;
    private long remainingTicks;
    private Component title;
    private boolean ended = false;
    @Nullable
    private final Set<UUID> targetPlayers;
    private final int maxSelectCount; // 最大可选项数

    VoteSession(Component title, List<VoteOption> options, boolean showResults, int syncIntervalTicks,
                int durationTicks, Predicate<VoteSession> customEndPredicate, boolean allowReVote,
                @Nullable Set<UUID> targetPlayers, int maxSelectCount) {
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
        this.maxSelectCount = Math.max(1, maxSelectCount);
    }

    public int getMaxSelectCount() {
        return maxSelectCount;
    }

    @Nullable
    public Set<UUID> getTargetPlayers() {
        return targetPlayers;
    }

    public boolean isAllowedToVote(UUID playerId) {
        return targetPlayers == null || targetPlayers.contains(playerId);
    }

    // 新投票方法：接收索引列表
    boolean castVote(UUID playerId, List<Integer> optionIndices) {
        if (ended || !isAllowedToVote(playerId)) return false;
        if (optionIndices.isEmpty()) return false;
        for (int idx : optionIndices) {
            if (idx < 0 || idx >= options.size()) return false;
        }
        if (!allowReVote && votes.containsKey(playerId)) return false;
        if (optionIndices.size() > maxSelectCount) return false;
        votes.put(playerId, new LinkedHashSet<>(optionIndices));
        return true;
    }

    // 保留旧接口以便兼容（内部转为列表调用）
    boolean castVote(UUID playerId, int optionIndex) {
        return castVote(playerId, List.of(optionIndex));
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

    // 结果统计：每个玩家选择的每个选项都计一票
    public Map<Integer, Integer> getIndexResults() {
        Map<Integer, Integer> tally = new LinkedHashMap<>();
        for (int i = 0; i < options.size(); i++) tally.put(i, 0);
        for (Set<Integer> choices : votes.values()) {
            for (int choice : choices) {
                if (choice >= 0 && choice < options.size()) {
                    tally.merge(choice, 1, Integer::sum);
                }
            }
        }
        return tally;
    }

    public Map<String, Integer> getResults() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (VoteOption opt : options) tally.put(opt.resultId(), 0);
        for (Set<Integer> choices : votes.values()) {
            for (int choice : choices) {
                if (choice >= 0 && choice < options.size()) {
                    String id = options.get(choice).resultId();
                    tally.merge(id, 1, Integer::sum);
                }
            }
        }
        return tally;
    }

    @Nullable
    public List<Map.Entry<String, Integer>> getTopResults() {
        Map<String, Integer> results = getResults();
        if (results.isEmpty()) return null;
        int maxValue = 0;
        for (int val : results.values()) maxValue = Math.max(maxValue, val);
        List<Map.Entry<String, Integer>> top = new ArrayList<>();
        for (var entry : results.entrySet()) {
            if (entry.getValue() >= maxValue) top.add(entry);
        }
        return top;
    }

    @Nullable
    public Map.Entry<String, Integer> getTopResult() {
        Map<String, Integer> results = getResults();
        if (results.isEmpty()) return null;
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
        if (paused || endTick < 0 || ended) return;
        // 注意：此处依赖外部传入当前 tick，将在 VoteManager 中调用时传入
        remainingTicks = Math.max(0, endTick - VoteManager.getCurrentTick());
        paused = true;
    }

    void resume(int serverTick) {
        if (!paused || ended) return;
        endTick = serverTick + remainingTicks;
        remainingTicks = 0;
        paused = false;
    }

    void reset(long serverTick) {
        votes.clear();
        remainingTicks = 0;
        paused = false;
        ended = false;
    }

    boolean shouldEnd(long serverTick) {
        if (ended) return true;
        if (paused) return false;
        if (endTick > 0 && serverTick >= endTick) return true;
        if (customEndPredicate != null && customEndPredicate.test(this)) return true;
        return false;
    }

    void markEnded() {
        this.ended = true;
    }

    void unmarkEnded() {
        this.ended = false;
    }
}