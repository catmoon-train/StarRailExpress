package io.wifi.starrailexpress.content.vote;

import java.util.*;
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

public class VoteManager {

    @Nullable
    private static VoteSession currentSession;
    private static MinecraftServer server;
    private static long lastSyncTick = 0;
    private static boolean optionsSent = false;
    private static final List<Consumer<VoteSession>> endCallbacks = new ArrayList<>();

    public static void init(MinecraftServer server) {
        VoteManager.server = server;
    }

    public static void addEndCallback(Consumer<VoteSession> callback) {
        endCallbacks.add(callback);
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, joinedServer) -> {
            VoteSession session = getCurrentSession();
            if (session != null && !session.isEnded()) {
                if (session.getTargetPlayers() == null
                        || session.getTargetPlayers().contains(handler.getPlayer().getUUID())) {
                    ServerPlayNetworking.send(handler.getPlayer(), VoteSyncS2CPacket.fullSync(session));
                }
            }
        });
    }

    public static VoteBuilder builder(Component title) {
        return new VoteBuilder(title);
    }

    // 内部使用，支持传入 maxSelectCount
    @Nullable
    static VoteSession startVote(Component title,
            List<VoteOption> options,
            int durationTicks,
            boolean allowReVote,
            boolean showResults,
            int syncIntervalTicks,
            @Nullable Predicate<VoteSession> customEnd,
            @Nullable Set<UUID> targetPlayers,
            Consumer<VoteSession> callback,
            int maxSelectCount) {
        if (currentSession != null && !currentSession.isEnded())
            return null;
        clear(); // 清除之前的结束回调等
        if (callback != null)
            addEndCallback(callback);
        return startVote(title, options, durationTicks, allowReVote, showResults, syncIntervalTicks, customEnd,
                targetPlayers, maxSelectCount);
    }

    @Nullable
    static VoteSession startVote(Component title,
            List<VoteOption> options,
            int durationTicks,
            boolean allowReVote,
            boolean showResults,
            int syncIntervalTicks,
            @Nullable Predicate<VoteSession> customEnd,
            @Nullable Set<UUID> targetPlayers,
            int maxSelectCount) {
        if (currentSession != null && !currentSession.isEnded())
            return null;
        if (currentSession != null && currentSession.isEnded())
            clear();
        VoteSession session = new VoteSession(title, options, showResults, syncIntervalTicks,
                durationTicks, customEnd, allowReVote, targetPlayers, maxSelectCount);
        optionsSent = false;
        session.start(server.overworld().getGameTime());
        currentSession = session;
        broadcastUpdate();
        return session;
    }

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

    public static void stopCurrentVote() {
        if (currentSession != null && !currentSession.isEnded()) {
            currentSession.markEnded();
            broadcastEnd();
            fireEndCallbacks(currentSession);
        }
    }

    public static boolean pauseCurrentVote() {
        if (currentSession == null || currentSession.isEnded() || currentSession.isPaused())
            return false;
        currentSession.pause();
        broadcastUpdate();
        return true;
    }

    public static boolean resumeCurrentVote() {
        if (currentSession == null || currentSession.isEnded() || !currentSession.isPaused())
            return false;
        currentSession.resume(server.getTickCount());
        broadcastUpdate();
        return true;
    }

    public static void clear() {
        if (currentSession != null) {
            currentSession.reset(server.overworld().getGameTime());
            broadcastEnd();
            currentSession = null;
            optionsSent = false;
        }
        endCallbacks.clear();
    }

    public static void handleVoteCast(ServerPlayer player, List<Integer> optionIndices) {
        VoteSession session = getCurrentSession();
        if (session == null || session.isEnded())
            return;
        if (!session.isAllowedToVote(player.getUUID())) {
            player.displayClientMessage(Component.translatable("vote.not_allowed").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (session.castVote(player.getUUID(), optionIndices)) {
            if (session.isShowResults())
                broadcastUpdate();
            // 可拼接选项名称
            StringBuilder names = new StringBuilder();
            for (int idx : optionIndices) {
                if (idx >= 0 && idx < session.getOptions().size()) {
                    names.append(session.getOptions().get(idx).display().getString()).append(" ");
                }
            }
            player.displayClientMessage(Component.translatable("vote.cast_success", names.toString().trim()), true);
        } else {
            player.displayClientMessage(Component.translatable("vote.already_voted"), true);
        }
    }

    public static void onServerTick() {
        VoteSession session = getCurrentSession();
        if (session == null || session.isEnded())
            return;
        long tick = server.overworld().getGameTime();
        if (session.isShowResults() && session.getSyncIntervalTicks() > 0
                && tick - lastSyncTick >= session.getSyncIntervalTicks()) {
            broadcastUpdate();
            lastSyncTick = tick;
        }
    }

    public static int getServerTick() {
        return server == null ? 0 : server.getTickCount();
    }

    public static String getStatusString() {
        if (currentSession == null || currentSession.isEnded())
            return "idle";
        if (currentSession.isPaused())
            return "paused";
        return "active";
    }

    public static boolean isStatus(String status) {
        return getStatusString().equals(status);
    }

    // 用于 VoteSession.pause() 中计算剩余 tick
    public static long getCurrentTick() {
        return server == null ? 0 : server.overworld().getGameTime();
    }

    private static void fireEndCallbacks(VoteSession session) {
        for (Consumer<VoteSession> c : endCallbacks) {
            try {
                c.accept(session);
            } catch (Exception e) {
                SRE.LOGGER.error("Error in vote end callback", e);
            }
        }
    }

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
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (targets == null || targets.contains(player.getUUID())) {
                ServerPlayNetworking.send(player, packet);
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 投票构建器
    // ═══════════════════════════════════════════════════════
    public static class VoteBuilder {
        private final Component title;
        private final List<VoteOption> options = new ArrayList<>();
        private int durationTicks = 20 * 30;
        private boolean allowReVote = false;
        private boolean showResults = false;
        private Consumer<VoteSession> callbackConsumer = null;
        private int syncIntervalTicks = 0;
        private Predicate<VoteSession> customEnd = null;
        private int autoIdCounter = 0;
        private Set<UUID> targetPlayers = null;
        private int maxSelectCount = 1; // 默认单选

        VoteBuilder(Component title) {
            this.title = title;
        }

        public VoteBuilder callback(Consumer<VoteSession> consumer) {
            this.callbackConsumer = consumer;
            return this;
        }

        public VoteBuilder addOption(VoteOption option) {
            if (option.resultId().isEmpty()) {
                option = wrapWithResultId(option, "autoid:" + autoIdCounter++);
            }
            options.add(option);
            return this;
        }

        public VoteBuilder addOption(VoteOption option, @Nullable String resultId) {
            String id = resultId != null ? resultId : String.valueOf(autoIdCounter++);
            options.add(wrapWithResultId(option, id));
            return this;
        }

        private VoteOption wrapWithResultId(VoteOption original, String id) {
            if (original instanceof TextOption t)
                return new TextOption(t.text(), t.typeId(), id, t.description());
            if (original instanceof ItemOption i)
                return new ItemOption(i.stack(), id, i.description());
            if (original instanceof PlayerOption p)
                return new PlayerOption(p.display(), p.uuid(), id, p.description());
            return original;
        }

        public VoteBuilder duration(int ticks) {
            this.durationTicks = ticks;
            return this;
        }

        public VoteBuilder allowReVote(boolean allow) {
            this.allowReVote = allow;
            return this;
        }

        public VoteBuilder showResults(boolean show) {
            this.showResults = show;
            return this;
        }

        public VoteBuilder syncInterval(int ticks) {
            this.syncIntervalTicks = ticks;
            return this;
        }

        public VoteBuilder endCondition(Predicate<VoteSession> condition) {
            this.customEnd = condition;
            return this;
        }

        public VoteBuilder targetPlayers(Collection<ServerPlayer> players) {
            this.targetPlayers = players.stream().map(ServerPlayer::getUUID).collect(Collectors.toSet());
            return this;
        }

        /** 设置多选模式，maxCount 必须 ≥1，大于1时启用多选 */
        public VoteBuilder maxSelect(int maxCount) {
            this.maxSelectCount = Math.max(1, maxCount);
            return this;
        }

        @Nullable
        public VoteSession start() {
            return VoteManager.startVote(title, options, durationTicks,
                    allowReVote, showResults, syncIntervalTicks, customEnd, targetPlayers,
                    callbackConsumer, maxSelectCount);
        }
    }
}