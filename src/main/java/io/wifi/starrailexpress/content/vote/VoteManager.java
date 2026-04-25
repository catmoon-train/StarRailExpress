package io.wifi.starrailexpress.content.vote;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.vote.VoteOption.ItemOption;
import io.wifi.starrailexpress.content.vote.VoteOption.PlayerOption;
import io.wifi.starrailexpress.content.vote.VoteOption.TextOption;
import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端投票管理器，单例，同一时间只允许一个活跃投票。
 * <p>
 * 提供投票生命周期管理、网络同步、回调注册等功能。
 *
 * <h3>快速使用</h3>
 * 
 * <pre>{@code
 *
 * // 创建投票
 * VoteSession session = VoteManager.builder(Component.literal("标题"))
 *         ...
 *         .start();
 *
 * }</pre>
 * 
 * {@link VoteBuilder}
 * 
 * @author wifi-left
 */
public class VoteManager {

    @Nullable
    private static VoteSession currentSession;
    private static MinecraftServer server;
    private static long lastSyncTick = 0;
    private static boolean optionsSent = false;

    /** 投票结束回调列表 */
    private static final List<Consumer<VoteSession>> endCallbacks = new ArrayList<>();

    /**
     * 初始化管理器，必须在服务器启动后调用。
     *
     * @param server 当前 Minecraft 服务器实例
     */
    public static void init(MinecraftServer server) {
        VoteManager.server = server;
    }

    /**
     * 注册一个在投票结束时触发的回调。
     * 回调在投票自然结束、手动停止（stop）时调用，参数为已结束的 {@link VoteSession}。
     *
     * @param callback 接收结束的投票会话
     */
    public static void addEndCallback(Consumer<VoteSession> callback) {
        endCallbacks.add(callback);
    }

    /**
     * 注册 Fabric 事件监听，自动向新加入玩家发送当前投票完整数据。
     * 应在 {@link #init(MinecraftServer)} 之后调用。
     * <p>
     * 已经注册，无需重复注册。
     */
    // 新玩家加入时检查目标
    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joinedServer) -> {
            VoteSession session = getCurrentSession();
            if (session != null && !session.isEnded()) {
                if (session.getTargetPlayers() == null
                        || session.getTargetPlayers().contains(handler.getPlayer().getUUID())) {
                    VoteSyncS2CPacket packet = VoteSyncS2CPacket.fullSync(session);
                    ServerPlayNetworking.send(handler.getPlayer(), packet);
                }
            }
        });
    }

    /**
     * 创建一个 {@link VoteBuilder} 用于链式配置新投票。
     *
     * @param title 投票标题
     * @return 构建器实例
     */
    public static VoteBuilder builder(Component title) {
        return new VoteBuilder(title);
    }

    /**
     * 启动一次新投票。
     * <p>
     * 如果当前已存在未结束的投票，返回 {@code null} 表示无法覆盖。
     * 若存在已结束但未清除的投票，会自动先清除旧数据。
     *
     * @param title             投票标题
     * @param options           投票选项列表
     * @param durationTicks     投票持续时长（游戏刻）
     * @param allowReVote       是否允许重投
     * @param showResults       是否向客户端实时展示票数
     * @param syncIntervalTicks 结果同步间隔（游戏刻）
     * @param customEnd         自定义结束判定，{@code null} 则只用时长
     * @param targetPlayers     需要投票的玩家
     * @param callback          结束投票后调用
     * @return 新投票会话，若已有活跃投票则返回 {@code null}
     */
    @Nullable
    public static VoteSession startVote(Component title,
            List<VoteOption> options,
            int durationTicks,
            boolean allowReVote,
            boolean showResults,
            int syncIntervalTicks,
            @Nullable Predicate<VoteSession> customEnd,
            @Nullable Set<UUID> targetPlayers, Consumer<VoteSession> callback) {
        if (currentSession != null && !currentSession.isEnded()) {
            return null;
        }
        endCallbacks.clear();
        if (callback != null)
            addEndCallback(callback);
        return startVote(title, options, durationTicks, allowReVote, showResults, syncIntervalTicks, customEnd,
                targetPlayers);
    }

    /**
     * 启动一次新投票。
     * <p>
     * 如果当前已存在未结束的投票，返回 {@code null} 表示无法覆盖。
     * 若存在已结束但未清除的投票，会自动先清除旧数据。
     *
     * @param title             投票标题
     * @param options           投票选项列表
     * @param durationTicks     投票持续时长（游戏刻）
     * @param allowReVote       是否允许重投
     * @param showResults       是否向客户端实时展示票数
     * @param syncIntervalTicks 结果同步间隔（游戏刻）
     * @param customEnd         自定义结束判定，{@code null} 则只用时长
     * @return 新投票会话，若已有活跃投票则返回 {@code null}
     */
    @Nullable
    public static VoteSession startVote(Component title,
            List<VoteOption> options,
            int durationTicks,
            boolean allowReVote,
            boolean showResults,
            int syncIntervalTicks,
            @Nullable Predicate<VoteSession> customEnd,
            @Nullable Set<UUID> targetPlayers) {
        if (currentSession != null && !currentSession.isEnded()) {
            return null;
        }
        if (currentSession != null && currentSession.isEnded()) {
            clear();
        }

        if (currentSession != null && !currentSession.isEnded()) {
            return null;
        }
        if (currentSession != null && currentSession.isEnded()) {
            clear();
        }

        VoteSession session = new VoteSession(title, options, showResults, syncIntervalTicks,
                durationTicks, customEnd, allowReVote, targetPlayers);
        optionsSent = false;
        session.start(server.overworld().getGameTime());
        currentSession = session;
        broadcastUpdate();
        return session;
    }

    /**
     * 获取当前投票会话，若投票已到结束条件会自动标记结束并触发回调。
     *
     * @return 当前会话（可能已结束），无投票时返回 {@code null}
     */
    @Nullable
    public static VoteSession getCurrentSession() {
        if (currentSession != null && !currentSession.isEnded()
                && currentSession.shouldEnd(server.overworld().getGameTime())) {
            currentSession.markEnded();
            broadcastEnd();
            fireEndCallbacks(currentSession);
        }
        return currentSession;
    }

    /**
     * 强制停止当前投票（标记为结束），保留数据供查询，触发结束回调。
     */
    public static void stopCurrentVote() {
        if (currentSession != null && !currentSession.isEnded()) {
            currentSession.markEnded();
            broadcastEnd();
            fireEndCallbacks(currentSession);
        }
    }

    /**
     * 暂停当前投票计时。
     *
     * @return {@code true} 成功暂停，否则 {@code false}
     */
    public static boolean pauseCurrentVote() {
        if (currentSession == null || currentSession.isEnded() || currentSession.isPaused())
            return false;
        currentSession.pause();
        broadcastUpdate();
        return true;
    }

    /**
     * 恢复当前投票计时。
     *
     * @return {@code true} 成功恢复，否则 {@code false}
     */
    public static boolean resumeCurrentVote() {
        if (currentSession == null || currentSession.isEnded() || !currentSession.isPaused())
            return false;
        currentSession.resume(server.getTickCount());
        broadcastUpdate();
        return true;
    }

    /**
     * 彻底清除所有投票数据（包括已结束的），广播结束包。
     */
    public static void clear() {
        if (currentSession != null) {
            currentSession.reset(server.getTickCount());
            broadcastEnd();
            currentSession = null;
            optionsSent = false;
        }
        endCallbacks.clear();
    }

    /**
     * 处理客户端投票包。
     *
     * @param player      投票玩家
     * @param optionIndex 投票选项索引
     */
    public static void handleVoteCast(ServerPlayer player, int optionIndex) {
        VoteSession session = getCurrentSession();
        if (session == null || session.isEnded())
            return;
        if (!session.isAllowedToVote(player.getUUID())) {
            player.displayClientMessage(Component.translatable("").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (session.castVote(player.getUUID(), optionIndex)) {
            if (session.isShowResults()) {
                broadcastUpdate();
            }
            var option = session.getOptions().get(optionIndex);
            player.displayClientMessage(Component.translatable("vote.cast_success", option.display()), true);
        } else {
            player.displayClientMessage(Component.translatable("vote.already_voted"), true);
        }
    }

    /**
     * 每 tick 调用一次，用于检查投票结束和定期同步。
     */
    public static void onServerTick() {
        VoteSession session = getCurrentSession(); // 可能触发结束
        if (session == null || session.isEnded())
            return;

        long tick = server.overworld().getGameTime();
        if (session.isShowResults() && session.getSyncIntervalTicks() > 0
                && tick - lastSyncTick >= session.getSyncIntervalTicks()) {
            broadcastUpdate();
            lastSyncTick = tick;
        }
    }

    /**
     * 获取当前服务器 tick 计数，默认 0。
     */
    public static int getServerTick() {
        return server == null ? 0 : server.getTickCount();
    }

    /**
     * 投票状态字符串：idle（无投票）、active（进行中）、paused（暂停）、ended（已结束）。
     */
    public static String getStatusString() {
        VoteSession session = currentSession;
        if (session == null)
            return "idle";
        if (session.isEnded())
            return "ended";
        if (session.isPaused())
            return "paused";
        return "active";
    }

    /**
     * 判断当前投票状态是否等于给定字符串。
     */
    public static boolean isStatus(String status) {
        return getStatusString().equals(status);
    }

    // ── 内部工具 ──────────────────────────────────

    private static void fireEndCallbacks(VoteSession session) {
        for (Consumer<VoteSession> c : endCallbacks) {
            if (c == null)
                continue;
            try {
                c.accept(session);
            } catch (Exception e) {
                SRE.LOGGER.error("Error while fire end callbacks.", e);
            }
        }
    }

    // 广播时过滤目标玩家
    private static void broadcastUpdate() {
        if (currentSession == null || server == null || currentSession.isEnded())
            return;
        VoteSyncS2CPacket packet;
        if (!optionsSent) {
            packet = VoteSyncS2CPacket.fullSync(currentSession);
            optionsSent = true;
        } else {
            packet = VoteSyncS2CPacket.update(currentSession);
        }
        Set<UUID> targets = currentSession.getTargetPlayers();
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                if (targets == null || targets.contains(player.getUUID())) {
                    ServerPlayNetworking.send(player, packet);
                }
            }
        }
    }

    private static void broadcastEnd() {
        if (server == null)
            return;
        VoteSyncS2CPacket packet = VoteSyncS2CPacket.end();
        Set<UUID> targets = currentSession != null ? currentSession.getTargetPlayers() : null;
        {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (targets == null || targets.contains(player.getUUID())) {
                    ServerPlayNetworking.send(player, packet);
                }
            }
        }
    }

    /**
     * 投票构建器，通过 {@link VoteManager#builder(Component)} 创建。
     * 
     * <pre>{@code
     *
     * // 创建投票
     * VoteSession session = VoteManager.builder(Component.literal("标题"))
     *         .addOption(VoteOption.text("选项 A"))
     *         .addOption(VoteOption.text("选项 B"), "sel_b")
     *         .addOption(VoteOption.player(player), "player")
     *         .addOption(VoteOption.item(itemStack), "item_a")
     *         .duration(20 * 30) // 30 秒
     *         .allowReVote(true)
     *         .showResults(true)
     *         .syncInterval(20 * 5)
     *         .callback(s -> {
     *             System.out.println("投票结束！获胜选项：" + s.getResults());
     *         })
     *         .start();
     *
     * }</pre>
     */
    public static class VoteBuilder {
        private final Component title;
        private final List<VoteOption> options = new ArrayList<>();
        private int durationTicks = 20 * 30; // 默认 30 秒
        private boolean allowReVote = false;
        private boolean showResults = false;
        private Consumer<VoteSession> callbackConsumer = null;
        private int syncIntervalTicks = 0; // 默认禁用
        private Predicate<VoteSession> customEnd = null;
        private int autoIdCounter = 0;

        VoteBuilder(Component title) {
            this.title = title;
        }

        public VoteBuilder callback(Consumer<VoteSession> consumer) {
            this.callbackConsumer = consumer;
            return this;
        }

        /** 添加选项（自动分配 resultId） */
        public VoteBuilder addOption(VoteOption option) {
            // 若选项未携带 resultId，则用当前计数器自动生成
            if (option.resultId().isEmpty()) {
                option = wrapWithResultId(option, "autoid:" + String.valueOf(autoIdCounter++));
            }
            options.add(option);
            return this;
        }

        private VoteOption wrapWithResultId(VoteOption original, String id) {
            if (original instanceof TextOption t)
                return new TextOption(t.text(), t.typeId(), id);
            if (original instanceof ItemOption i)
                return new ItemOption(i.stack(), id);
            if (original instanceof PlayerOption p)
                return new PlayerOption(p.display(), p.uuid(), id);
            return original; // fallback
        }

        /** 添加选项并指定 resultId */
        public VoteBuilder addOption(VoteOption option, @Nullable String resultId) {
            String id = resultId != null ? resultId : String.valueOf(autoIdCounter++);
            options.add(wrapWithResultId(option, id));
            return this;
        }

        /** 设置持续时间（游戏刻） */
        public VoteBuilder duration(int ticks) {
            this.durationTicks = ticks;
            return this;
        }

        /** 是否允许重新投票 */
        public VoteBuilder allowReVote(boolean allow) {
            this.allowReVote = allow;
            return this;
        }

        /** 是否向客户端显示实时结果 */
        public VoteBuilder showResults(boolean show) {
            this.showResults = show;
            return this;
        }

        /** 设置结果同步间隔（游戏刻） */
        public VoteBuilder syncInterval(int ticks) {
            this.syncIntervalTicks = ticks;
            return this;
        }

        /** 自定义投票结束条件，返回 true 时投票立即结束 */
        public VoteBuilder endCondition(Predicate<VoteSession> condition) {
            this.customEnd = condition;
            return this;
        }

        private Set<UUID> targetPlayers = null;

        /** 限制参与投票的玩家，null 或空表示全体 */
        public VoteBuilder targetPlayers(Collection<ServerPlayer> players) {
            this.targetPlayers = players.stream().map(ServerPlayer::getUUID).collect(Collectors.toSet());
            return this;
        }

        @Nullable
        public VoteSession start() {
            return VoteManager.startVote(title, options, durationTicks,
                    allowReVote, showResults, syncIntervalTicks, customEnd, targetPlayers, callbackConsumer);
        }
    }

    /**
     * 获取当前服务器游戏刻数，无服务器时返回 0。
     */
    public static long getCurrentTick() {
        return server == null ? 0 : server.overworld().getGameTime();
    }
}