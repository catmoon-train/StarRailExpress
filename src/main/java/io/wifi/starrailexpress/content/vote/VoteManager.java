package io.wifi.starrailexpress.content.vote;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 投票管理器（服务端单例）。
 *
 * <h3>快速使用示例：</h3>
 * <pre>{@code
 * VoteSession session = VoteSession.builder("example")
 *     .title("你最喜欢哪个？")
 *     .addTextOption("选项 A")
 *     .addTextOption("选项 B")
 *     .showResults(true)
 *     .durationSeconds(60)
 *     .build();
 * VoteManager.getInstance().startNow(session, SRE.SERVER);
 * }</pre>
 */
public class VoteManager {

    private static VoteManager instance;

    /** 待启动（已注册但未 start）的会话 Builder */
    private final Map<String, VoteSession.Builder> pendingBuilders = new LinkedHashMap<>();
    /** 活跃中的会话 */
    private final Map<String, VoteSession> active = new LinkedHashMap<>();

    private VoteManager() {}

    public static synchronized VoteManager getInstance() {
        if (instance == null) instance = new VoteManager();
        return instance;
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * 创建一个待启动的投票 Builder 并注册。
     * 可通过 {@link #getPendingBuilder(String)} 修改其配置，
     * 然后调用 {@link #start(String, MinecraftServer)} 来启动。
     *
     * @return 新建的 Builder（也可忽略，后续通过 getPendingBuilder 取得）
     */
    public VoteSession.Builder createPending(String id, String title) {
        VoteSession.Builder b = VoteSession.builder(id).title(title);
        pendingBuilders.put(id, b);
        return b;
    }

    /**
     * 注册一个已构建的会话作为 pending 状态（programmatic API 用）。
     * 使用 {@link #startNow(VoteSession, MinecraftServer)} 可跳过此步骤。
     */
    public void registerBuilt(VoteSession session) {
        // 通过临时 builder 将已构建会话放入 pending
        VoteSession.Builder b = VoteSession.builder(session.getId()).title(session.getTitle())
                .showResults(session.isShowResults())
                .allowRevote(session.isAllowRevote());
        for (var opt : session.getOptions()) {
            switch (opt.getType()) {
                case TEXT   -> b.addTextOption(opt.getLabel());
                case PLAYER -> b.addPlayerOption(opt.getLabel(), opt.getPlayerUuid());
                case ITEM   -> b.addItemOption(opt.getItem());
            }
        }
        pendingBuilders.put(session.getId(), b);
    }

    /**
     * 获取 pending Builder，用于添加选项或修改配置。
     * 不存在则返回 null。
     */
    public VoteSession.Builder getPendingBuilder(String id) {
        return pendingBuilders.get(id);
    }

    /**
     * 启动一个已注册的 pending 会话。
     *
     * @return false 表示找不到对应的 pending builder
     */
    public boolean start(String id, MinecraftServer server) {
        VoteSession.Builder builder = pendingBuilders.remove(id);
        if (builder == null) return false;
        VoteSession session = builder.build();
        doStart(session, server);
        return true;
    }

    /** 直接启动（无需预先注册） */
    public void startNow(VoteSession session, MinecraftServer server) {
        doStart(session, server);
    }

    /**
     * 强制停止一个活跃投票并向所有玩家发送关闭包。
     *
     * @return false 表示找不到对应的活跃会话
     */
    public boolean stop(String id, MinecraftServer server) {
        VoteSession session = active.remove(id);
        if (session == null) return false;
        session.end();
        broadcastClose(session, server);
        return true;
    }

    /**
     * 删除一个 pending builder（未启动状态）。
     */
    public boolean removePending(String id) {
        return pendingBuilders.remove(id) != null;
    }

    /** 获取活跃会话，不存在则返回 null */
    public VoteSession getActive(String id) { return active.get(id); }

    /** 判断某 id 是否有 pending builder */
    public boolean hasPending(String id) { return pendingBuilders.containsKey(id); }

    /** 所有活跃会话（只读） */
    public java.util.Collection<VoteSession> getActiveSessions() {
        return Collections.unmodifiableCollection(active.values());
    }

    /** 所有 pending 会话 ID 列表（只读） */
    public java.util.Set<String> getPendingIds() {
        return Collections.unmodifiableSet(pendingBuilders.keySet());
    }

    /**
     * 服务端收到玩家投票请求时调用。
     * 若投票成功且 showResults=true，则立即向全体玩家推送一次计数更新。
     */
    public void handleVote(ServerPlayer player, String sessionId, int optionIndex) {
        VoteSession session = active.get(sessionId);
        if (session == null) return;
        boolean voted = session.recordVote(player.getUUID(), optionIndex);
        if (voted && session.isShowResults() && player.getServer() != null) {
            broadcastCounts(session, player.getServer());
        }
    }

    /**
     * 每个服务端 tick 调用一次（在 SRE.java 的 ServerLifecycleEvents.SERVER_STARTED tick 中注册）。
     */
    public void tick(MinecraftServer server) {
        if (active.isEmpty()) return;

        List<String> toEnd = new ArrayList<>();
        for (VoteSession session : active.values()) {
            if (session.tick()) {
                toEnd.add(session.getId());
            } else if (session.isShowResults() && session.shouldRefresh()) {
                broadcastCounts(session, server);
            }
        }

        for (String id : toEnd) {
            VoteSession session = active.remove(id);
            if (session != null) {
                session.end();
                broadcastClose(session, server);
                SRE.LOGGER.info("[VoteManager] 投票 '{}' 已结束", id);
            }
        }
    }

    // ── 内部方法 ──────────────────────────────────────────────────────────────

    private void doStart(VoteSession session, MinecraftServer server) {
        session.start();
        active.put(session.getId(), session);
        SRE.LOGGER.info("[VoteManager] 投票 '{}' 开始", session.getId());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendOpenVote(player, session);
        }
    }

    /** 向指定玩家发送打开投票界面的包 */
    public void sendOpenVote(ServerPlayer player, VoteSession session) {
        Map<Integer, Integer> counts = session.isShowResults()
                ? session.getVoteCounts()
                : Collections.emptyMap();
        int myVote = session.getPlayerVote(player.getUUID());
        ServerPlayNetworking.send(player, new OpenVoteScreenS2CPayload(
                session.getId(),
                session.getTitle(),
                session.getOptions(),
                session.isShowResults(),
                session.isAllowRevote(),
                session.getEndTimeMillis(),
                counts,
                myVote
        ));
    }

    private void broadcastCounts(VoteSession session, MinecraftServer server) {
        UpdateVoteCountsS2CPayload payload = new UpdateVoteCountsS2CPayload(
                session.getId(),
                session.getVoteCounts(),
                session.getEndTimeMillis()
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private void broadcastClose(VoteSession session, MinecraftServer server) {
        CloseVoteScreenS2CPayload payload = new CloseVoteScreenS2CPayload(
                session.getId(),
                session.getVoteCounts()
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
