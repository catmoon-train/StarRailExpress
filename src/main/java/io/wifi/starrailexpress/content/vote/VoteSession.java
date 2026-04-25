package io.wifi.starrailexpress.content.vote;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Predicate;

/**
 * 投票会话 – 保存一次投票的完整配置与运行时状态（服务端）。
 *
 * <p>通过 {@link #builder(String)} 获取流式构建器来创建实例：
 * <pre>{@code
 * VoteSession session = VoteSession.builder("my_vote")
 *     .title("最受欢迎玩家")
 *     .addPlayerOption(playerA)
 *     .addPlayerOption(playerB)
 *     .showResults(true)
 *     .durationSeconds(60)
 *     .allowRevote(false)
 *     .build();
 * VoteManager.getInstance().startNow(session, server);
 * }</pre>
 */
public class VoteSession {

    // ── 身份 & 配置 ────────────────────────────────────────────────────────────
    private final String id;
    private final String title;
    private final List<VoteOption> options;
    private final boolean showResults;
    /** 结果刷新间隔（ticks），默认 200（10 秒） */
    private final int refreshIntervalTicks;
    private final boolean allowRevote;
    /** 投票持续时间（毫秒），&lt;0 表示不自动结束 */
    private final long durationMillis;
    /** 自定义结束判断（null = 不使用），返回 true 则立刻结束 */
    private final Predicate<VoteSession> endPredicate;

    // ── 运行时状态 ────────────────────────────────────────────────────────────
    private boolean active = false;
    private long startTimeMillis = -1L;
    /** playerUUID → 所选选项 index */
    private final Map<UUID, Integer> playerVotes = new LinkedHashMap<>();
    private int tickAccum = 0;

    VoteSession(String id, String title, List<VoteOption> options,
                boolean showResults, int refreshIntervalTicks, boolean allowRevote,
                long durationMillis, Predicate<VoteSession> endPredicate) {
        this.id = id;
        this.title = title;
        this.options = Collections.unmodifiableList(new ArrayList<>(options));
        this.showResults = showResults;
        this.refreshIntervalTicks = refreshIntervalTicks;
        this.allowRevote = allowRevote;
        this.durationMillis = durationMillis;
        this.endPredicate = endPredicate;
    }

    // ── 公共 API ──────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getTitle() { return title; }
    public List<VoteOption> getOptions() { return options; }
    public boolean isShowResults() { return showResults; }
    public boolean isAllowRevote() { return allowRevote; }
    public boolean isActive() { return active; }

    /** 投票结束的 epoch 毫秒时间戳；若无截止时间则返回 -1 */
    public long getEndTimeMillis() {
        if (startTimeMillis < 0 || durationMillis < 0) return -1L;
        return startTimeMillis + durationMillis;
    }

    /** 获取各选项票数（选项 index → 票数） */
    public Map<Integer, Integer> getVoteCounts() {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (VoteOption opt : options) {
            map.put(opt.getIndex(), 0);
        }
        for (int optIdx : playerVotes.values()) {
            map.merge(optIdx, 1, Integer::sum);
        }
        return map;
    }

    /** 已投票总人数 */
    public int getTotalVotes() { return playerVotes.size(); }

    /** 查询某玩家的投票选项（未投则返回 -1） */
    public int getPlayerVote(UUID playerId) {
        return playerVotes.getOrDefault(playerId, -1);
    }

    /** 所有玩家投票记录（只读） */
    public Map<UUID, Integer> getPlayerVotes() {
        return Collections.unmodifiableMap(playerVotes);
    }

    /**
     * 记录一票。
     *
     * @return false 表示不允许投票（不在活动状态、选项非法、或禁止重投且已投过）
     */
    public boolean recordVote(UUID playerId, int optionIndex) {
        if (!active) return false;
        if (optionIndex < 0 || optionIndex >= options.size()) return false;
        if (!allowRevote && playerVotes.containsKey(playerId)) return false;
        playerVotes.put(playerId, optionIndex);
        return true;
    }

    // ── 内部生命周期（仅由 VoteManager 调用） ─────────────────────────────────

    void start() {
        this.active = true;
        this.startTimeMillis = System.currentTimeMillis();
        this.tickAccum = 0;
    }

    void end() {
        this.active = false;
    }

    /**
     * 每 tick 调用一次，检查是否应该结束。
     *
     * @return true 表示应立刻结束投票
     */
    boolean tick() {
        if (!active) return false;
        long endTime = getEndTimeMillis();
        if (endTime > 0 && System.currentTimeMillis() >= endTime) return true;
        if (endPredicate != null && endPredicate.test(this)) return true;
        return false;
    }

    /**
     * 判断是否需要向客户端推送票数更新。
     * 每次调用都会推进计数器；到达刷新间隔时返回 true 并重置计数器。
     */
    boolean shouldRefresh() {
        tickAccum++;
        if (tickAccum >= refreshIntervalTicks) {
            tickAccum = 0;
            return true;
        }
        return false;
    }

    // ── 流式构建器 ─────────────────────────────────────────────────────────────

    /** 使用指定 ID 创建构建器 */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String title = "Vote";
        private final List<VoteOption> options = new ArrayList<>();
        private boolean showResults = true;
        private int refreshIntervalTicks = 200; // 10 秒
        private boolean allowRevote = false;
        private long durationMillis = 60_000L;  // 默认 60 秒
        private Predicate<VoteSession> endPredicate = null;

        private Builder(String id) { this.id = id; }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder addTextOption(String text) {
            options.add(VoteOption.ofText(options.size(), text));
            return this;
        }

        public Builder addPlayerOption(ServerPlayer player) {
            options.add(VoteOption.ofPlayer(options.size(), player));
            return this;
        }

        public Builder addPlayerOption(String name, UUID uuid) {
            options.add(VoteOption.ofPlayer(options.size(), name, uuid));
            return this;
        }

        public Builder addItemOption(ItemStack item) {
            options.add(VoteOption.ofItem(options.size(), item));
            return this;
        }

        /** 是否向客户端显示实时票数 */
        public Builder showResults(boolean showResults) {
            this.showResults = showResults;
            return this;
        }

        /** 刷新间隔（ticks），默认 200（10 秒） */
        public Builder refreshIntervalTicks(int ticks) {
            this.refreshIntervalTicks = Math.max(1, ticks);
            return this;
        }

        /** 是否允许玩家在投票期间更改选择 */
        public Builder allowRevote(boolean allow) {
            this.allowRevote = allow;
            return this;
        }

        /** 投票持续时长（毫秒）；传入 &lt;0 则不自动结束 */
        public Builder durationMillis(long millis) {
            this.durationMillis = millis;
            return this;
        }

        /** 投票持续时长（秒）；传入 &lt;0 则不自动结束 */
        public Builder durationSeconds(int seconds) {
            this.durationMillis = seconds < 0 ? -1L : seconds * 1000L;
            return this;
        }

        /**
         * 自定义结束判断。
         * 每个服务端 tick 都会调用该 Predicate；返回 true 则立刻结束投票。
         */
        public Builder endPredicate(Predicate<VoteSession> predicate) {
            this.endPredicate = predicate;
            return this;
        }

        /** 当前已添加的选项数量 */
        public int getOptionCount() { return options.size(); }

        /** 当前标题 */
        public String getTitle() { return title; }

        /** 当前是否显示结果 */
        public boolean isShowResults() { return showResults; }

        /** 当前是否允许重投 */
        public boolean isAllowRevote() { return allowRevote; }

        /** 当前刷新间隔（ticks） */
        public int getRefreshIntervalTicks() { return refreshIntervalTicks; }

        /** 所有已添加选项的不可变副本 */
        public java.util.List<VoteOption> getOptions() {
            return Collections.unmodifiableList(options);
        }

        public VoteSession build() {
            if (options.isEmpty()) {
                throw new IllegalStateException("VoteSession '" + id + "' 至少需要一个选项");
            }
            return new VoteSession(id, title, options,
                    showResults, refreshIntervalTicks, allowRevote,
                    durationMillis, endPredicate);
        }
    }
}
